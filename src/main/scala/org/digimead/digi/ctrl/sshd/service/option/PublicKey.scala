/**
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

package org.digimead.digi.ctrl.sshd.service.option

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

import scala.Array.canBuildFrom

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.base.AppControl
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.IAmBusy
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.lib.message.IAmReady
import org.digimead.digi.ctrl.lib.message.IAmWarn
import org.digimead.digi.ctrl.lib.message.Origin.anyRefToOrigin
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.util.SyncVar
import org.digimead.digi.ctrl.sshd.Message.dispatcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

trait PublicKey extends Logging {
  val option: DOption.OptVal

  def generateHostKey(context: Context): Boolean = option match {
    case RSAPublicKeyEncription.option =>
      AppComponent.Inner.enginePath.map {
        enginePath =>
          val rsa_key = new File(enginePath, "dropbear_rsa_host_key")
          log.debug("private key path: " + rsa_key.getAbsolutePath())
          if (generateKey(context, rsa_key)) {
            true
          } else {
            IAmWarn("RSA key generation failed")
            false
          }
      } getOrElse false
    case DSAPublicKeyEncription.option =>
      AppComponent.Inner.enginePath.map {
        enginePath =>
          val dss_key = new File(enginePath, "dropbear_dss_host_key")
          log.debug("private key path: " + dss_key.getAbsolutePath())
          if (generateKey(context, dss_key)) {
            true
          } else {
            IAmWarn("DSA key generation failed")
            false
          }
      } getOrElse false
  }
  def importHostKey(activity: Activity) = {
    /*AppComponent.Inner.showDialogSafe[Dialog](activity, "service_import_hostkey_dialog", () => {
      val kindOpt = if (option.tag == "dsa") "dss" else option.tag
      val importTemplateName = "dropbear_" + kindOpt + "_host_key"
      val filter = new FileFilter { override def accept(file: File) = file.isDirectory || file.getName == importTemplateName }
      val userHomeFile = new File("/")
      val dialog = FileChooser.createDialog(activity,
        XResource.getString(activity, "dialog_import_key").getOrElse("Import \"" + importTemplateName + "\""),
        userHomeFile,
        importHostKeyOnResult,
        filter,
        (context, f) => f.getName == importTemplateName)
      dialog.show()
      dialog
    })*/
  }
  private def importHostKeyOnResult(context: Context, path: File, files: Seq[File], stash: AnyRef) {
    val kindOpt = if (option.tag == "dsa") "dss" else option.tag
    (for {
      importFileFrom <- files.headOption
      enginePath <- AppComponent.Inner.enginePath
    } yield {
      IAmWarn("import " + kindOpt.toUpperCase + " from " + importFileFrom)
      val importTemplateName = "dropbear_" + kindOpt + "_host_key"
      val importFileTo = new File(enginePath, importTemplateName)
      if (Common.copyFile(importFileFrom, importFileTo))
        Toast.makeText(context, XResource.getString(context, "import_public_key_successful").
          getOrElse("import %s key succesful").format(option.tag.toUpperCase), Toast.LENGTH_SHORT).show
      else
        Toast.makeText(context, XResource.getString(context, "import_public_key_failed").
          getOrElse("import %s key failed").format(option.tag.toUpperCase), Toast.LENGTH_LONG).show
    }) getOrElse {
      AppComponent.Context.foreach(context => Toast.makeText(context, XResource.getString(context, "import_public_key_canceled").
        getOrElse("import %s key canceled").format(option.tag.toUpperCase), Toast.LENGTH_LONG).show)
    }
  }
  def exportHostKey(context: Context): Boolean = try {
    val file = option match {
      case RSAPublicKeyEncription.option =>
        AppComponent.Inner.enginePath.flatMap(p => Some(new File(p, "dropbear_rsa_host_key")))
      case DSAPublicKeyEncription.option =>
        AppComponent.Inner.enginePath.flatMap(p => Some(new File(p, "dropbear_dss_host_key")))
    }
    file match {
      case Some(file) if file.exists =>
        IAmMumble("export " + option.tag.toUpperCase + " key")
        val intent = new Intent()
        intent.setAction(Intent.ACTION_SEND)
        intent.setType("application/octet-stream")
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
        context.startActivity(Intent.createChooser(intent, XResource.getString(context, "export_host_key").
          getOrElse("Export %s key").format(option.tag.toUpperCase)))
      case _ =>
        val message = "unable to export unexists " + option.tag.toUpperCase + " key"
        AnyBase.runOnUiThread { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
        IAmWarn(message)
    }
    false
  } catch {
    case e =>
      val message = "unable to export " + option.tag.toUpperCase + " key"
      AnyBase.runOnUiThread { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
      IAmWarn(message)
      log.error(e.getMessage(), e)
      false
  }
  protected def getSourceKeyFile(): Option[File] = AppComponent.Inner.enginePath flatMap {
    enginePath =>
      val kindOpt = if (option.tag == "dsa") "dss" else option.tag
      Some(new File(enginePath, "dropbear_" + kindOpt + "_host_key"))
  }
  private def generateKey(context: Context, keyFile: File): Boolean = try {
    IAmBusy(this, option.tag.toUpperCase + " key generation")
    val internalPath = new SyncVar[File]()
    val externalPath = new SyncVar[File]()
    AppControl.Inner.getInternalDirectory(DTimeout.long) match {
      case Some(path) =>
        if (keyFile.exists())
          keyFile.delete()
        val dropbearkey = new File(path, "dropbearkey").getAbsolutePath()
        log.debug("generate " + option.tag + " key")
        val result = {
          val kindOpt = if (option.tag == "dsa") "dss" else option.tag
          val p = Runtime.getRuntime().exec(dropbearkey +: Array("-t", kindOpt, "-f", keyFile.getAbsolutePath()))
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
          keyFile.setReadable(true, false)
        result
      case _ =>
        false
    }
  } catch {
    case e =>
      log.error(e.getMessage(), e)
      false
  } finally {
    IAmReady(this, option.tag.toUpperCase + " key generated")
  }
}
