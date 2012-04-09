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

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.sshd.R
import com.commonsware.cwac.merge.MergeAdapter
import android.app.ListActivity
import android.os.Bundle
import android.view.View
import android.widget.ListView
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.util.Android
import android.widget.TextView
import android.text.Html
import org.digimead.digi.ctrl.sshd.SSHDActivity
import scala.ref.WeakReference

class TabActivity extends ListActivity with Logging {
  private lazy val lv = getListView()
  @Loggable
  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.session)
    TabActivity.activity = Some(this)

    // prepare empty view
    // options
    val optionsHeader = findViewById(Android.getId(this, "nodata_header_option")).asInstanceOf[TextView]
    optionsHeader.setText(Html.fromHtml(Android.getString(this, "block_option_title").getOrElse("options")))
    // sessions
    val sessionsHeader = findViewById(Android.getId(this, "nodata_header_session")).asInstanceOf[TextView]
    sessionsHeader.setText(Html.fromHtml(Android.getString(this, "block_session_title").getOrElse("sessions")))
    // prepare active view
    getListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE)
    TabActivity.adapter.foreach(adapter => runOnUiThread(new Runnable { def run = setListAdapter(adapter) }))
  }
  @Loggable
  override protected def onListItemClick(l: ListView, v: View, position: Int, id: Long) = for {
    adapter <- TabActivity.adapter
    optionBlock <- TabActivity.optionBlock
    sessionBlock <- TabActivity.sessionBlock
  } {
    adapter.getItem(position) match {
      case item: OptionBlock.Item =>
        optionBlock.onListItemClick(item)
      case item =>
        log.g_a_s_e("!!! click " + item)
      //        uiSessions.onListItemClick(item)
      //      case _ =>
    }
  }
}

object TabActivity extends Logging {
  @volatile private var activity: Option[TabActivity] = None
  @volatile private var adapter: Option[MergeAdapter] = None
  @volatile private var optionBlock: Option[OptionBlock] = None
  @volatile private var sessionBlock: Option[SessionBlock] = None
  def addLazyInit = AppActivity.LazyInit("initialize session adapter") {
    SSHDActivity.activity match {
      case Some(activity) =>
        adapter = Some(new MergeAdapter())
        optionBlock = Some(new OptionBlock(activity))
        sessionBlock = Some(new SessionBlock(activity))
        for {
          adapter <- adapter
          optionBlock <- optionBlock
          sessionBlock <- sessionBlock
        } {
          optionBlock appendTo (adapter)
          sessionBlock appendTo (adapter)
          TabActivity.activity.foreach(ctx => ctx.runOnUiThread(new Runnable { def run = ctx.setListAdapter(adapter) }))
        }
      case None =>
        log.fatal("lost SSHDActivity context")
    }
  }
}
