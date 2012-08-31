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

import scala.actors.Actor
import scala.ref.WeakReference
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.IAmBusy
import org.digimead.digi.ctrl.lib.message.IAmReady
import android.view.View
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.AnyBase
import android.widget.TextView
import scala.actors.Futures

object SSHDRunningStatus extends Logging {
  @volatile private var statusBarForSmallLayout = new WeakReference[View](null)
  @volatile private var statusBarForLargeLayout = new WeakReference[View](null)
  @volatile private var running = false
  @volatile private var busyCounter = 0
  lazy val actor = {
    val actor = new Actor {
      def act = {
        loop {
          react {
            case message: IAmReady =>
              busyCounter += 1
              update()
            case message: IAmBusy =>
              if (busyCounter > 0)
                busyCounter -= 1
              update()
            case _ =>
              update()
          }
        }
      }
    }
    actor.start
    actor
  }

  def update() = for {
    activity <- SSHDActivity.activity
    small <- statusBarForSmallLayout.get
    large <- statusBarForLargeLayout.get
  } {
    val text = AppComponent.Inner.state.get match {
      case AppComponent.State(DState.Initializing, rawMessage, callback) =>
        log.debug("set status text to " + DState.Initializing)
        Some(XResource.getCapitalized(activity, "status_initializing").getOrElse("Initializing"))
      case AppComponent.State(DState.Passive, rawMessage, callback) =>
        log.debug("set status text to " + DState.Passive)
        running = false
        // future { onAppPassive }
        Some(XResource.getCapitalized(activity, "status_ready").getOrElse("Ready"))
      case AppComponent.State(DState.Active, rawMessage, callback) =>
        log.debug("set status text to " + DState.Active)
        running = true
        // future { onAppActive }
        Some(XResource.getCapitalized(activity, "status_active").getOrElse("Active"))
        None
      case AppComponent.State(DState.Broken, rawMessage, callback) =>
        log.debug("set status text to " + DState.Broken + " with raw message: " + rawMessage)
        val message = if (rawMessage.length > 1)
          XResource.getString(activity, rawMessage.head, rawMessage.tail: _*).getOrElse(rawMessage.head)
        else if (rawMessage.length == 1)
          XResource.getString(activity, rawMessage.head).getOrElse(rawMessage.head)
        else
          XResource.getString(activity, "unknown").getOrElse("unknown")
        Some(XResource.getCapitalized(activity, "status_error").getOrElse("Error %s").format(message))
      case AppComponent.State(DState.Busy, rawMessage, callback) =>
        log.debug("set status text to " + DState.Busy)
        Some(XResource.getCapitalized(activity, "status_busy").getOrElse("Busy"))
      case AppComponent.State(DState.Unknown, rawMessage, callback) =>
        log.debug("skip notification with state DState.Unknown")
        Some(XResource.getCapitalized(activity, "status_unknown").getOrElse("Unknown"))
      case state =>
        log.fatal("unknown state " + state)
        None
    }
    log.___glance("!!!" + text)
    text.foreach {
      text =>
        AnyBase.runOnUiThread {
          small.asInstanceOf[TextView].setText(text)
          large.asInstanceOf[TextView].setText(text)
        }
    }
  }
  @Loggable
  def init(small: View, large: View) {
    assert(small != null && large != null, "status bar lost")
    statusBarForSmallLayout = new WeakReference(small)
    statusBarForLargeLayout = new WeakReference(large)
    running = false
    busyCounter = 0
    Futures.future { update }
  }
  @Loggable
  def deinit() {
    statusBarForSmallLayout = new WeakReference(null)
    statusBarForLargeLayout = new WeakReference(null)
  }
}
