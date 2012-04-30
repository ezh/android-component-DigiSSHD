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
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package org.digimead.digi.ctrl.sshd

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Locale

import scala.Array.canBuildFrom
import scala.Option.option2Iterable
import scala.actors.Futures.future
import scala.annotation.elidable
import scala.collection.JavaConversions._

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.base.AppControl
import org.digimead.digi.ctrl.lib.declaration.DOption.OptVal.value2string_id
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.info.ComponentInfo
import org.digimead.digi.ctrl.lib.info.ExecutableInfo
import org.digimead.digi.ctrl.lib.log.AndroidLogger
import org.digimead.digi.ctrl.lib.log.FileLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.lib.message.IAmWarn
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.SyncVar
import org.digimead.digi.ctrl.lib.Service
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.service.{ OptionBlock => ServiceOptions }
import org.digimead.digi.ctrl.ICtrlComponent

import android.content.Context
import android.content.Intent
import android.os.IBinder
import annotation.elidable.ASSERTION

class SSHDService extends Service {
  private val ready = new SyncVar[Boolean]()
  private val binder = new SSHDService.Binder(ready)
  log.debug("alive")

  @Loggable
  override def onCreate() = {
    super.onCreate()
    SSHDService.addLazyInit
    future {
      AppComponent.LazyInit.init
      AppControl.Inner.bind(this)
      ready.set(true)
    }
  }
  @Loggable
  override def onBind(intent: Intent): IBinder = binder
  @Loggable
  override def onRebind(intent: Intent) = super.onRebind(intent)
  @Loggable
  override def onUnbind(intent: Intent): Boolean = super.onUnbind(intent)
}

object SSHDService extends Logging {
  if (SSHDActivity.DEBUG)
    Logging.addLogger(Seq(AndroidLogger, FileLogger))
  else
    Logging.addLogger(FileLogger)
  log.debug("alive")

  def addLazyInit = AppComponent.LazyInit("SSHDService initialize onCreate", 50) {
    future { AppComponent.Inner.getCachedComponentInfo(SSHDActivity.locale, SSHDActivity.localeLanguage) }
    // TODO
    /*    for {
      context <- AppComponent.Context
      info <- AnyBase.info.get
    } {
      AppComponent.Inner.state.set(AppComponent.State(DState.Passive))
      IAmBusy(SSHDService, "service environment verification")
      AppControl.Inner ! AppControl.Message.Prepare(context.getPackageName, info.appBuild, (r) => {
        if (!r) AppComponent.Inner.state.set(AppComponent.State(DState.Broken, "inconsistent service environment"))
        IAmReady(SSHDService, "service environment verified")
      })
    }
     getComponentStatus
    */
  }
  @Loggable
  def getExecutableInfo(workdir: String, allowCallFromUI: Boolean = false): Seq[ExecutableInfo] = try {
    val executables = Seq("dropbear", "openssh")
    (for {
      context <- AppComponent.Context
      appNativePath <- AppComponent.Inner.appNativePath
      xml <- AppComponent.Inner.nativeManifest
    } yield {
      var executableID = 0
      executables.map(executable => {
        // get or throw block
        val block = (xml \\ "application").find(app => (app \ "name").text == executable).get
        val id = executableID
        val commandLine = executable match {
          case "dropbear" =>
            val internalPath = new SyncVar[File]()
            val externalPath = new SyncVar[File]()
            AppControl.Inner.callListDirectories(context.getPackageName, allowCallFromUI)() match {
              case Some((internal, external)) =>
                internalPath.set(new File(internal))
                externalPath.set(new File(external))
              case r =>
                log.warn("unable to get component directories, result " + r)
                internalPath.set(null)
                externalPath.set(null)
            }
            internalPath.get(DTimeout.long) match {
              case Some(path) if path != null =>
                val masterPassword = Option(ServiceOptions.authPasswordItem.getState[String](context))
                val masterPasswordOption = masterPassword.map(pw => Seq("-Y", pw)).flatten.toSeq
                Some(Seq(new File(path, executable).getAbsolutePath,
                  "-i", // Start for inetd
                  "-E", // Log to stderr rather than syslog
                  "-F", // Don't fork into background
                  "-H", externalPath.get(0).getOrElse(path).getAbsolutePath, // forced home path
                  "-d", new File(path, "dropbear_dss_host_key").getAbsolutePath, // Use dsskeyfile for the dss host key
                  "-r", new File(path, "dropbear_rsa_host_key").getAbsolutePath) ++ // Use rsakeyfile for the rsa host key
                  masterPasswordOption) // Enable master password to any account
              case Some(path) =>
                val masterPassword = Option(ServiceOptions.authPasswordItem.getState[String](context))
                val masterPasswordOption = masterPassword.map(pw => Seq("-Y", pw)).flatten.toSeq
                Some(Seq(executable,
                  "-i", // Start for inetd
                  "-E", // Log to stderr rather than syslog
                  "-F", // Don't fork into background
                  "-H", "/", // forced home path
                  "-d", new File(path, "dropbear_dss_host_key").getAbsolutePath, // Use dsskeyfile for the dss host key
                  "-r", new File(path, "dropbear_rsa_host_key").getAbsolutePath) ++ // Use rsakeyfile for the rsa host key
                  masterPasswordOption) // Enable master password to any account
              case _ =>
                None
            }
          case "openssh" => None
        }
        val port = executable match {
          case "dropbear" =>
            val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_WORLD_READABLE)
            val port = pref.getInt(DOption.Port, 2222)
            Some(port)
          case "openssh" => None
        }
        val env = Seq()
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
  private def generateKey(kind: String, keyFile: File, path: File): Boolean = try {
    log.debug("private key path: " + path)
    if (keyFile.exists())
      keyFile.delete()
    val dropbearkey = new File(path, "dropbearkey").getAbsolutePath()
    log.debug("generate " + kind + " key")
    val result = {
      val p = Runtime.getRuntime().exec(dropbearkey +: Array("-t", kind, "-f", keyFile.getAbsolutePath()))
      val err = new BufferedReader(new InputStreamReader(p.getErrorStream()))
      p.waitFor()
      val retcode = p.exitValue()
      if (retcode != 0) {
        var error = err.readLine()
        while (error != null) {
          log.error(dropbearkey + " error: " + error)
          error = err.readLine()
        }
        false
      } else
        true
    }
    if (result)
      try { Android.execChmod(644, keyFile, false) } catch { case e => log.warn(e.getMessage) }
    result
  } catch {
    case e =>
      log.error(e.getMessage(), e)
      false
  }
  class Binder(ready: SyncVar[Boolean]) extends ICtrlComponent.Stub with Logging {
    log.debug("binder alive")
    @Loggable(result = false)
    def info(): ComponentInfo =
      SSHDActivity.info
    @Loggable(result = false)
    def uid() = android.os.Process.myUid()
    @Loggable(result = false)
    def size() = 2
    @Loggable(result = false)
    def pre(id: Int, workdir: String) = {
      ready.get(DTimeout.long).getOrElse({ log.fatal("unable to start DigiSSHD service") })
      for {
        context <- AppComponent.Context
        appNativePath <- AppComponent.Inner.appNativePath
      } yield {
        assert(id == 0)
        val internalPath = new SyncVar[File]()
        AppControl.Inner.callListDirectories(context.getPackageName)() match {
          case Some((internal, external)) =>
            internalPath.set(new File(internal))
          case _ =>
            log.warn("unable to get component directories")
            internalPath.set(null)
        }
        internalPath.get(DTimeout.long) match {
          case Some(path) if path != null =>
            (if (ServiceOptions.rsaItem.getState[Boolean](context)) {
              val rsa_key = new File(path, "dropbear_rsa_host_key")
              if (rsa_key.exists()) {
                IAmMumble("RSA key exists")
                true
              } else {
                IAmMumble("RSA key generation")
                if (generateKey("rsa", rsa_key, path)) {
                  IAmMumble("RSA key generated")
                  true
                } else {
                  IAmWarn("RSA key generation failed")
                  false
                }
              }
            } else
              true) &&
              (if (ServiceOptions.dssItem.getState[Boolean](context)) {
                val dss_key = new File(path, "dropbear_dss_host_key")
                if (dss_key.exists()) {
                  IAmMumble("DSA key exists")
                  true
                } else {
                  IAmMumble("DSA key generation")
                  if (generateKey("dss", dss_key, path)) {
                    IAmMumble("DSA key generated")
                    true
                  } else {
                    IAmWarn("DSA key generation failed")
                    false
                  }
                }
              } else
                true)
          case _ =>
            false
        }
      }
    } getOrElse false
    @Loggable(result = false)
    def executable(id: Int, workdir: String): ExecutableInfo =
      SSHDService.getExecutableInfo(workdir).find(_.executableID == id).getOrElse(null)
    @Loggable(result = false)
    def post(id: Int, workdir: String): Boolean = {
      log.debug("post(...)")
      assert(id == 0)
      true
    }
    @Loggable(result = false)
    def accessRulesOrder(): Boolean = try {
      AppComponent.Context.map(
        _.getSharedPreferences(DPreference.Main, Context.MODE_WORLD_READABLE).
          getBoolean(DOption.ACLConnection, DOption.ACLConnection.default.asInstanceOf[Boolean])).
        getOrElse(DOption.ACLConnection.default.asInstanceOf[Boolean])
    } catch {
      case e =>
        log.error(e.getMessage, e)
        DOption.ACLConnection.default.asInstanceOf[Boolean]
    }
    @Loggable(result = false)
    def readBooleanProperty(property: Int): Boolean = try {
      AppComponent.Context.map(
        _.getSharedPreferences(DPreference.Main, Context.MODE_WORLD_READABLE).
          getBoolean(DOption(property).asInstanceOf[DOption.OptVal].r,
            DOption(property).asInstanceOf[DOption.OptVal].default.asInstanceOf[Boolean])).
        getOrElse(DOption(property).asInstanceOf[DOption.OptVal].default.asInstanceOf[Boolean])
    } catch {
      case e =>
        log.error(e.getMessage, e)
        false
    }
    @Loggable(result = false)
    def readIntProperty(property: Int): Int = try {
      AppComponent.Context.map(
        _.getSharedPreferences(DPreference.Main, Context.MODE_WORLD_READABLE).
          getInt(DOption(property).asInstanceOf[DOption.OptVal].r,
            DOption(property).asInstanceOf[DOption.OptVal].default.asInstanceOf[Int])).
        getOrElse(DOption(property).asInstanceOf[DOption.OptVal].default.asInstanceOf[Int])
    } catch {
      case e =>
        log.error(e.getMessage, e)
        Int.MinValue
    }
    @Loggable(result = false)
    def readStringProperty(property: Int): String = try {
      AppComponent.Context.map(
        _.getSharedPreferences(DPreference.Main, Context.MODE_WORLD_READABLE).
          getString(DOption(property).asInstanceOf[DOption.OptVal].r,
            DOption(property).asInstanceOf[DOption.OptVal].default.asInstanceOf[String])).
        getOrElse(DOption(property).asInstanceOf[DOption.OptVal].default.asInstanceOf[String])
    } catch {
      case e =>
        log.error(e.getMessage, e)
        null
    }
    @Loggable(result = false)
    def accessAllowRules(): java.util.List[java.lang.String] = try {
      AppComponent.Context.map(
        _.getSharedPreferences(DPreference.FilterConnectionAllow, Context.MODE_WORLD_READABLE).
          getAll.filter(t => t._2.asInstanceOf[Boolean]).map(_._1).toSeq).getOrElse(Seq()).toList
    } catch {
      case e =>
        log.error(e.getMessage, e)
        List()
    }
    @Loggable(result = false)
    def accessDenyRules(): java.util.List[java.lang.String] = try {
      AppComponent.Context.map(
        _.getSharedPreferences(DPreference.FilterConnectionDeny, Context.MODE_WORLD_READABLE).
          getAll.filter(t => t._2.asInstanceOf[Boolean]).map(_._1).toSeq).getOrElse(Seq()).toList
    } catch {
      case e =>
        log.error(e.getMessage, e)
        List()
    }
    @Loggable(result = false)
    def interfaceRules(): java.util.Map[_, _] = try {
      AppComponent.Context.map(
        _.getSharedPreferences(DPreference.FilterInterface, Context.MODE_WORLD_READABLE).getAll).
        getOrElse(new java.util.HashMap[String, Any]())
    } catch {
      case e =>
        log.error(e.getMessage, e)
        new java.util.HashMap[String, Any]()
    }
  }
}
