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

package org.digimead.digi.ctrl.sshd.session

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.declaration.DConnection
import org.digimead.digi.ctrl.lib.declaration.DProvider
import org.digimead.digi.ctrl.lib.util.Common
import scala.actors.Actor
import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.WeakHashMap

class SessionAdapter(context: Context, layout: Int)
  extends SimpleCursorAdapter(context, layout, null, Array[String](), Array[Int]()) with Logging {
  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    convertView match {
      case null =>
        val view = super.getView(position, convertView, parent)
        val cursor = getCursor()
        if (!cursor.moveToPosition(position))
          throw new IllegalStateException("couldn't move cursor to position " + position)
        val componentPackage = cursor.getString(DProvider.Field.ComponentPackage.id)
        val executableID = cursor.getInt(DProvider.Field.ExecutableID.id)
        Option(cursor.getBlob(DProvider.Field.Connection.id)).foreach {
          connection =>
            Common.deserializeFromArray(connection) match {
              case Some(connection: DConnection) =>
                log.g_a_s_e("!" + connection)
              /*                Component.get(componentPackage) match {
                  case Some(component) =>
                    val title = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
                    val description = view.findViewById(android.R.id.text2).asInstanceOf[TextView]
                    val subinfo = view.findViewById(android.R.id.message).asInstanceOf[TextView]
                    val kind = view.findViewById(android.R.id.icon1).asInstanceOf[ImageView]
                    val state = view.findViewById(android.R.id.button1).asInstanceOf[ImageButton]
                    val ip = InetAddress.getByAddress(BigInt(connection.remoteIP).toByteArray)
                    component.icon(context).foreach(kind.setBackgroundDrawable)
                    title.setText(context.getString(R.string.session_title).
                      format(ip.getHostAddress, component.executable(executableID).name))
                    description.setText(context.getString(R.string.session_description).
                      format(component.name))
                    state.setFocusable(false)
                    state.setFocusableInTouchMode(false)
                    Adapter.durationField(subinfo) = connection.timestamp
                    Adapter.updateDurationText(subinfo)
                  case None =>
                    log.warn("couldn't aquire component " + componentPackage + " for cursor at position " + position)
                }*/
              case _ =>
                log.warn("couldn't deserialize connection from cursor at position " + position)
            }
        }
        //        log.trace("recreate view for child " + cursor)
        view
      case view: View =>
        super.getView(position, convertView, parent)
    }
  }
}

object SessionAdapter extends Actor with Logging {
  private val jscheduler = Executors.newSingleThreadScheduledExecutor()
  private val durationField = new WeakHashMap[TextView, Long] with SynchronizedMap[TextView, Long]
  start
  schedule(1000)
  log.debug("alive")

  private def schedule(duration: Int) {
    jscheduler.scheduleAtFixedRate(new Runnable { def run { SessionAdapter.this ! () } }, 0, duration, TimeUnit.MILLISECONDS)
  }
  def act = {
    loop {
      react {
        case _ =>
          durationField.keys.foreach(updateDurationText)
      }
    }
  }
  private def updateDurationText(field: TextView) =
    if (field.getVisibility == View.VISIBLE)
      field.getRootView.post(new Runnable() {
        def run() = field.setText("D: " + (System.currentTimeMillis - durationField(field)))
      })
}
