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

import scala.actors.Futures
import scala.actors.Futures.future
import scala.collection.mutable.Subscriber

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.base.AppComponentEvent
import org.digimead.digi.ctrl.lib.base.AppControl
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.log.RichLogger
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.lib.message.IAmWarn
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.Message.dispatcher

import android.app.Activity
import android.content.Context
import android.widget.Toast

object SSHDCommon extends Logging {
  // AppComponent global state subscriber
  val globalStateSubscriber = new Subscriber[AppComponentEvent, AppComponent.type#Pub] {
    def notify(pub: AppComponent.type#Pub, event: AppComponentEvent) =
      if (AppControl.Inner != null && AppControl.Inner.isAvailable == Some(true)) {
        event match {
          case AppComponent.Event.Resume =>
            IAmWarn("DigiSSHD resume")
            // leave UI thread
            Futures.future { Option(AppControl.Inner).foreach(_.callUpdateShutdownTimer(getClass.getPackage.getName, -1)) }
          case AppComponent.Event.Suspend(timeout) =>
            IAmWarn("DigiSSHD suspend, shutdown timer is " + timeout + "s")
            // leave UI thread
            Futures.future {
              var remain = timeout
              val step = 5000
              while ((AppComponent.isSuspend || AppControl.isSuspend) && remain > 0) {
                Option(AppControl.Inner).foreach(_.callUpdateShutdownTimer(getClass.getPackage.getName, remain))
                Thread.sleep(step)
                remain -= step
              }
            }
          case AppComponent.Event.Shutdown =>
            IAmWarn("DigiSSHD shutdown")
            // leave UI thread
            Futures.future { Option(AppControl.Inner).foreach(_.callUpdateShutdownTimer(getClass.getPackage.getName, 0)) }
          case _ =>
        }
      } else
        log.debug("skip event " + event + ", ICtrlHost not binded")
  }
  AppComponent.subscribe(globalStateSubscriber)
  log.debug("alive")

  def optionChangedOnRestartNotify(context: Context, option: DOption.OptVal, state: String)(implicit logger: RichLogger, dispatcher: Dispatcher) {
    if (AppComponent.Inner.state.get.value == DState.Passive) {
      val message = Android.getString(context, "option_changed").getOrElse("%1$s set to %2$s").format(option.name(context), state)
      IAmMumble(message)(logger, dispatcher)
      future {
        Thread.sleep(DTimeout.shortest)
        AnyBase.handler.post(new Runnable {
          def run =
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        })
      }
    } else {
      val message = Android.getString(context, "option_changed_on_restart").getOrElse("%1$s set to %2$s, it will be applied on the next run").format(option.name(context), state)
      future {
        Thread.sleep(DTimeout.shortest)
        AnyBase.handler.post(new Runnable {
          def run =
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        })
      }
    }
  }
  def optionChangedNotify(context: Activity, option: DOption.OptVal, state: String)(implicit logger: RichLogger, dispatcher: Dispatcher) {
    val message = Android.getString(context, "option_changed").getOrElse("%1$s set to %2$s").format(option.name(context), state)
    IAmMumble(message)(logger, dispatcher)
    future {
      Thread.sleep(DTimeout.shortest)
      context.runOnUiThread(new Runnable {
        def run =
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
      })
    }
  }
}
