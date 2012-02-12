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

package org.digimead.digi.ctrl.sshd.info

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.AppActivity
import org.digimead.digi.ctrl.lib.AppService
import org.digimead.digi.ctrl.lib.Common
import org.digimead.digi.ctrl.sshd.R
import org.slf4j.LoggerFactory

import com.commonsware.cwac.merge.MergeAdapter

import android.app.AlertDialog
import android.app.Dialog
import android.app.ListActivity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView

class TabActivity extends ListActivity {
  private val log = LoggerFactory.getLogger(getClass.getName().replaceFirst("org.digimead.digi.ctrl", "o.d.d.c"))
  private val adapter = new MergeAdapter()
  private var interfaces = Seq(TabActivity.InterfaceItem(null, null)) // null is "pending..." item, handled at InterfaceAdapter
  private lazy val interfaceAdapter = new InterfaceAdapter(this, () => { interfaces })
  private val receiver = new BroadcastReceiver() {
    @Loggable
    def onReceive(context: Context, intent: Intent) = {
      intent.getAction() match {
        case Common.Intent.update =>
          log.error("UPPDATE2!!! " + context)
          updatedInterfaceList()
        case _ =>
          log.error("skip unknown intent " + intent + " with context " + context)
      }
    }
  }
  log.debug("alive")
  @Loggable
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.info)
    buildUIInterfaces(adapter)
    buildUISupport(adapter)
    buildUICommunity(adapter)
    buildUIThanks(adapter)
    buildUICopyright(adapter)
    setListAdapter(adapter)
  }
  @Loggable
  override def onResume() {
    registerReceiver(receiver, new IntentFilter(Common.Intent.update))
    super.onResume()
    updatedInterfaceList()
  }
  @Loggable
  override def onPause() {
    super.onPause()
    unregisterReceiver(receiver)
  }
  @Loggable
  def buildUIInterfaces(adapter: MergeAdapter) {
    val header = getLayoutInflater.inflate(R.layout.header, null).asInstanceOf[TextView]
    header.setText(getString(R.string.info_interfaces))
    header.setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View) = showDialog(TabActivity.DIALOG_INTERFACES_ID)
    })
    adapter.addView(header)
    adapter.addAdapter(interfaceAdapter)
  }
  @Loggable
  def buildUISupport(adapter: MergeAdapter) {
    val header = getLayoutInflater.inflate(R.layout.header, null).asInstanceOf[TextView]
    header.setText(getString(R.string.info_support))
    adapter.addView(header)
    // TODO project
    // TODO issues
    // TODO documentation
    // TODO send sms with link
    // TODO send email with link
    // TODO multisend
    // NOTE as ICONS! after click notification that url copy to clipboard
  }
  @Loggable
  def buildUICommunity(adapter: MergeAdapter) {
    val header = getLayoutInflater.inflate(R.layout.header, null).asInstanceOf[TextView]
    header.setText(getString(R.string.info_community))
    adapter.addView(header)
    // TODO ui translation help: LANG as link
    // TODO documentation translation help: LANG as link
    // TODO web page/description translation help: LANG as link
  }
  @Loggable
  def buildUIThanks(adapter: MergeAdapter) {
    val header = getLayoutInflater.inflate(R.layout.header, null).asInstanceOf[TextView]
    header.setText(getString(R.string.info_thanks))
    adapter.addView(header)
    // TODO check apk key (debug/release): ask + market link or respect
    // TODO list of contributor
  }
  @Loggable
  def buildUICopyright(adapter: MergeAdapter) {
    val header = getLayoutInflater.inflate(R.layout.header, null).asInstanceOf[TextView]
    header.setText(getString(R.string.info_copyright))
    adapter.addView(header)
    // TODO copyright
  }
  @Loggable
  override def onListItemClick(parent: ListView, v: View, position: Int, id: Long) = adapter.getItem(position) match {
    case item: TabActivity.InterfaceItem =>
      showDialog(TabActivity.DIALOG_INTERFACES_ID)
    case item => log.error("unknown item: " + item)
  }
  @Loggable
  override def onCreateDialog(id: Int): Dialog = id match {
    case TabActivity.DIALOG_INTERFACES_ID =>
      val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
      val layout = inflater.inflate(R.layout.info_interfaces_dialog, null).asInstanceOf[ViewGroup]
      val builder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.InterfacesLegendDialog))
      builder.setView(layout)
      val dialog = builder.create()
      layout.setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View) = dialog.dismiss()
      })
      dialog.setCanceledOnTouchOutside(true)
      dialog
    case id =>
      log.error("unknown dialog id " + id)
      null
  }
  @Loggable
  private def updatedInterfaceList() = for {
    innerApp <- AppActivity.Inner
    innerSrv <- AppService.Inner
  } {
    interfaces = Common.listInterfaces().map(i => TabActivity.InterfaceItem(i, innerSrv.getInterfaceStatus(i, innerApp.filters())))
    runOnUiThread(new Runnable() { def run = interfaceAdapter.notifyDataSetChanged(true) })
  }
}

object TabActivity {
  val DIALOG_INTERFACES_ID = 0
  /*
   * status:
   * None - unused
   * Some(false) - passive
   * Some(true) - active
   */
  case class InterfaceItem(val value: String, val status: Option[Boolean]) {
    override def toString() = value
  }
}