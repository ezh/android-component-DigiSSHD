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

package org.digimead.digi.ctrl.sshd.service

import scala.collection.JavaConversions._

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.R

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.ListActivity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ListView

class FilterRemoveActivity extends ListActivity with Logging {
  // lazy for workaround of System services not available to Activities before onCreate()
  private lazy val adapter = {
    val pref = getSharedPreferences(DPreference.FilterInterface, Context.MODE_PRIVATE)
    val values = pref.getAll().toSeq.map(t => t._1).filter(_ != FilterBlock.ALL).sorted.map(FilterRemoveActivity.FilterItem(_, false))
    new FilterRemoveAdapter(this, values)
  }
  private lazy val inflater = getLayoutInflater()
  private lazy val footerApply = findViewById(R.id.service_filter_footer_apply).asInstanceOf[Button]
  log.debug("alive")
  /** Called when the activity is first created. */
  @Loggable
  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    AnyBase.init(this, false)
    AnyBase.preventShutdown(this)
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_BEHIND)
    setContentView(R.layout.service_filter)
    val footer = inflater.inflate(R.layout.service_filter_footer, null)
    getListView().addFooterView(footer, null, false)
    setListAdapter(adapter)
  }
  @Loggable
  override def onDestroy() {
    AnyBase.deinit(this)
    super.onDestroy()
  }
  @Loggable
  override protected def onListItemClick(l: ListView, v: View, position: Int, id: Long) = {
    runOnUiThread(new Runnable {
      def run = {
        adapter.itemClick(position)
        if (adapter.getPending.isEmpty)
          footerApply.setEnabled(false)
        else
          footerApply.setEnabled(true)
      }
    })
  }
  @Loggable
  def applySelectedFilters(v: View) {
    showDialog(0) // apply?
  }
  @Loggable
  override protected def onCreateDialog(id: Int): Dialog = {
    // there is only one - apply
    new AlertDialog.Builder(this).
      setTitle("Apply").
      setMessage("R u sure?").
      setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, whichButton: Int) {
          FilterRemoveActivity.this.onSubmit()
          setResult(Activity.RESULT_OK, new Intent())
          finish()
        }
      }).
      setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, whichButton: Int) {
        }
      }).
      create()
  }
  @Loggable
  private def onSubmit() = {
    val pref = getSharedPreferences(DPreference.FilterInterface, Context.MODE_PRIVATE)
    val editor = pref.edit()
    adapter.getPending.foreach(filter => editor.remove(filter.value))
    editor.commit()
  }
}

object FilterRemoveActivity {
  case class FilterItem(val value: String, var pending: Boolean)
}
