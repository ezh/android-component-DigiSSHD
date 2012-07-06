/*
 * DigiSSHD - DigiControl component for Android Platform
 * Copyright (c) 2012, Alexey Aksenov ezh@ezh.msk.ru. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 or any later
 * version, as published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package org.digimead.digi.ctrl.sshd

import java.io.File

import scala.actors.Futures
import scala.collection.JavaConversions._

import org.digimead.digi.ctrl.ICtrlComponent
import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.DService
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.base.AppControl
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.dialog.Preferences
import org.digimead.digi.ctrl.lib.info.ComponentInfo
import org.digimead.digi.ctrl.lib.info.ExecutableInfo
import org.digimead.digi.ctrl.lib.info.UserInfo
import org.digimead.digi.ctrl.lib.log.FileLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.lib.message.IAmYell
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.util.Hash
import org.digimead.digi.ctrl.lib.util.SyncVar
import org.digimead.digi.ctrl.lib.util.Version
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.service.option.DSAPublicKeyEncription
import org.digimead.digi.ctrl.sshd.service.option.NetworkPort
import org.digimead.digi.ctrl.sshd.service.option.RSAPublicKeyEncription
import org.digimead.digi.ctrl.sshd.service.option.SSHAuthentificationMode

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.os.IBinder

class SSHDService extends Service with DService {
  private val ready = new SyncVar[Boolean]()
  private val binder = new SSHDService.Binder(ready)
  log.debug("alive")

  @Loggable
  override def onCreate() = {
    SSHDService.service = Some(this)
    // sometimes there is java.lang.IllegalArgumentException in scala.actors.threadpool.ThreadPoolExecutor
    // if we started actors from the singleton
    SSHDActivity.actor.start // Yes, SSHDActivity from SSHDService
    Preferences.DebugLogLevel.set(this)
    Preferences.DebugAndroidLogger.set(this)
    super.onCreate()
    onCreateExt(this)
    SSHDPreferences.initServicePersistentOptions(this)
    if (AppControl.Inner.isAvailable != Some(true))
      Futures.future {
        log.debug("try to bind " + DConstant.controlPackage)
        AppComponent.Inner.minVersionRequired(DConstant.controlPackage) match {
          case Some(minVersion) => try {
            val pm = getPackageManager()
            val pi = pm.getPackageInfo(DConstant.controlPackage, 0)
            val version = new Version(pi.versionName)
            log.debug(DConstant.controlPackage + " minimum version '" + minVersion + "' and current version '" + version + "'")
            if (version.compareTo(minVersion) == -1) {
              val message = Android.getString(this, "error_digicontrol_minimum_version").
                getOrElse("Required minimum version of DigiControl: %s. Current version is %s").format(minVersion, version)
              IAmYell(message)
              AppControl.Inner.bindStub("error_digicontrol_minimum_version", minVersion.toString, version.toString)
            } else {
              AppControl.Inner.bind(getApplicationContext)
            }
          } catch {
            case e: NameNotFoundException =>
              log.debug("DigiControl package " + DConstant.controlPackage + " not found")
          }
          case None =>
            AppControl.Inner.bind(getApplicationContext)
        }
      }
    Futures.future {
      if (!SSHDActivity.isConsistent) {
        SSHDService.addLazyInit
        Message.addLazyInit
        AppComponent.LazyInit.init
      }
      ready.set(true)
    }
  }
  @Loggable
  override def onBind(intent: Intent): IBinder = binder
  @Loggable
  override def onRebind(intent: Intent) = super.onRebind(intent)
  @Loggable
  override def onUnbind(intent: Intent): Boolean = {
    super.onUnbind(intent)
    true // Return true if you would like to have the service's onRebind(Intent) method later called when new clients bind to it.
  }
  @Loggable
  override def onDestroy() {
    onDestroyExt(this)
    super.onDestroy()
  }
}

object SSHDService extends Logging {
  @volatile private var service: Option[SSHDService] = None
  Logging.addLogger(FileLogger)
  SSHDCommon
  log.debug("alive")

  def addLazyInit = AppComponent.LazyInit("SSHDService initialize onCreate", 50, DTimeout.longest) {
    service.foreach {
      service =>
        SSHDPreferences.initServicePersistentOptions(service)
        // preload
        Futures.future { AppComponent.Inner.getCachedComponentInfo(SSHDActivity.locale, SSHDActivity.localeLanguage) }
        // prepare
        AppComponent.Inner.state.set(AppComponent.State(DState.Passive))
    }
  }

  @Loggable
  def getExecutableInfo(workdir: String, allowCallFromUI: Boolean = false): Seq[ExecutableInfo] = try {
    val executables = Seq("dropbear", "openssh")
    (for {
      context <- AppComponent.Context
      appNativePath <- AppComponent.Inner.appNativePath
      xml <- AppComponent.Inner.nativeManifest
      info <- AnyBase.info.get
    } yield {
      var executableID = 0
      executables.map(executable => {
        // get or throw block
        val block = (xml \\ "application").find(app => (app \ "name").text == executable).get
        val id = executableID
        var env: Seq[String] = Seq(
          "DIGISSHD_V=" + info.appVersion,
          "DIGISSHD_B=" + info.appBuild)
        val commandLine = executable match {
          case "dropbear" =>
            val masterPassword = SSHAuthentificationMode.getStateExt(context) match {
              case SSHAuthentificationMode.AuthType.SingleUser =>
                SSHDUsers.list.find(_.name == "android") match {
                  case Some(systemUser) =>
                    Some(systemUser.password)
                  case None =>
                    log.fatal("system user not found")
                    None
                }
              case SSHAuthentificationMode.AuthType.MultiUser =>
                None
              case invalid =>
                log.fatal("invalid authenticatin type \"" + invalid + "\"")
                None
            }
            val masterPasswordOption = masterPassword.map(pw => Seq("-Y", pw)).getOrElse(Seq[String]())
            val digiIntegrationOption = if (masterPassword.isEmpty) Seq("-D") else Seq()
            AppControl.Inner.getInternalDirectory(DTimeout.long) match {
              case Some(path) =>
                val rsaKey = if (RSAPublicKeyEncription.getState[Boolean](context))
                  Seq("-r", new File(path, "dropbear_rsa_host_key").getAbsolutePath)
                else
                  Seq()
                val dsaKey = if (DSAPublicKeyEncription.getState[Boolean](context))
                  Seq("-d", new File(path, "dropbear_dss_host_key").getAbsolutePath)
                else
                  Seq()
                Option(System.getenv("PATH")).map(s => {
                  val oldPATH = s.substring(s.indexOf('=') + 1)
                  env = env :+ ("PATH=" + path + ":" + oldPATH)
                })
                val forceHomePathOption = AppControl.Inner.getExternalDirectory() match {
                  case Some(externalPath) if !masterPassword.isEmpty =>
                    Seq("-H", externalPath.getAbsolutePath)
                  case _ =>
                    Seq()
                }
                Some(Seq(new File(path, executable).getAbsolutePath,
                  "-i", // start for inetd
                  "-E", // log to stderr rather than syslog
                  "-F", // don't fork into background
                  "-U", // fake user RW permissions in SFTP
                  "-e") ++ // keep environment variables
                  forceHomePathOption ++ // forced home path
                  rsaKey ++ // use rsakeyfile for the rsa host key
                  dsaKey ++ // use dsskeyfile for the dss host key
                  digiIntegrationOption ++ // DigiNNN integration
                  masterPasswordOption) // enable master password to any account
              case None =>
                val path = new File(".")
                val rsaKey = if (RSAPublicKeyEncription.getState[Boolean](context))
                  Seq("-r", new File(path, "dropbear_rsa_host_key").getAbsolutePath)
                else
                  Seq()
                val dsaKey = if (DSAPublicKeyEncription.getState[Boolean](context))
                  Seq("-d", new File(path, "dropbear_dss_host_key").getAbsolutePath)
                else
                  Seq()
                val forceHomePathOption = if (masterPassword.isEmpty) Seq() else Seq("-H", "/")
                Some(Seq(executable,
                  "-i", // start for inetd
                  "-E", // log to stderr rather than syslog
                  "-F", // don't fork into background
                  "-U", // fake user RW permissions in SFTP
                  "-e") ++ // keep environment variables
                  forceHomePathOption ++ // forced home path
                  rsaKey ++ // use rsakeyfile for the rsa host key
                  dsaKey ++ // use dsskeyfile for the dss host key
                  digiIntegrationOption ++ // DigiNNN integration
                  masterPasswordOption) // enable master password to any account
            }
          case "openssh" => None
        }
        val port = executable match {
          case "dropbear" => Some(NetworkPort.getState[Int](context))
          case "openssh" => None
        }
        val state = DState.Active
        val name = executable
        val version = (block \ "version").text
        val description = (block \ "description").text
        val origin = (block \ "origin").text
        val license = (block \ "license").text
        val project = (block \ "project").text
        executableID += 1
        new ExecutableInfo(id, commandLine, port, env, state, name, version, description, origin, license, project)
      })
    }) getOrElse Seq()
  } catch {
    case e =>
      log.error(e.getMessage, e)
      Seq()
  }
  class Binder(ready: SyncVar[Boolean]) extends ICtrlComponent.Stub with Logging {
    log.debug("binder alive")
    @Loggable(result = false)
    def info(): ComponentInfo = {
      log.debug("process Binder::info")
      SSHDActivity.info
    }
    @Loggable(result = false)
    def uid() = {
      log.debug("process Binder::uid")
      android.os.Process.myUid()
    }
    @Loggable(result = false)
    def size() = {
      log.debug("process Binder::size")
      2
    }
    @Loggable(result = false)
    def pre(id: Int, workdir: String): Boolean = try {
      log.debug("process Binder::pre for id " + id + " at " + workdir)
      ready.get(DTimeout.long).getOrElse({ log.fatal("unable to start DigiSSHD service") })
      (for {
        context <- AppComponent.Context
        appNativePath <- AppComponent.Inner.appNativePath
      } yield {
        assert(id == 0)
        val keyResult = AppControl.Inner.getInternalDirectory(DTimeout.long) match {
          case Some(path) =>
            // create SCP groups helper
            // coreutils groups native failed with exit code 1
            // and message "groups: cannot find name for group ID N"
            // under Android our app uid == gid
            val groups = new File(path, "groups")
            if (!groups.exists) {
              log.debug("create groups stub for SCP")
              Common.writeToFile(groups, "echo %d\n".format(android.os.Process.myUid))
              groups.setReadable(true, false)
              groups.setExecutable(true, false)
            }
            // create security keys
            (if (RSAPublicKeyEncription.getState[Boolean](context)) {
              log.debug("prepare RSA key")
              val rsa_key_source = new File(appNativePath, "dropbear_rsa_host_key")
              val rsa_key_destination = new File(path, "dropbear_rsa_host_key")
              if (rsa_key_source.exists && rsa_key_source.length > 0) {
                IAmMumble("syncronize RSA key with origin")
                Common.copyFile(rsa_key_source, rsa_key_destination) &&
                  rsa_key_destination.setReadable(true, false)
              } else if (rsa_key_destination.exists && rsa_key_destination.length > 0) {
                IAmMumble("restore RSA key from working copy")
                Common.copyFile(rsa_key_destination, rsa_key_source)
              } else {
                if (RSAPublicKeyEncription.generateHostKey(context))
                  Common.copyFile(rsa_key_source, rsa_key_destination) &&
                    rsa_key_destination.setReadable(true, false)
                else
                  false
              }
            } else
              true) &&
              (if (DSAPublicKeyEncription.getState[Boolean](context)) {
                log.debug("prepare DSA key")
                val dss_key_source = new File(appNativePath, "dropbear_dss_host_key")
                val dss_key_destination = new File(path, "dropbear_dss_host_key")
                if (dss_key_source.exists && dss_key_source.length > 0) {
                  IAmMumble("syncronize DSA key with origin")
                  Common.copyFile(dss_key_source, dss_key_destination) &&
                    dss_key_destination.setReadable(true, false)
                } else if (dss_key_destination.exists && dss_key_destination.length > 0) {
                  IAmMumble("restore DSA key from working copy")
                  Common.copyFile(dss_key_destination, dss_key_source)
                } else {
                  if (DSAPublicKeyEncription.generateHostKey(context))
                    Common.copyFile(dss_key_source, dss_key_destination) &&
                      dss_key_destination.setReadable(true, false)
                  else
                    false
                }
              } else
                true)
          case _ =>
            false
        }
        val homeResult = AppControl.Inner.getExternalDirectory(DTimeout.long) match {
          case Some(path) if path != null =>
            val profileFile = new File(path, ".profile")
            if (!profileFile.exists) {
              IAmMumble("Create default user profile")
              Common.writeToFile(profileFile, SSHDUserProfile.content)
            }
          case _ =>
            false
        }
        keyResult
      }) getOrElse false
    } catch {
      case e =>
        log.error(e.getMessage, e)
        false
    }
    @Loggable(result = false)
    def executable(id: Int, workdir: String): ExecutableInfo = {
      log.debug("process Binder::executable for id " + id + " at " + workdir)
      SSHDService.getExecutableInfo(workdir).find(_.executableID == id).getOrElse(null)
    }
    @Loggable(result = false)
    def post(id: Int, workdir: String): Boolean = try {
      log.debug("process Binder::post for id " + id + " at " + workdir)
      assert(id == 0)
      true
    } catch {
      case e =>
        log.error(e.getMessage, e)
        false
    }
    @Loggable(result = false)
    def accessRulesOrder(): Boolean = try {
      log.debug("process Binder::accessRulesOrder")
      AppComponent.Context.map(
        _.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE).
          getBoolean(DOption.ACLConnection.tag, DOption.ACLConnection.default.asInstanceOf[Boolean])).
        getOrElse(DOption.ACLConnection.default.asInstanceOf[Boolean])
    } catch {
      case e =>
        log.error(e.getMessage, e)
        DOption.ACLConnection.default.asInstanceOf[Boolean]
    }
    @Loggable(result = false)
    def readBooleanProperty(property: String): Boolean = try {
      log.debug("process Binder::readBooleanProperty " + property)
      val dprop = DOption.withName(property).asInstanceOf[DOption.OptVal]
      AppComponent.Context.map(
        _.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE).
          getBoolean(dprop.tag, dprop.default.asInstanceOf[Boolean])).
        getOrElse(dprop.default.asInstanceOf[Boolean])
    } catch {
      case e =>
        log.error(e.getMessage, e)
        false
    }
    @Loggable(result = false)
    def readIntProperty(property: String): Int = try {
      log.debug("process Binder::readIntProperty " + property)
      val dprop = DOption.withName(property).asInstanceOf[DOption.OptVal]
      AppComponent.Context.map(
        _.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE).
          getInt(dprop.tag, dprop.default.asInstanceOf[Int])).
        getOrElse(dprop.asInstanceOf[Int])
    } catch {
      case e =>
        log.error(e.getMessage, e)
        Int.MinValue
    }
    @Loggable(result = false)
    def readStringProperty(property: String): String = try {
      log.debug("process Binder::readStringProperty " + property)
      val dprop = DOption.withName(property).asInstanceOf[DOption.OptVal]
      AppComponent.Context.map(
        _.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE).
          getString(dprop.tag, dprop.default.asInstanceOf[String])).
        getOrElse(dprop.default.asInstanceOf[String])
    } catch {
      case e =>
        log.error(e.getMessage, e)
        null
    }
    @Loggable(result = false)
    def accessAllowRules(): java.util.List[java.lang.String] = try {
      log.debug("process Binder::accessAllowRules")
      AppComponent.Context.map(c => SSHDPreferences.FilterConnection.Allow.get(c).
        filter(t => t._2).map(_._1)).getOrElse(Seq()).toList
    } catch {
      case e =>
        log.error(e.getMessage, e)
        List()
    }
    @Loggable(result = false)
    def accessDenyRules(): java.util.List[java.lang.String] = try {
      log.debug("process Binder::accessDenyRules")
      AppComponent.Context.map(c => SSHDPreferences.FilterConnection.Deny.get(c).
        filter(t => t._2).map(_._1)).getOrElse(Seq()).toList
    } catch {
      case e =>
        log.error(e.getMessage, e)
        List()
    }
    @Loggable(result = false)
    def interfaceRules(): java.util.Map[_, _] = try {
      log.debug("process Binder::interfaceRules")
      AppComponent.Context.map(
        _.getSharedPreferences(DPreference.FilterInterface, Context.MODE_PRIVATE).getAll).
        getOrElse(new java.util.HashMap[String, Any]())
    } catch {
      case e =>
        log.error(e.getMessage, e)
        new java.util.HashMap[String, Any]()
    }
    @Loggable(result = false)
    def user(name: String): UserInfo = try {
      log.debug("process Binder::user " + name)
      AppComponent.Context.flatMap {
        context =>
          SSHDUsers.find(context, name).map(user => {
            val userHome = SSHDUsers.homeDirectory(context, user)
            user.copy(password = Hash.crypt(user.password), home = userHome.getAbsolutePath)
          })
      } getOrElse null
    } catch {
      case e =>
        log.error(e.getMessage, e)
        null
    }
  }
}
