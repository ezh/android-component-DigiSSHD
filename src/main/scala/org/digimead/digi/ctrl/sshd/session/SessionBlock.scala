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

class SessionBlock(context: Activity) extends Logging {
  implicit def weakActivity2Activity(a: WeakReference[Activity]): Activity = a.get.get
  private val updateReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = {
      if (intent.getData.getAuthority == DConstant.SessionAuthority) {
        log.debug("receive session update " + intent.toUri(0))
        onIntentUpdate(intent)
      }
    }
  }
  private val header = context.getLayoutInflater.inflate(R.layout.session_header, null).asInstanceOf[LinearLayout]
  private val adapter = {
    val result: SyncVar[SessionAdapter] = new SyncVar
    context.runOnUiThread(new Runnable { def run = result.set(new SessionAdapter(context, R.layout.session_item)) })
    result.get
  }
  private lazy val inflater = LayoutInflater.from(context)
  private var applyButton = new WeakReference[Button](null)
  private var cancelButton = new WeakReference[Button](null)
  def appendTo(adapter: MergeAdapter) {
    val headerTitle = header.findViewById(android.R.id.title).asInstanceOf[TextView]
    headerTitle.setText(Html.fromHtml(Android.getString(context, "block_session_title").getOrElse("sessions")))
    if (this.adapter.isEmpty)
      header.findViewById(android.R.id.content).setVisibility(View.VISIBLE)
    adapter.addView(header)
    adapter.addAdapter(this.adapter)
  }
  @Loggable
  def onResume() {
    val updateFilter = new IntentFilter()
    updateFilter.addAction(DIntent.Update)
    updateFilter.addDataScheme("code")
    context.registerReceiver(updateReceiver, updateFilter, DPermission.Base, null)
    future { updateCursor }
  }
  @Loggable
  def onPause() {
    context.unregisterReceiver(updateReceiver)
  }
  @Loggable
  private def onIntentUpdate(intent: Intent): Unit = future { updateCursor }
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
        adapter.changeCursor(cursor)
        adapter.notifyDataSetChanged
      }
    })
  }
}

/*

class TabActivity extends ListActivity with Logging {


  log.debug("alive")
  @Loggable
  override protected def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.session)
    val lv = findViewById(android.R.id.list).asInstanceOf[ListView]
    val header = inflater.inflate(R.layout.separator, null).asInstanceOf[TextView]
    header.setText(R.string.session_list)
    lv.addHeaderView(header)
    val footer = inflater.inflate(R.layout.session_footer, null)
    applyButton = new WeakReference(footer.findViewById(R.id.session_footer_apply).asInstanceOf[Button])
    applyButton.get.foreach(_.setOnTouchListener(new View.OnTouchListener {
      def onTouch(v: View, event: MotionEvent): Boolean = {
        if (event.getAction() == MotionEvent.ACTION_DOWN)
          TabActivity.this.onApply(v)
        false
      }
    }))
    cancelButton = new WeakReference(footer.findViewById(R.id.session_footer_cancel).asInstanceOf[Button])
    cancelButton.get.foreach(_.setOnTouchListener(new View.OnTouchListener {
      def onTouch(v: View, event: MotionEvent): Boolean = {
        if (event.getAction() == MotionEvent.ACTION_DOWN)
          TabActivity.this.onCancel(v)
        false
      }
    }))
    lv.addFooterView(footer)
    lv.setAdapter(adapter)
    future { updateCursor }
  }
}

*/

