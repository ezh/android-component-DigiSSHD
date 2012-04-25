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

import scala.actors.Futures.future

import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.log.RichLogger
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.lib.util.Android

import android.app.Activity
import android.widget.Toast

object SSHDCommon {
  def optionChangedOnRestartNotify(context: Activity, option: DOption.OptVal, state: String)(implicit logger: RichLogger, dispatcher: Dispatcher) {
    if (AppComponent.Inner.state.get.code == DState.Passive) {
      val message = Android.getString(context, "option_changed").getOrElse("%1$s set to %2$s").format(option.name(context), state)
      IAmMumble(message)(logger, dispatcher)
      future {
        Thread.sleep(DTimeout.shortest)
        context.runOnUiThread(new Runnable {
          def run =
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        })
      }
    } else {
      val message = Android.getString(context, "option_changed_on_restart").getOrElse("%1$s set to %2$s, it will be applied on the next run").format(option.name(context), state)
      future {
        Thread.sleep(DTimeout.shortest)
        context.runOnUiThread(new Runnable {
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
