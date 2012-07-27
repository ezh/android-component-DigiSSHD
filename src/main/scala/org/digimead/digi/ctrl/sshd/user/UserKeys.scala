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

package org.digimead.digi.ctrl.sshd.user

import java.io.BufferedReader
import java.io.File
import java.io.FileFilter
import java.io.FileWriter
import java.io.FilenameFilter
import java.io.InputStreamReader
import java.io.OutputStream

import scala.Array.canBuildFrom
import scala.actors.Futures

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.base.AppControl
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.dialog.FileChooser
import org.digimead.digi.ctrl.lib.info.UserInfo
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.Origin.anyRefToOrigin
import org.digimead.digi.ctrl.lib.message.IAmBusy
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.lib.message.IAmReady
import org.digimead.digi.ctrl.lib.message.IAmWarn
import org.digimead.digi.ctrl.lib.message.IAmYell
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.sshd.Message.dispatcher

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object UserKeys extends Logging {
  @Loggable
  def getDropbearKeyFile(context: Context, user: UserInfo): Option[File] = AppComponent.Inner.appNativePath flatMap {
    appNativePath =>
      if (user.name != "android") {
        val homeDir = new File(user.home)
        val sshDir = new File(homeDir, ".ssh")
        Some(new File(sshDir, "dropbear_user_key." + Uri.encode(user.name)))
      } else {
        AppControl.Inner.getExternalDirectory(DTimeout.long).flatMap {
          androidHome =>
            val sshDir = new File(androidHome, ".ssh")
            Some(new File(sshDir, "dropbear_user_key." + Uri.encode(user.name)))
        }
      }
  }
  @Loggable def getPublicKeyFile(context: Context, user: UserInfo): Option[File] =
    getDropbearKeyFile(context, user).map(file => new File(file.getParentFile, "public_user_key." + Uri.encode(user.name)))
  @Loggable def getAuthorizedKeysFile(context: Context, user: UserInfo): Option[File] =
    getDropbearKeyFile(context, user).map(file => new File(file.getParentFile, "authorized_keys"))
  @Loggable def getOpenSSHKeyFile(context: Context, user: UserInfo): Option[File] =
    getDropbearKeyFile(context, user).map(file => new File(file.getParentFile, "openssh_user_key." + Uri.encode(user.name)))
  @Loggable
  def importKey(activity: Activity, user: UserInfo) {
    /*AppComponent.Inner.showDialogSafe[Dialog](activity, "service_import_userkey_dialog", () => {
      val dialog = FileChooser.createDialog(activity,
        Android.getString(activity, "dialog_import_key").getOrElse("Import public key"),
        new File("/"),
        importKeyOnResult,
        new FileFilter { override def accept(file: File) = true },
        importKeyOnClick,
        false,
        user)
      dialog.show()
      dialog
    })*/
  }
  def importKeyOnClick(context: Context, file: File): Boolean = try {
    if (scala.io.Source.fromFile(file).getLines.filter(_.startsWith("ssh-")).isEmpty) {
      Toast.makeText(context, "public key \"" + file.getName + "\" is broken", Toast.LENGTH_LONG).show
      false
    } else
      true
  } catch {
    case e =>
      Toast.makeText(context, "public key \"" + file.getName + "\" is broken", Toast.LENGTH_LONG).show
      false
  }
  @Loggable
  def importKeyOnResult(context: Context, path: File, files: Seq[File], stash: Any) = try {
    val user = stash.asInstanceOf[UserInfo]
    (for {
      importFileFrom <- files.headOption
      importFileTo <- getPublicKeyFile(context, user)
    } yield {
      IAmWarn("import " + importFileFrom.getName + " as " + importFileTo)
      if (importFileTo.exists())
        importFileTo.delete()
      if (!importFileTo.getParentFile.exists)
        importFileTo.getParentFile.mkdirs
      if (Common.copyFile(importFileFrom, importFileTo)) {
        updateAuthorizedKeys(context, user)
        Toast.makeText(context, Android.getString(context, "import_public_key_successful").
          getOrElse("import \"%s\" key succesful").format(importFileFrom.getName), Toast.LENGTH_SHORT).show
      } else
        Toast.makeText(context, Android.getString(context, "import_public_key_failed").
          getOrElse("import \"%s\" key failed").format(importFileFrom.getName), Toast.LENGTH_LONG).show
    }) getOrElse {
      Toast.makeText(context, Android.getString(context, "import_public_key_canceled").getOrElse("import failed"), Toast.LENGTH_LONG).show
    }
  } catch {
    case e =>
      Toast.makeText(context, Android.getString(context, "import_public_key_canceled").getOrElse("import failed"), Toast.LENGTH_LONG).show
      log.error(e.getMessage, e)
  }
  @Loggable
  def exportDropbearKey(context: Context, user: UserInfo) = try {
    getDropbearKeyFile(context, user) match {
      case Some(file) if file.exists && file.length > 0 =>
        IAmMumble("export Dropbear private key")
        val intent = new Intent(Intent.ACTION_SEND)
        intent.setType("application/octet-stream")
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
        context.startActivity(Intent.createChooser(intent, Android.getString(context, "export_dropbear_key").getOrElse("Export Dropbear key")))
      case _ =>
        val message = "unable to export unexists/broken Dropbear private key"
        AnyBase.runOnUiThread { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
        IAmWarn(message)
    }
  } catch {
    case e =>
      val message = "unable to export Dropbear key: " + e.getMessage
      AnyBase.runOnUiThread { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
      IAmWarn(message)
      log.error(e.getMessage(), e)
  }
  @Loggable
  def exportOpenSSHKey(context: Context, user: UserInfo) = try {
    getOpenSSHKeyFile(context, user) match {
      case Some(file) if file.exists && file.length > 0 =>
        IAmMumble("export OpenSSH private key")
        val intent = new Intent(Intent.ACTION_SEND)
        intent.setType("application/octet-stream")
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
        context.startActivity(Intent.createChooser(intent, Android.getString(context, "export_openssh_key").getOrElse("Export OpenSSH key")))
      case _ =>
        val message = "unable to export unexists/broken OpenSSH private key"
        AnyBase.runOnUiThread { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
        IAmWarn(message)
    }
  } catch {
    case e =>
      val message = "unable to export OpenSSH key: " + e.getMessage
      AnyBase.runOnUiThread { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
      IAmWarn(message)
      log.error(e.getMessage(), e)
  }
  @Loggable
  def generateDSAKey(context: Context, user: UserInfo): Boolean = try {
    IAmBusy(this, "1024-bit DSA user key generation")
    generatePrivateKey(context, user, "-t", "dss") &&
      writePublicKey(context, user) &&
      writeOpenSSHKey(context, user) &&
      updateAuthorizedKeys(context, user)
  } catch {
    case e =>
      log.error(e.getMessage(), e)
      false
  } finally {
    IAmReady(this, "DSA key generated")
  }
  @Loggable
  def generateRSAKey(context: Context, user: UserInfo, length: Int): Boolean = try {
    IAmBusy(this, length + "-bit RSA user key generation")
    generatePrivateKey(context, user, "-t", "rsa", "-s", length.toString) &&
      writePublicKey(context, user) &&
      writeOpenSSHKey(context, user) &&
      updateAuthorizedKeys(context, user)
  } catch {
    case e =>
      log.error(e.getMessage(), e)
      false
  } finally {
    IAmReady(this, "RSA key generated")
  }
  @Loggable
  def updateAuthorizedKeys(context: Context, user: UserInfo): Boolean = {
    for {
      fileAuthorizedKeys <- getAuthorizedKeysFile(context, user)
    } yield {
      IAmMumble("update authorized_keys file at " + fileAuthorizedKeys.getAbsolutePath())
      var fw: FileWriter = null
      try {
        if (fileAuthorizedKeys.exists())
          fileAuthorizedKeys.delete()
        if (!fileAuthorizedKeys.getParentFile.exists)
          fileAuthorizedKeys.getParentFile.mkdirs
        fw = new FileWriter(fileAuthorizedKeys, false)
        val pathAuthorizedKeys = fileAuthorizedKeys.getParentFile
        val publicKeys = pathAuthorizedKeys.listFiles(new FilenameFilter {
          def accept(dir: File, name: String) = name.startsWith("public_user_key.")
        })
        log.debug("accumulate public keys from: " + publicKeys.map(_.getName.substring(16)).mkString(", "))
        publicKeys.foreach(f => fw.write(scala.io.Source.fromFile(f).getLines.
          filter(_.startsWith("ssh-")).mkString("\n") + "\n"))
        fw.flush
        true
      } catch {
        case e =>
          IAmYell(e.getMessage, e)
          false
      } finally {
        if (fw != null)
          fw.close
      }
    }
  } getOrElse false
  private def generatePrivateKey(context: Context, user: UserInfo, args: String*): Boolean = try {
    (for {
      keyDropbear <- getDropbearKeyFile(context, user)
      path <- AppControl.Inner.getInternalDirectory(DTimeout.long)
    } yield {
      IAmMumble("generate Dropbear key " + keyDropbear.getAbsolutePath())
      val dropbearkey = new File(path, "dropbearkey").getAbsolutePath()
      if (keyDropbear.exists())
        keyDropbear.delete()
      if (!keyDropbear.getParentFile.exists)
        keyDropbear.getParentFile.mkdirs
      val result = {
        val p = Runtime.getRuntime().exec(Array(dropbearkey, "-f", keyDropbear.getAbsolutePath()) ++ args)
        val err = new BufferedReader(new InputStreamReader(p.getErrorStream()))
        p.waitFor()
        val retcode = p.exitValue()
        if (retcode != 0) {
          var error = err.readLine()
          while (error != null) {
            log.error(dropbearkey + " error: " + error)
            IAmYell("dropbearkey: " + error)
            error = err.readLine()
          }
          false
        } else
          true
      }
      if (result)
        keyDropbear.setReadable(true, false)
      result
    }) getOrElse false
  } catch {
    case e =>
      log.error(e.getMessage, e)
      false
  }
  private def writePublicKey(context: Context, user: UserInfo): Boolean = try {
    (for {
      keyDropbear <- getDropbearKeyFile(context, user)
      keyPublic <- getPublicKeyFile(context, user)
      path <- AppControl.Inner.getInternalDirectory(DTimeout.long)
    } yield {
      IAmMumble("write public key to " + keyPublic.getAbsolutePath())
      if (keyDropbear.exists) {
        val dropbearkey = new File(path, "dropbearkey").getAbsolutePath()
        if (keyPublic.exists())
          keyPublic.delete()
        if (!keyPublic.getParentFile.exists)
          keyPublic.getParentFile.mkdirs
        val p = Runtime.getRuntime().exec(Array(dropbearkey, "-f", keyDropbear.getAbsolutePath(), "-y"))
        val err = new BufferedReader(new InputStreamReader(p.getErrorStream()))
        Futures.future {
          var bufferedOutput: OutputStream = null
          try {
            val lines = scala.io.Source.fromInputStream(p.getInputStream()).getLines.filter(_.startsWith("ssh-"))
            Common.writeToFile(keyPublic, lines.mkString("\n") + "\n")
            keyPublic.setReadable(true, false)
          } catch {
            case e =>
              log.error(e.getMessage, e)
          } finally {
            if (bufferedOutput != null)
              bufferedOutput.close
          }
        }
        p.waitFor()
        val retcode = p.exitValue()
        val result = if (retcode != 0) {
          var error = err.readLine()
          while (error != null) {
            log.error(dropbearkey + " error: " + error)
            IAmYell("dropbearkey: " + error)
            error = err.readLine()
          }
          false
        } else
          true
        if (result)
          keyPublic.setReadable(true, false)
        result
      } else {
        IAmYell("private key " + keyDropbear.getAbsolutePath() + " not found")
        false
      }
    }) getOrElse false
  } catch {
    case e =>
      log.error(e.getMessage, e)
      false
  }
  @Loggable
  private def writeOpenSSHKey(context: Context, user: UserInfo): Boolean = try {
    (for {
      keyDropbear <- getDropbearKeyFile(context, user)
      keyOpenSSH <- getOpenSSHKeyFile(context, user)
      path <- AppControl.Inner.getInternalDirectory(DTimeout.long)
    } yield {
      IAmMumble("write OpenSSH key to " + keyOpenSSH.getAbsolutePath())
      if (keyDropbear.exists) {
        val dropbearconvert = new File(path, "dropbearconvert").getAbsolutePath()
        if (keyOpenSSH.exists())
          keyOpenSSH.delete()
        if (!keyOpenSSH.getParentFile.exists)
          keyOpenSSH.getParentFile.mkdirs
        val p = Runtime.getRuntime().exec(Array(dropbearconvert, "dropbear", "openssh",
          keyDropbear.getAbsolutePath(), keyOpenSSH.getAbsolutePath()))
        val err = new BufferedReader(new InputStreamReader(p.getErrorStream()))
        p.waitFor()
        val retcode = p.exitValue()
        val result = if (retcode != 0) {
          var error = err.readLine()
          while (error != null) {
            log.error(dropbearconvert + " error: " + error)
            IAmYell("dropbearconvert: " + error)
            error = err.readLine()
          }
          false
        } else
          true
        if (result)
          keyOpenSSH.setReadable(true, false)
        result
      } else {
        IAmYell("private key " + keyDropbear.getAbsolutePath() + " not found")
        false
      }
    }) getOrElse false
  } catch {
    case e =>
      log.error(e.getMessage, e)
      false
  }
}
