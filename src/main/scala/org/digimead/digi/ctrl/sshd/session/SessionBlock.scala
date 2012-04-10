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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DPermission
import org.digimead.digi.ctrl.lib.declaration.DProvider
import org.digimead.digi.ctrl.sshd.R
import scala.actors.Futures.future
import scala.ref.WeakReference
import com.commonsware.cwac.merge.MergeAdapter
import android.app.Activity
import org.digimead.digi.ctrl.sshd.SSHDActivity
import scala.concurrent.SyncVar
import android.text.Html
import org.digimead.digi.ctrl.lib.util.Android
import android.widget.LinearLayout
import android.view.MotionEvent

class SessionBlock(context: Activity) extends Logging {
  implicit def weakActivity2Activity(a: WeakReference[Activity]): Activity = a.get.get
  private val header = context.getLayoutInflater.inflate(R.layout.session_header, null).asInstanceOf[LinearLayout]
  private val adapter = {
    val result: SyncVar[SessionAdapter] = new SyncVar
    context.runOnUiThread(new Runnable { def run = result.set(new SessionAdapter(context, R.layout.session_item)) })
    result.get
  }
  private lazy val inflater = LayoutInflater.from(context)
  private var disconnectButton = new WeakReference[Button](null)
  SessionBlock.block = new WeakReference(this)
  future { updateCursor }

  def appendTo(adapter: MergeAdapter) {
    val headerTitle = header.findViewById(android.R.id.title).asInstanceOf[TextView]
    headerTitle.setText(Html.fromHtml(Android.getString(context, "block_session_title").getOrElse("sessions")))
    adapter.addView(header)
    adapter.addAdapter(this.adapter)
    val footer = inflater.inflate(Android.getId(context, "session_footer", "layout"), null)
    adapter.addView(footer)
    disconnectButton = new WeakReference(footer.findViewById(Android.getId(context, "session_footer_disconnect_all")).asInstanceOf[Button])
    disconnectButton.get.foreach(_.setOnTouchListener(new View.OnTouchListener {
      def onTouch(v: View, event: MotionEvent): Boolean = {
        if (event.getAction() == MotionEvent.ACTION_DOWN)
          future {
            log.g_a_s_e("disconnect")
            //            adapter.item.values.toSeq.foreach(i =>
            //              AppService.Inner ! AppService.Message.Disconnect(i.componentPackage, i.processID, i.connectionID))
          }
        false
      }
    }))
    if (this.adapter.isEmpty) {
      header.findViewById(android.R.id.content).setVisibility(View.VISIBLE)
      disconnectButton.get.foreach(_.setEnabled(false))
    }
  }
  @Loggable
  def onApply(view: View) = future {}
  @Loggable
  def onCancel(view: View) = future {}
  @Loggable
  private def updateCursor() = synchronized {
    log.debug("recreate session cursor")
    val cursor = context.getContentResolver().query(Uri.parse(DProvider.Uri.Session.toString), null, null, null, null)
    context.runOnUiThread(new Runnable() {
      def run() {
        if (cursor.getCount == 0) {
          header.findViewById(android.R.id.content).setVisibility(View.VISIBLE)
          disconnectButton.get.foreach(_.setEnabled(false))
        } else {
          header.findViewById(android.R.id.content).setVisibility(View.GONE)
          disconnectButton.get.foreach(_.setEnabled(true))
        }
        adapter.changeCursor(cursor)
        adapter.notifyDataSetChanged
      }
    })
  }
}

object SessionBlock extends Logging {
  @volatile protected var block = new WeakReference[SessionBlock](null)

  @Loggable
  def updateCursor =
    synchronized { block.get.foreach(_.updateCursor()) }
}
