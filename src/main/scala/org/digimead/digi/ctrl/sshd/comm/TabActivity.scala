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

package org.digimead.digi.ctrl.sshd.comm

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.sshd.R
import com.commonsware.cwac.merge.MergeAdapter
import android.app.ListActivity
import android.os.Bundle
import android.view.View
import android.widget.ListView
import org.digimead.digi.ctrl.lib.log.Logging

class TabActivity extends ListActivity with Logging {
  private lazy val adapter = new MergeAdapter()
  private lazy val lv = getListView()
  private lazy val optionUI = new OptionUI(this)
  private lazy val sessionUI = new SessionUI(this)
  @Loggable
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.service)
    optionUI appendTo adapter
    sessionUI appendTo adapter
    setListAdapter(adapter)
    getListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE)
  }
  @Loggable
  override def onResume() {
    sessionUI.onResume()
    super.onResume()
  }
  @Loggable
  override def onPause() {
    sessionUI.onPause()
    super.onPause()
  }
  @Loggable
  override protected def onListItemClick(l: ListView, v: View, position: Int, id: Long) = {
    adapter.getItem(position) match {
      case item: OptionUI.Item =>
        optionUI.onListItemClick(item)
      case item =>
        log.g_a_s_e("!!! click " + item)
      //        uiSessions.onListItemClick(item)
      //      case _ =>
    }
  }
}
