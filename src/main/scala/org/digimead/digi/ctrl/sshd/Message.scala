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

import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DHistoryProvider
import org.digimead.digi.ctrl.lib.declaration.DHistoryProvider.value2uri
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.DMessage
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.message.IAmBusy
import org.digimead.digi.ctrl.lib.util.Android

import android.content.ContentValues
import android.content.Intent
import android.net.Uri

object Message extends Logging {
  implicit val dispatcher: Dispatcher = new Dispatcher {
    // skip if process called before initialization, look for setLogLevel routine and onCreate sequence
    def process(message: DMessage): Unit = for {
      inner <- Option(AppComponent.Inner)
      context <- AppComponent.Context
    } {
      if (message.logger != null) {
        try {
          val intent = new Intent(DIntent.Message, Uri.parse("code://" + context.getPackageName))
          intent.putExtra(DIntent.Message, message)
          intent.putExtra(DIntent.DroneName, Android.getString(context, "app_name").getOrElse("DigiSSHD"))
          intent.putExtra(DIntent.DronePackage, context.getPackageName)
          AppComponent.Inner.sendPrivateBroadcast(intent)
        } catch {
          case e =>
            log.warn("Message::Dispatcher sendPrivateBroadcast " + e.getMessage)
        }
        // push in history
        try {
          val values = new ContentValues()
          values.put(DIntent.DroneName, Android.getString(context, "app_name").getOrElse("DigiControl"))
          values.put(DIntent.DronePackage, context.getPackageName)
          /*
           * [error] both method put in class ContentValues of type (x$1: java.lang.String,x$2: java.lang.Double)Unit
           * [error] and  method put in class ContentValues of type (x$1: java.lang.String,x$2: java.lang.Float)Unit
           * [error] match argument types (java.lang.String,Long)
           * [error]     values.put(DHistoryProvider.Field.ActivityTS.toString, message.
           * TODO submit Scala ticket
           */
          values.put(DHistoryProvider.Field.ActivityTS.toString, Long.box(message.ts))
          values.put(DHistoryProvider.Field.ActivitySeverity.toString, message.getClass.getName)
          values.put(DHistoryProvider.Field.ActivityMessage.toString, message.message)
          context.getContentResolver.insert(DHistoryProvider.Uri.Activity, values)
        } catch {
          case e =>
            log.warn("Message::Dispatcher getContentResolver.insert " + e.getMessage)
        }
      }
      if (SSHDActivity.isConsistent)
        if (message.isInstanceOf[IAmBusy]) {
          log.debug("send request IAmBusy")
          SSHDActivity !? (DTimeout.normal, message) orElse ({ log.fatal("request IAmBusy hang with timeout " + DTimeout.normal); None })
        } else
          SSHDActivity ! message
    }
  }
}

