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

import java.util.concurrent.ConcurrentLinkedQueue

import scala.Array.canBuildFrom
import scala.annotation.tailrec

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
import android.os.Parcelable

object Message extends Logging {
  private val flushLimit = 1000
  private val queue = new ConcurrentLinkedQueue[DMessage]
  @volatile private var runner = new Thread

  def addLazyInit = AppComponent.LazyInit("MessageDispatcher initialize onCreate", 50) {
    Message.synchronized {
      if (!runner.isAlive) {
        runner = new Thread("MessageDispatcher for " + Message.getClass.getName) {
          log.debug("new MessageDispatcher thread %s alive".format(this.getId.toString))
          this.setDaemon(true)
          @tailrec
          override def run() = {
            if (!queue.isEmpty) {
              flushQueue(flushLimit)
              Thread.sleep(100)
            } else
              Thread.sleep(500)
            if (runner.getId != this.getId || AppComponent.Inner == null)
              log.debug("MessageDispatcher thread %s terminated".format(this.getId.toString))
            else
              run
          }
        }
        runner.start
      }
    }
  }

  implicit val dispatcher: Dispatcher = new Dispatcher {
    def process(message: DMessage): Unit = {
      queue.offer(message)
      
/*        if (message.isInstanceOf[IAmBusy]) {
          log.debug("send request IAmBusy")
          SSHDActivity.actor !? (DTimeout.normal, message) orElse ({ log.fatal("request IAmBusy hang with timeout " + DTimeout.normal); None })
        } else
          SSHDActivity.actor ! message*/
    }
  }

  private def flushQueue(): Int = flushQueue(java.lang.Integer.MAX_VALUE)
  private def flushQueue(n: Int): Int = synchronized {
    var count = 0
    var messages: Array[DMessage] = Array()
    // acquire
    while (count < n && !queue.isEmpty()) {
      val message = queue.poll().asInstanceOf[DMessage]
      messages = messages :+ message
      count += 1;
    }
    AppComponent.Context foreach {
      context =>
        // push in history
        try {
          val values = new ContentValues()
          values.put(DIntent.DroneName, Android.getString(context, "app_name").getOrElse("DigiSSHD"))
          values.put(DIntent.DronePackage, context.getPackageName)
          /*
           * [error] both method put in class ContentValues of type (x$1: java.lang.String,x$2: java.lang.Double)Unit
           * [error] and  method put in class ContentValues of type (x$1: java.lang.String,x$2: java.lang.Float)Unit
           * [error] match argument types (java.lang.String,Long)
           * [error]     values.put(DHistoryProvider.Field.ActivityTS.toString, message.
           * TODO submit Scala ticket
           */
          messages.foreach {
            message =>
              values.put(DHistoryProvider.Field.ActivityTS.toString, Long.box(message.ts))
              values.put(DHistoryProvider.Field.ActivitySeverity.toString, message.getClass.getName)
              values.put(DHistoryProvider.Field.ActivityMessage.toString, message.message)
              context.getContentResolver.insert(DHistoryProvider.Uri.Activity, values)
          }
        } catch {
          case e =>
            log.warn("Message::Dispatcher getContentResolver.insert " + e.getMessage)
        }
        // send broadcast
        val intent = new Intent(DIntent.Message, Uri.parse("code://" + context.getPackageName))
        intent.putExtra(DIntent.Message, messages.asInstanceOf[Array[Parcelable]])
        intent.putExtra(DIntent.DroneName, Android.getString(context, "app_name").getOrElse("DigiSSHD"))
        intent.putExtra(DIntent.DronePackage, context.getPackageName)
        try {
          AppComponent.Inner.sendPrivateBroadcast(intent)
        } catch {
          case e =>
            log.warn("Message::Dispatcher sendPrivateBroadcast " + e.getMessage)
        }
    }
    count
  }
}
