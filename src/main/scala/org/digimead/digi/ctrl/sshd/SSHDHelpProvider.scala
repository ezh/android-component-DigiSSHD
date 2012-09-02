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

package org.digimead.digi.ctrl.sshd

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile

import org.digimead.digi.ctrl.lib.androidext.ZipFileProvider
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.util.Common

class SSHDHelpProvider extends ZipFileProvider {
  @Loggable
  protected def getZip(): Option[ZipFile] = {
    var result: Option[ZipFile] = None
    var outputStream: OutputStream = null
    var inputStream: InputStream = null
    val helpDirectory = "help"
    val helpPrefix = "help-"
    Common.getDirectory(getContext(), helpDirectory, false, None, None, None) foreach {
      helpDir =>
        try {
          log.debug("check for help resources from DigiControl")
          val latestHelpName: Option[String] = None // get
          latestHelpName foreach {
            name =>
              val helpFile = new File(helpDir, name)
              if (helpFile.exists() && helpFile.length() != 0) {
                log.debug("get latest help from DigiControl \"" + name + "\"")
              } else {
                log.debug("update latest help from DigiControl to \"" + name + "\"")
                // todo
                log.debug("get latest help from DigiControl \"" + name + "\"")
              }
          }
        } catch {
          case e =>
            log.error(e.getMessage(), e)
        } finally {
          if (inputStream != null) {
            inputStream.close()
            inputStream = null
          }
          if (outputStream != null) {
            outputStream.close()
            outputStream = null
          }
        }
        try {
          log.debug("check for help resources from assets")
          getContext().getAssets().list(helpDirectory).filter(_.startsWith(helpPrefix)).foreach {
            helpFileName =>
              val helpFile = new File(helpDir, helpFileName)
              if (!helpFile.exists()) {
                log.debug("write help " + helpFileName + " from assets to " + helpFile.getAbsolutePath())
                outputStream = new BufferedOutputStream(new FileOutputStream(helpFile))
                inputStream = getContext().getAssets().open(helpDirectory + "/" + helpFileName)
                Common.writeToStream(inputStream, outputStream)
              }
          }
        } catch {
          case e =>
            log.error(e.getMessage(), e)
        } finally {
          if (inputStream != null) {
            inputStream.close()
            inputStream = null
          }
          if (outputStream != null) {
            outputStream.close()
            outputStream = null
          }
        }
        // search for result
        helpDir.listFiles().filter(_.getName().startsWith(helpPrefix)).sortBy(_.getName).lastOption.foreach {
          bestHelpFile =>
            result = Some(new ZipFile(bestHelpFile))
        }
    }
    result
  }
}
