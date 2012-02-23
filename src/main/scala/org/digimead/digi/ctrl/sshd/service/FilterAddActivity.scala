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

package org.digimead.digi.ctrl.sshd.service

import scala.collection.JavaConversions._
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.AppActivity
import org.digimead.digi.ctrl.lib.Common
import org.digimead.digi.ctrl.sshd.R
import org.slf4j.LoggerFactory
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.ListActivity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.ContextMenu.ContextMenuInfo
import android.view.ContextMenu
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import org.digimead.digi.ctrl.lib.aop.Logging

class FilterAddActivity extends ListActivity with Logging {
  // lazy for workaround of System services not available to Activities before onCreate()
  private lazy val adapter = AppActivity.Inner.map(inner =>
    new FilterAddAdapter(this, () => {
      val alreadyInUse = getSharedPreferences(Common.Preference.filter, Context.MODE_PRIVATE).getAll().map(t => t._1).toSeq
      predefinedFilters().diff(alreadyInUse) // drop "already in use" values
    }))
  private lazy val inflater = getLayoutInflater()
  private lazy val headerInterface = findViewById(R.id.service_filter_header_interface).asInstanceOf[TextView]
  private lazy val headerIP1 = findViewById(R.id.service_filter_header_ip1).asInstanceOf[TextView]
  private lazy val headerIP2 = findViewById(R.id.service_filter_header_ip2).asInstanceOf[TextView]
  private lazy val headerIP3 = findViewById(R.id.service_filter_header_ip3).asInstanceOf[TextView]
  private lazy val headerIP4 = findViewById(R.id.service_filter_header_ip4).asInstanceOf[TextView]
  private lazy val footerApply = findViewById(R.id.service_filter_footer_apply).asInstanceOf[Button]
  private val receiver = new BroadcastReceiver() {
    @Loggable
    def onReceive(context: Context, intent: Intent) = adapter foreach {
      adapter =>
        runOnUiThread(new Runnable { def run = adapter.notifyDataSetChanged(true) })
    }
  }
  log.debug("alive")
  /** Called when the activity is first created. */
  @Loggable
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.service_filter)
    val header = inflater.inflate(R.layout.service_filter_header, null)
    getListView().addHeaderView(header, null, false)
    val footer = inflater.inflate(R.layout.service_filter_footer, null)
    getListView().addFooterView(footer, null, false)
    adapter.foreach {
      adapter =>
        setListAdapter(adapter)
        registerForContextMenu(getListView())
        // 1st time run
        if (savedInstanceState == null)
          runOnUiThread(new Runnable { def run = adapter.notifyDataSetChanged(true) })
    }
  }
  @Loggable
  override def onStart() {
    super.onStart()
  }
  @Loggable
  override def onResume() {
    super.onResume()
    registerReceiver(receiver, new IntentFilter(Common.Intent.update))
  }
  @Loggable
  override def onPause() {
    super.onPause()
    unregisterReceiver(receiver)
  }
  @Loggable
  override def onDestroy() {
    super.onDestroy()
  }
  @Loggable
  override protected def onSaveInstanceState(savedInstanceState: Bundle) = adapter foreach {
    adapter =>
      savedInstanceState.putStringArray(FilterAddActivity.STATE_PENDING, adapter.getPending.toArray)
      savedInstanceState.putString(FilterAddActivity.STATE_HTEXT1, headerInterface.getText.toString)
      savedInstanceState.putString(FilterAddActivity.STATE_HTEXT2, headerIP1.getText.toString)
      savedInstanceState.putString(FilterAddActivity.STATE_HTEXT3, headerIP2.getText.toString)
      savedInstanceState.putString(FilterAddActivity.STATE_HTEXT4, headerIP3.getText.toString)
      savedInstanceState.putString(FilterAddActivity.STATE_HTEXT5, headerIP4.getText.toString)
      super.onSaveInstanceState(savedInstanceState)
  }
  @Loggable
  override protected def onRestoreInstanceState(savedInstanceState: Bundle) = {
    super.onRestoreInstanceState(savedInstanceState)
    adapter foreach {
      adapter =>
        runOnUiThread(new Runnable {
          def run = {
            adapter.setPending(savedInstanceState.getStringArray(FilterAddActivity.STATE_PENDING))
            if (adapter.getPending.isEmpty)
              footerApply.setEnabled(false)
            else
              footerApply.setEnabled(true)
          }
          headerInterface.setText(savedInstanceState.getString(FilterAddActivity.STATE_HTEXT1))
          headerIP1.setText(savedInstanceState.getString(FilterAddActivity.STATE_HTEXT2))
          headerIP2.setText(savedInstanceState.getString(FilterAddActivity.STATE_HTEXT3))
          headerIP3.setText(savedInstanceState.getString(FilterAddActivity.STATE_HTEXT4))
          headerIP4.setText(savedInstanceState.getString(FilterAddActivity.STATE_HTEXT5))
          adapter.notifyDataSetChanged()
        })
    }
  }
  @Loggable
  override protected def onListItemClick(l: ListView, v: View, position: Int, id: Long) = adapter foreach {
    adapter =>
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
  def addCustomFilter(v: View) = adapter foreach {
    adapter =>
      val item: String = headerInterface.getText.toString.replaceFirst("^$", "*") + ":" +
        headerIP1.getText.toString.replaceFirst("^$", "*") + "." +
        headerIP2.getText.toString.replaceFirst("^$", "*") + "." +
        headerIP3.getText.toString.replaceFirst("^$", "*") + "." +
        headerIP4.getText.toString.replaceFirst("^$", "*")
      if (item == "*:*.*.*.*") {
        log.info("filter *:*.*.*.* is illegal")
        Toast.makeText(this, getString(R.string.service_filter_illegal).format(item), Common.Constant.toastTimeout).show()
      } else if (adapter.exists(item)) {
        log.info("filter " + item + " already exists")
        Toast.makeText(this, getString(R.string.service_filter_exists).format(item), Common.Constant.toastTimeout).show()
      } else {
        adapter.addPending(item)
        runOnUiThread(new Runnable {
          def run = {
            adapter.notifyDataSetChanged()
            if (adapter.getPending.isEmpty)
              footerApply.setEnabled(false)
            else
              footerApply.setEnabled(true)
          }
        })
        Toast.makeText(this, getString(R.string.service_filter_select).format(item), Common.Constant.toastTimeout).show()
      }
  }
  @Loggable
  def applySelectedFilters(v: View) {
    log.debug("applySelectedFilters(...)")
    showDialog(0) // apply?
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo) = adapter foreach {
    adapter =>
      if (v.getId() == android.R.id.list) {
        val info = menuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo]
        runOnUiThread(new Runnable {
          def run = {
            adapter.itemLongClick(info.position)
            val item = adapter.getItem(info.position - 1)
            if (item != null) {
              val Array(interface, ip) = item.split(":")
              val Array(ip1, ip2, ip3, ip4) = ip.split("""\.""")
              headerInterface.setText(interface.replaceAll("""\*""", ""))
              headerIP1.setText(ip1.replaceAll("""\*""", ""))
              headerIP2.setText(ip2.replaceAll("""\*""", ""))
              headerIP3.setText(ip3.replaceAll("""\*""", ""))
              headerIP4.setText(ip4.replaceAll("""\*""", ""))
            }
          }
        })
      }
  }
  @Loggable
  override protected def onCreateDialog(id: Int): Dialog = {
    // there is only one - apply
    new AlertDialog.Builder(this).
      setTitle("Apply").
      setMessage("R u sure?").
      setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, whichButton: Int) {
          FilterAddActivity.this.onSubmit()
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
  private def onSubmit() = for {
    inner <- AppActivity.Inner
    adapter <- adapter
  } {
    val pref = getSharedPreferences(Common.Preference.filter, Context.MODE_PRIVATE)
    val editor = pref.edit()
    adapter.getPending.foreach(filter => editor.putBoolean(filter, true))
    editor.commit()
  }
  @Loggable
  private def predefinedFilters(): Seq[String] = {
    log.debug("predefinedFilters(...)")
    Common.listInterfaces().map(entry => {
      val Array(interface, ip) = entry.split(":")
      if (ip == "0.0.0.0")
        Seq(interface + ":*.*.*.*")
      else
        Seq(entry, interface + ":*.*.*.*", "*:" + ip)
    }).flatten
  }
}

object FilterAddActivity {
  private val STATE_PENDING = "pending"
  private val STATE_HTEXT1 = "htext1"
  private val STATE_HTEXT2 = "htext2"
  private val STATE_HTEXT3 = "htext3"
  private val STATE_HTEXT4 = "htext4"
  private val STATE_HTEXT5 = "htext5"
  case class FilterItem(value: String)
}