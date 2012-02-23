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
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.AppActivity
import org.digimead.digi.ctrl.lib.Common
import org.digimead.digi.ctrl.lib.base.Service
import org.digimead.digi.ctrl.ICtrlComponent
import org.slf4j.LoggerFactory
import android.content.Intent
import android.os.IBinder
import scala.xml._
import org.digimead.digi.ctrl.lib.aop.Logging
import org.digimead.digi.ctrl.lib.info.ComponentInfo
import java.util.Locale

class SSHDService extends Service {
  private lazy val binder = new SSHDService.Binder
  log.debug("alive")
  @Loggable
  override def onCreate() = super.onCreate()
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
  @Loggable
  def getExecutableEnvironments(workdir: String): Seq[Common.ExecutableEnvironment] = try {
    val executables = Seq("dropbear", "openssh")
    (for {
      inner <- AppActivity.Inner
      appNativePath <- inner.appNativePath
      xml <- inner.nativeManifest
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
          case "dropbear" => Some(2222)
          case "openssh" => None
        }
        val env = Seq()
        val state = Common.State.Active
        val name = executable
        val version = (block \ "version").text
        val description = (block \ "description").text
        val origin = (block \ "origin").text
        val license = (block \ "license").text
        val project = (block \ "project").text
        executableID += 1
        new Common.ExecutableEnvironment(id, commandLine, port, env, state, name, version, description, origin, license, project)
      })
    }) getOrElse Seq()
  } catch {
    case e =>
      log.error(e.getMessage, e)
      Seq()
  }
  @Loggable
  def getComponentInfo(): Option[ComponentInfo] = {
    for {
      inner <- AppActivity.Inner
      appManifest <- inner.applicationManifest
    } yield {
      val extractor = (icons: Seq[(ComponentInfo.IconType, String)]) => {
        icons.map {
          case (iconType, url) =>
            iconType match {
              case icon: ComponentInfo.Thumbnail.type =>
                None
              case icon: ComponentInfo.LDPI.type =>
                None
              case icon: ComponentInfo.MDPI.type =>
                None
              case icon: ComponentInfo.HDPI.type =>
                None
              case icon: ComponentInfo.XHDPI.type =>
                None
            }
        }
      }
      ComponentInfo(appManifest, locale, localeLanguage, extractor)
    }
  } getOrElse None
  class Binder extends ICtrlComponent.Stub with Logging {
    log.debug("binder alive")
    @Loggable(result = false)
    def info(): java.util.List[_] =
      SSHDService.getComponentInfo().map(Common.serializeToList(_)) getOrElse null
    @Loggable(result = false)
    def uid() = android.os.Process.myUid()
    @Loggable(result = false)
    def size() = 2
    @Loggable(result = false)
    def pre(id: Int, workdir: String) = {
      for {
        inner <- AppActivity.Inner
        appNativePath <- inner.appNativePath
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
    def executable(id: Int, workdir: String): java.util.List[_] =
      SSHDService.getExecutableEnvironments(workdir).find(_.id == id).map(Common.serializeToList(_)) getOrElse null
    @Loggable(result = false)
    def post(id: Int, workdir: String): Boolean = {
      log.debug("post(...)")
      assert(id == 0)
      true
    }
  }
  /*    val id = 
      val name = 
        val version = 
  case class ComponentInfo(id, name, version, description,
    val project: String, // Uri Not Serializable
    val thumb: Option[Array[Byte]], // Bitmap Not Serializable
    val origin: String,
    val license: String,
    val email: String,
    val iconHDPI: Array[Byte], // Bitmap Not Serializable
    val iconLDPI: Array[Byte], // Bitmap Not Serializable
    val iconMDPI: Array[Byte], // Bitmap Not Serializable
    val iconXHDPI: Array[Byte], // Bitmap Not Serializable
    val market: String,
    val componentPackage: String) extends java.io.Serializable {
    def getDescription(): String = {
      Seq("Name: " + name,
        "Version: " + version,
        "Description: " + description,
        "Project: " + project,
        "Market: " + market,
        "License: " + license,
        "E-Mail: " + email,
        "Origin: " + origin).mkString("\n")
    }*/
}