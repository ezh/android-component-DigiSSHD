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

import java.util.ArrayList
import scala.collection.JavaConversions._
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.sshd.R
import org.slf4j.LoggerFactory
import com.commonsware.cwac.merge.MergeAdapter
import android.app.Activity
import android.app.ListActivity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import org.digimead.digi.ctrl.lib.aop.Logging
import org.digimead.digi.ctrl.sshd.service.software.{ UI => SWUI }
import org.digimead.digi.ctrl.sshd.service.options.{ UI => OPTUI }
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.declaration.DPreference

class TabActivity extends ListActivity with Logging {
  private[service] val adapter = new MergeAdapter()
  private lazy val interfaceAdapter = new ArrayAdapter[TabActivity.InterfaceItem](this, android.R.layout.simple_list_item_checked, new ArrayList[TabActivity.InterfaceItem]())
  private[service] lazy val lv = getListView()
  private var interfaceRemoveButton: Button = null
  private var uiSoftware: SWUI = null
  private var uiOptions: OPTUI = null
  @Loggable
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.service)
    buildUIInterfaces(adapter)
    uiOptions = new OPTUI(this)
    buildUIEnvironment(adapter)
    uiSoftware = new SWUI(this)
    setListAdapter(adapter)
    getListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE)
    updateButtonState()
  }
  @Loggable
  def buildUIInterfaces(adapter: MergeAdapter) {
    val header = getLayoutInflater.inflate(R.layout.service_interface_header, getListView(), false).asInstanceOf[LinearLayout]
    interfaceRemoveButton = header.findViewById(R.id.service_interface_remove).asInstanceOf[Button]
    // TODO add context menu
    adapter.addView(header)
    adapter.addAdapter(interfaceAdapter)
    updateInterfaceAdapter()
  }
  @Loggable
  def buildUIOptions(adapter: MergeAdapter) {
    val header = getLayoutInflater.inflate(R.layout.header, null).asInstanceOf[TextView]
    header.setText(getString(R.string.service_options))
    adapter.addView(header)
  }
  @Loggable
  def buildUIEnvironment(adapter: MergeAdapter) {
    val header = getLayoutInflater.inflate(R.layout.header, null).asInstanceOf[TextView]
    header.setText(getString(R.string.service_environment))
    val tableview = getLayoutInflater.inflate(R.layout.service_environment, null).asInstanceOf[TableLayout]
    adapter.addView(header)
    adapter.addView(tableview)
  }
  @Loggable
  def buildUISoftware(adapter: MergeAdapter) {

  }
  @Loggable
  override protected def onListItemClick(l: ListView, v: View, position: Int, id: Long) = {
    adapter.getItem(position) match {
      case interfaceItem: TabActivity.InterfaceItem =>
        lv.isItemChecked(position) match {
          case true =>
            interfaceItem.state = true
            Toast.makeText(this, getString(R.string.service_filter_enabled).format(interfaceItem), DConstant.toastTimeout).show()
          case false =>
            interfaceItem.state = false
            Toast.makeText(this, getString(R.string.service_filter_disabled).format(interfaceItem), DConstant.toastTimeout).show()
        }
      case item: software.UI.Item =>
        uiSoftware.onListItemClick(item)
      case _ =>
    }
  }
  @Loggable
  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    // enable child elements(buttons) focus
    for (i <- 0 until lv.getChildCount())
      lv.getChildAt(i).requestFocusFromTouch()
    resultCode match {
      case Activity.RESULT_OK if requestCode == TabActivity.FILTER_REQUEST =>
        updateInterfaceAdapter()
        updateButtonState()
      case _ =>
    }
  }
  @Loggable
  def onFilterAdd(v: View) = {
    startActivityForResult(new Intent(this, classOf[FilterAddActivity]), TabActivity.FILTER_REQUEST)
  }
  @Loggable
  def onFilterRemove(v: View) = {
    startActivityForResult(new Intent(this, classOf[FilterRemoveActivity]), TabActivity.FILTER_REQUEST)
  }
  @Loggable
  def onClickReInstall(v: View) = {
    log.info("reinstall files/force prepare evironment")
    Toast.makeText(this, Android.getString(this, "reinstall").getOrElse("reinstall"), DConstant.toastTimeout).show()
    AppActivity.Inner ! AppActivity.Message.PrepareEnvironment(this, false, true, (success) =>
      runOnUiThread(new Runnable() {
        def run = if (success)
          Toast.makeText(TabActivity.this, Android.getString(TabActivity.this,
            "reinstall_complete").getOrElse("reinstall complete"), DConstant.toastTimeout).show()
      }))
  }
  @Loggable
  def onClickCopyToClipboard(v: View) = {
    log.info("copy path of prepared files to clipboard")
    Common.copyPreparedFilesToClipboard(this)
  }
  @Loggable
  private def updateInterfaceAdapter() = {
    val pref = getSharedPreferences(DPreference.Filter, Context.MODE_PRIVATE)
    val values = pref.getAll().toSeq.map(t => (t._1, t._2.asInstanceOf[Boolean])).sorted
    interfaceAdapter.clear()
    if (values.nonEmpty)
      values.foreach(t => interfaceAdapter.add(TabActivity.InterfaceItem(t._1, t._2, this)))
    else
      interfaceAdapter.add(TabActivity.InterfaceItem(null, true, this))
    lv.post(new Runnable {
      def run = {
        interfaceAdapter.notifyDataSetChanged()
        var pos = 1
        if (values.nonEmpty)
          for (t <- values) {
            if (t._2)
              lv.setItemChecked(pos, true)
            pos += 1
          }
        else
          lv.setItemChecked(pos, true) // all interfaces always checked
      }
    })
  }
  @Loggable
  private def updateButtonState() = {
    lv.post(new Runnable {
      def run = {
        if (AppActivity.Inner.filters().isEmpty)
          interfaceRemoveButton.setEnabled(false)
        else
          interfaceRemoveButton.setEnabled(true)
      }
    })
  }
}

object TabActivity {
  val FILTER_REQUEST = 10000
  val DIALOG_FILTER_REMOVE_ID = 0
  case class InterfaceItem(_value: String, var _state: Boolean, _context: Context) {
    override def toString() =
      if (_value != null)
        _value
      else
        _context.getString(R.string.all)
    def state = _state
    def state_=(newState: Boolean): Unit = synchronized {
      if (_value == null)
        return
      if (newState != _state) {
        _state = newState
        val pref = _context.getSharedPreferences(DPreference.Filter, Context.MODE_PRIVATE)
        val editor = pref.edit()
        editor.putBoolean(_value, _state)
        editor.commit()
      }
    }
  }
}
