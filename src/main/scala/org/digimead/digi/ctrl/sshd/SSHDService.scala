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
import scala.collection.JavaConversions._

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.declaration.DOption.OptVal.value2string_id
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.info.ComponentInfo
import org.digimead.digi.ctrl.lib.info.ExecutableInfo
import org.digimead.digi.ctrl.lib.log.AndroidLogger
import org.digimead.digi.ctrl.lib.log.FileLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.Service
import org.digimead.digi.ctrl.ICtrlComponent

import android.content.Context
import android.content.Intent
import android.os.IBinder

class SSHDService extends Service {
  private lazy val binder = new SSHDService.Binder
  log.debug("alive")
  @Loggable
  override def onCreate() = {
    super.onCreate()
    future { AppActivity.LazyInit.init }
  }
  @Loggable
  override def onBind(intent: Intent): IBinder = binder
  @Loggable
  override def onRebind(intent: Intent) = super.onRebind(intent)
  @Loggable
  override def onUnbind(intent: Intent): Boolean = super.onUnbind(intent)
}

object SSHDService extends Logging {
  val locale = Locale.getDefault().getLanguage() + "_" + Locale.getDefault().getCountry()
  val localeLanguage = Locale.getDefault().getLanguage()
  if (true)
    Logging.addLogger(Seq(AndroidLogger, FileLogger))
  else
    Logging.addLogger(FileLogger)
  log.debug("alive")

  @Loggable
  def getExecutableInfo(workdir: String): Seq[ExecutableInfo] = try {
    val executables = Seq("dropbear", "openssh")
    (for {
      context <- AppActivity.Context
      appNativePath <- AppActivity.Inner.appNativePath
      xml <- AppActivity.Inner.nativeManifest
    } yield {
      var executableID = 0
      executables.map(executable => {
        // get or throw block
        val block = (xml \\ "application").find(app => (app \ "name").text == executable).get
        val id = executableID
        val commandLine = executable match {
          case "dropbear" =>
            val dropbear = new File(appNativePath, executable)
            val masterPassword = Option("123")
            val masterPasswordOption = masterPassword.map(pw => Seq("-Y", pw)).flatten.toSeq
            Some(Seq(dropbear.getAbsolutePath(),
              "-i", // Start for inetd
              "-E", // Log to stderr rather than syslog
              "-F", // Don't fork into background
              "-d", workdir + "/dropbear_dss_host_key", // Use dsskeyfile for the dss host key
              "-r", workdir + "/dropbear_rsa_host_key") ++ // Use rsakeyfile for the rsa host key
              masterPasswordOption) // Enable master password to any account
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
  class Binder extends ICtrlComponent.Stub with Logging {
    log.debug("binder alive")
    @Loggable(result = false)
    def info(): ComponentInfo =
      AppActivity.Inner.getComponentInfo(locale, localeLanguage).getOrElse(null)
    @Loggable(result = false)
    def uid() = android.os.Process.myUid()
    @Loggable(result = false)
    def size() = 2
    @Loggable(result = false)
    def pre(id: Int, workdir: String) = {
      for {
        appNativePath <- AppActivity.Inner.appNativePath
      } yield {
        assert(id == 0)
        val privateKeysPath = new File(workdir)
        val rsa_key = new File(privateKeysPath, "dropbear_rsa_host_key")
        val dss_key = new File(privateKeysPath, "dropbear_dss_host_key")
        if (rsa_key.exists() && dss_key.exists()) {
          true
        } else {
          log.debug("private key path: " + privateKeysPath)
          val dropbearkey = new File(appNativePath, "dropbearkey").getAbsolutePath()
          def generateKey(args: Array[String]): Boolean = {
            log.debug("generate " + args(1) + " key")
            val p = Runtime.getRuntime().exec(dropbearkey +: args)
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
          try {
            if (rsa_key.exists())
              rsa_key.delete()
            if (dss_key.exists())
              dss_key.delete()
            generateKey(Array("-t", "rsa", "-f", rsa_key.getAbsolutePath())) &&
              generateKey(Array("-t", "dss", "-f", dss_key.getAbsolutePath())) &&
              Common.execChmod("o+r", rsa_key) &&
              Common.execChmod("o+r", dss_key)
          } catch {
            case e =>
              log.error(e.getMessage(), e)
              false
          }
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
      AppActivity.Context.map(
        _.getSharedPreferences(DPreference.Main, Context.MODE_WORLD_READABLE).
          getBoolean(DOption.ACLConnection, DOption.ACLConnection.default.asInstanceOf[Boolean])).
        getOrElse(DOption.ACLConnection.default.asInstanceOf[Boolean])
    } catch {
      case e =>
        log.error(e.getMessage, e)
        DOption.ACLConnection.default.asInstanceOf[Boolean]
    }
    @Loggable(result = false)
    def accessRuleImplicitInteractive(): Boolean = try {
      AppActivity.Context.map(
        _.getSharedPreferences(DPreference.Main, Context.MODE_WORLD_READABLE).
          getBoolean(DOption.ConfirmConn, DOption.ConfirmConn.default.asInstanceOf[Boolean])).
        getOrElse(DOption.ConfirmConn.default.asInstanceOf[Boolean])
    } catch {
      case e =>
        log.error(e.getMessage, e)
        DOption.ConfirmConn.default.asInstanceOf[Boolean]
    }
    @Loggable(result = false)
    def accessAllowRules(): java.util.List[java.lang.String] = try {
      AppActivity.Context.map(
        _.getSharedPreferences(DPreference.FilterConnectionAllow, Context.MODE_WORLD_READABLE).
          getAll.filter(t => t._2.asInstanceOf[Boolean]).map(_._1).toSeq).getOrElse(Seq()).toList
    } catch {
      case e =>
        log.error(e.getMessage, e)
        List()
    }
    @Loggable(result = false)
    def accessDenyRules(): java.util.List[java.lang.String] = try {
      AppActivity.Context.map(
        _.getSharedPreferences(DPreference.FilterConnectionDeny, Context.MODE_WORLD_READABLE).
          getAll.filter(t => t._2.asInstanceOf[Boolean]).map(_._1).toSeq).getOrElse(Seq()).toList
    } catch {
      case e =>
        log.error(e.getMessage, e)
        List()
    }

  }
}
