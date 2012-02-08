/*
 * DigiSSHD - DigiINETD component for Android Platform
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

package org.digimead.digi.inetd.sshd.comm

import org.digimead.digi.inetd.lib.aop.Loggable
import org.digimead.digi.inetd.sshd.R

import com.commonsware.cwac.merge.MergeAdapter

import android.app.ListActivity
import android.os.Bundle
import android.view.View
import android.widget.ListView

class TabActivity extends ListActivity {
  private[comm] val adapter = new MergeAdapter()
  private[comm] lazy val lv = getListView()
  private var uiOptions: options.UI = null
  private var uiSessions: sessions.UI = null
  @Loggable
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.service)
    uiOptions = new options.UI(this)
    uiSessions = new sessions.UI(this)
    setListAdapter(adapter)
    getListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE)
  }
  @Loggable
  override protected def onListItemClick(l: ListView, v: View, position: Int, id: Long) = {
    adapter.getItem(position) match {
      case item: options.UI.Item =>
        uiOptions.onListItemClick(item)
      case item: sessions.UI.Item =>
        uiSessions.onListItemClick(item)
      case _ =>
    }
  }
}
