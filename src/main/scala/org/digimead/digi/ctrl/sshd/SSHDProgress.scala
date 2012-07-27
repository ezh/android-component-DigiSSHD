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

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import scala.actors.Actor
import scala.actors.Futures

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.IAmBusy
import org.digimead.digi.ctrl.lib.message.IAmReady
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.SyncVar

import android.app.ProgressDialog
import android.content.DialogInterface

class SSHDProgress {

}

object SSHDProgress extends Logging {

  @Loggable
  private def onBusy(activity: SSHDActivity): Unit = if (!busyDialog.isSet) {
/*    AppComponent.Inner.showDialogSafeWait[ProgressDialog](activity, "progress_dialog", () =>
      if (busyCounter.get > 0) {
        busyBuffer.lastOption.foreach(msg => busyBuffer = Seq(msg))
        val dialog = new ProgressDialog(activity)
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        dialog.setTitle("Please wait...")
        dialog.setOnShowListener(new DialogInterface.OnShowListener {
          def onShow(dialog: DialogInterface) = Futures.future {
            // additional guard
            Thread.sleep(DTimeout.shortest)
            if (busyCounter.get <= 0) try {
              dialog.dismiss
            } catch {
              case e =>
                log.warn(e.getMessage, e)
            }
          }
        })
        dialog.setMessage(busyBuffer.mkString("\n"))
        dialog.setCancelable(false)
        AppComponent.Inner.disableRotation()
        dialog.show
        log.debug("display progress dialog")
        dialog
      } else
        null, () => {
      log.debug("hide progress dialog")
      busyDialog.unset()
      busyCounter.set(0)
      AppComponent.Inner.enableRotation()
    }).map(dialog => busyDialog.set(dialog))*/
  }
  @Loggable
  private def onReady(activity: SSHDActivity): Unit = Futures.future {
    Thread.sleep(500)
    busyDialog.get(0).foreach(dialog =>
      if (!showDialog.get)
        dialog.dismiss)
  }
  def onUpdate(activity: SSHDActivity): Unit = busyDialog.get(0).foreach {
    dialog =>
      activity.runOnUiThread(new Runnable {
        def run = dialog.setMessage(busyBuffer.mkString("\n"))
      })
  }

  lazy val NewConnection = AppComponent.Context.map(a => Android.getId(a, "new_connection")).getOrElse(0)
  lazy val ComponentInfo = AppComponent.Context.map(a => Android.getId(a, "component_info")).getOrElse(0)
  private[sshd] val busyDialog = new SyncVar[ProgressDialog]()
  private[sshd] val busyCounter = new AtomicInteger()
  private[sshd] val busySize = 5
  private val showDialog = new AtomicBoolean(false)
  @volatile private[sshd] var busyBuffer = Seq[String]()

  private[sshd] val queue = new Actor {
    def act = {
      loop {
        react {
          case message: IAmBusy =>
            showDialog.set(true)
            SSHDActivity.activity.foreach(onBusy)
          case message: IAmReady =>
            showDialog.set(false)
            SSHDActivity.activity.foreach(onReady)
        }
      }
    }
  }
}