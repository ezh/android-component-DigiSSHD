/**
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

import java.util.Locale
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.block.Community
import org.digimead.digi.ctrl.lib.block.Legal
import org.digimead.digi.ctrl.lib.block.Support
import org.digimead.digi.ctrl.lib.block.Thanks
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import com.commonsware.cwac.merge.MergeAdapter
import android.app.AlertDialog
import android.app.Dialog
import android.app.ListActivity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import android.view.ContextMenu
import android.widget.AdapterView.AdapterContextMenuInfo
import android.view.MenuItem

class TabActivity extends ListActivity with Logging {
  private lazy val info = AppActivity.Inner.getCachedComponentInfo(TabActivity.locale, TabActivity.localeLanguage).get
  private val adapter = new MergeAdapter()
  private lazy val interfaceBlock = new Interface(this)
  private lazy val supportBlock = new Support(this, Uri.parse(info.project), Uri.parse(info.project + "/issues"),
    info.email, info.name, "+74955185377", "ezhariur")
  private lazy val communityBlock = new Community(this, Uri.parse(info.project + "/wiki"))
  private lazy val thanksBlock = new Thanks(this)
  val legal = """<img src="ic_launcher">The DigiControl consists of a software program protected by copyright and other applicable
intellectual property laws and treaties. Certain Licensed Software programs may be wholly
or partially subject to other licenses. For details see the description of each individual package. <br/>
Copyright © 2011-2012 Alexey B. Aksenov/Ezh. All rights reserved."""
  private lazy val legalBlock = new Legal(this, List(Legal.Item(legal)("https://github.com/ezh/android-DigiControl/blob/master/LICENSE.md")))
  log.debug("alive")

  @Loggable
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.info)
    // prepare empty view
    // interfaces
    val interfacesHeader = findViewById(Android.getId(this, "nodata_header_interface")).asInstanceOf[TextView]
    interfacesHeader.setText(Html.fromHtml(Android.getString(this, "block_interface_title").getOrElse("interfaces")))
    // community
    val communityHeader = findViewById(Android.getId(this, "nodata_header_community")).asInstanceOf[TextView]
    communityHeader.setText(Html.fromHtml(Android.getString(this, "block_community_title").getOrElse("community")))
    // support
    val supportHeader = findViewById(Android.getId(this, "nodata_header_support")).asInstanceOf[TextView]
    supportHeader.setText(Html.fromHtml(Android.getString(this, "block_support_title").getOrElse("support")))
    // legal
    val legalHeader = findViewById(Android.getId(this, "nodata_header_legal")).asInstanceOf[TextView]
    legalHeader.setText(Html.fromHtml(Android.getString(this, "block_legal_title").getOrElse("legal")))
    // prepare active view
    getListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE)
    val lv = getListView()
    registerForContextMenu(lv)
    lv.setLongClickable(false)
    AppActivity.LazyInit("initialize info adapter") {
      interfaceBlock appendTo adapter
      communityBlock appendTo adapter
      supportBlock appendTo adapter
      //thanksBlock appendTo adapter
      legalBlock appendTo adapter
      runOnUiThread(new Runnable { def run = setListAdapter(adapter) })
    }
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo)
    menuInfo match {
      case info: AdapterContextMenuInfo =>
        adapter.getItem(info.position) match {
          case item: Support.Item =>
            supportBlock.onCreateContextMenu(menu, v, menuInfo, item)
          case item: Community.Item =>
            communityBlock.onCreateContextMenu(menu, v, menuInfo, item)
          case item: Thanks.Item =>
            thanksBlock.onCreateContextMenu(menu, v, menuInfo, item)
          case item: Legal.Item =>
            legalBlock.onCreateContextMenu(menu, v, menuInfo, item)
          case item =>
            log.fatal("unknown item " + item)
        }
      case info =>
        log.fatal("unsupported menu info " + info)
    }
  }
  @Loggable
  override def onContextItemSelected(menuItem: MenuItem): Boolean = {
    val info = menuItem.getMenuInfo.asInstanceOf[AdapterContextMenuInfo]
    adapter.getItem(info.position) match {
      case item: Support.Item =>
        supportBlock.onContextItemSelected(menuItem, item)
      case item: Community.Item =>
        communityBlock.onContextItemSelected(menuItem, item)
      case item: Thanks.Item =>
        thanksBlock.onContextItemSelected(menuItem, item)
      case item: Legal.Item =>
        legalBlock.onContextItemSelected(menuItem, item)
      case item =>
        log.fatal("unknown item " + item)
        false
    }
  }
  /*  @Loggable
  override def onResume() {
    registerReceiver(receiver, new IntentFilter(DIntent.Update))
    super.onResume()
    updatedInterfaceList()
  }
  @Loggable
  override def onPause() {
    super.onPause()
    unregisterReceiver(receiver)
  }*/
  @Loggable
  def buildUIInterfaces(adapter: MergeAdapter) {
    val header = getLayoutInflater.inflate(R.layout.header, null).asInstanceOf[TextView]
    header.setText(getString(R.string.info_interfaces))
    header.setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View) = showDialog(TabActivity.DIALOG_INTERFACES_ID)
    })
    adapter.addView(header)
    //    adapter.addAdapter(interfaceAdapter)
  }
  @Loggable
  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) = {
    adapter.getItem(position) match {
      case item: Support.Item =>
        supportBlock.onListItemClick(l, v, item)
      case item: Community.Item =>
        communityBlock.onListItemClick(l, v, item)
      case item: Thanks.Item =>
        thanksBlock.onListItemClick(l, v, item)
      case item: Legal.Item =>
        legalBlock.onListItemClick(l, v, item)
      case item: Interface.Item =>
        showDialog(TabActivity.DIALOG_INTERFACES_ID)
      case item =>
        log.fatal("unknown item " + item)
    }
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
  private def updatedInterfaceList() = {
    //interfaces = Common.listInterfaces().map(i => TabActivity.InterfaceItem(i, AppService.Inner.getInterfaceStatus(i, AppActivity.Inner.filters())))
    //    runOnUiThread(new Runnable() { def run = interfaceAdapter.notifyDataSetChanged(true) })
  }
}

object TabActivity {
  val locale = Locale.getDefault().getLanguage() + "_" + Locale.getDefault().getCountry()
  val localeLanguage = Locale.getDefault().getLanguage()
  val DIALOG_INTERFACES_ID = 0
}
/*
 *   
 *   
  private val receiver = new BroadcastReceiver() {
    @Loggable
    def onReceive(context: Context, intent: Intent) = {
      intent.getAction() match {
        case DIntent.Update =>
          log.error("UPPDATE2!!! " + context)
          updatedInterfaceList()
        case _ =>
          log.error("skip unknown intent " + intent + " with context " + context)
      }
    }
  }
*/
