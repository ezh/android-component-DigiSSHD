/*
 * DigiSSHD - DigiINETD component for Android Platform
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

package org.digimead.digi.inetd.sshd

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

import org.digimead.digi.inetd.lib.aop.Loggable
import org.digimead.digi.inetd.lib.AppActivity
import org.digimead.digi.inetd.lib.Common
import org.digimead.digi.inetd.lib.ServiceBase
import org.digimead.digi.inetd.IINETDComponent
import org.slf4j.LoggerFactory

import android.content.Intent
import android.os.IBinder

class SSHDService extends ServiceBase {
  private val log = LoggerFactory.getLogger(getClass.getName().replaceFirst("org.digimead.digi.inetd", "o.d.d.i"))
  private lazy val binder = new IINETDComponent.Stub() {
    log.debug("binder alive")
    @Loggable
    def uid() = android.os.Process.myUid()
    @Loggable
    def size() = 1
    @Loggable
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
    @Loggable
    def getExecutable(id: Int, workdir: String): java.util.List[_] = {
      for {
        inner <- AppActivity.Inner
        appNativePath <- inner.appNativePath
      } yield {
        assert(id == 0)
        val dropbear = new File(appNativePath, "dropbear")
        val r = new Common.ServiceEnvironment(0, Array(
          dropbear.getAbsolutePath(), "-i", "-E", "-F",
          "-d", workdir + "/dropbear_dss_host_key",
          "-r", workdir + "/dropbear_rsa_host_key",
          "-Y", "123"), 2222)
        Common.serializeToList(r)
      }
    } getOrElse null
    @Loggable
    def post(id: Int, workdir: String): Boolean = {
      log.debug("post(...)")
      assert(id == 0)
      true
    }
  }
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
