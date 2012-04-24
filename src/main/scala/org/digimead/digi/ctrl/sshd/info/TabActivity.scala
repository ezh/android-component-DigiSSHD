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

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.block.CommunityBlock
import org.digimead.digi.ctrl.lib.block.LegalBlock
import org.digimead.digi.ctrl.lib.block.SupportBlock
import org.digimead.digi.ctrl.lib.block.ThanksBlock
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDActivity

import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
import android.app.ListActivity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.ListView
import android.widget.TextView

class TabActivity extends ListActivity with Logging {
  log.debug("alive")

  @Loggable
  override def onCreate(savedInstanceState: Bundle) = synchronized {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.info)
    TabActivity.activity = Some(this)

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
    val lv = getListView()
    registerForContextMenu(lv)
    TabActivity.adapter.foreach(adapter => runOnUiThread(new Runnable { def run = setListAdapter(adapter) }))
  }
  @Loggable
  override def onResume() {
    super.onResume()
    TabActivity.interfaceBlock.foreach(_.updateAdapter())
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) = for {
    adapter <- TabActivity.adapter
    interfaceBlock <- TabActivity.interfaceBlock
    supportBlock <- TabActivity.supportBlock
    communityBlock <- TabActivity.communityBlock
    thanksBlock <- TabActivity.thanksBlock
    legalBlock <- TabActivity.legalBlock
  } {
    super.onCreateContextMenu(menu, v, menuInfo)
    menuInfo match {
      case info: AdapterContextMenuInfo =>
        adapter.getItem(info.position) match {
          case item: SupportBlock.Item =>
            supportBlock.onCreateContextMenu(menu, v, menuInfo, item)
          case item: CommunityBlock.Item =>
            communityBlock.onCreateContextMenu(menu, v, menuInfo, item)
          case item: ThanksBlock.Item =>
            thanksBlock.onCreateContextMenu(menu, v, menuInfo, item)
          case item: LegalBlock.Item =>
            legalBlock.onCreateContextMenu(menu, v, menuInfo, item)
          case item: InterfaceBlock.Item =>
            interfaceBlock.onCreateContextMenu(menu, v, menuInfo, item)
          case item =>
            log.fatal("unknown item " + item)
        }
      case info =>
        log.fatal("unsupported menu info " + info)
    }
  }
  @Loggable
  override def onContextItemSelected(menuItem: MenuItem): Boolean = {
    for {
      adapter <- TabActivity.adapter
      interfaceBlock <- TabActivity.interfaceBlock
      supportBlock <- TabActivity.supportBlock
      communityBlock <- TabActivity.communityBlock
      thanksBlock <- TabActivity.thanksBlock
      legalBlock <- TabActivity.legalBlock
    } yield {
      val info = menuItem.getMenuInfo.asInstanceOf[AdapterContextMenuInfo]
      adapter.getItem(info.position) match {
        case item: SupportBlock.Item =>
          supportBlock.onContextItemSelected(menuItem, item)
        case item: CommunityBlock.Item =>
          communityBlock.onContextItemSelected(menuItem, item)
        case item: ThanksBlock.Item =>
          thanksBlock.onContextItemSelected(menuItem, item)
        case item: LegalBlock.Item =>
          legalBlock.onContextItemSelected(menuItem, item)
        case item =>
          log.fatal("unknown item " + item)
          false
      }
    }
  } getOrElse false
  @Loggable
  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) = for {
    adapter <- TabActivity.adapter
    interfaceBlock <- TabActivity.interfaceBlock
    supportBlock <- TabActivity.supportBlock
    communityBlock <- TabActivity.communityBlock
    thanksBlock <- TabActivity.thanksBlock
    legalBlock <- TabActivity.legalBlock
  } {
    adapter.getItem(position) match {
      case item: SupportBlock.Item =>
        supportBlock.onListItemClick(l, v, item)
      case item: CommunityBlock.Item =>
        communityBlock.onListItemClick(l, v, item)
      case item: ThanksBlock.Item =>
        thanksBlock.onListItemClick(l, v, item)
      case item: LegalBlock.Item =>
        legalBlock.onListItemClick(l, v, item)
      case item: InterfaceBlock.Item =>
        interfaceBlock.onListItemClick(l, v, item)
      case item =>
        log.fatal("unknown item " + item)
    }
  }
}

object TabActivity extends Logging {
  val legal = """<img src="ic_launcher">The DigiControl consists of a software program protected by copyright and other applicable
intellectual property laws and treaties. Certain Licensed Software programs may be wholly
or partially subject to other licenses. For details see the description of each individual package. <br/>
Copyright © 2011-2012 Alexey B. Aksenov/Ezh. All rights reserved."""
  @volatile private[info] var activity: Option[TabActivity] = None
  @volatile private[info] var adapter: Option[MergeAdapter] = None
  @volatile private var interfaceBlock: Option[InterfaceBlock] = None
  @volatile private var supportBlock: Option[SupportBlock] = None
  @volatile private var communityBlock: Option[CommunityBlock] = None
  @volatile private var thanksBlock: Option[ThanksBlock] = None
  @volatile private var legalBlock: Option[LegalBlock] = None
  private val interfaceFilterUpdateReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) =
      interfaceBlock.foreach(_.updateAdapter())
  }

  def addLazyInit = AppComponent.LazyInit("info.TabActivity initialize onCreate", 50) {
    SSHDActivity.activity match {
      case Some(activity) if activity.isInstanceOf[Activity] =>
        // initialize once from onCreate
        if (adapter == None) {
          adapter = Some(new MergeAdapter())
          interfaceBlock = Some(new InterfaceBlock(activity))
          supportBlock = Some(new SupportBlock(activity, Uri.parse(SSHDActivity.info.project), Uri.parse(SSHDActivity.info.project + "/issues"),
            SSHDActivity.info.email, SSHDActivity.info.name, "+18008505240", "ezhariur"))
          communityBlock = Some(new CommunityBlock(activity, Uri.parse(SSHDActivity.info.project + "/wiki")))
          thanksBlock = Some(new ThanksBlock(activity))
          legalBlock = Some(new LegalBlock(activity, List(LegalBlock.Item(legal)("https://github.com/ezh/android-DigiControl/blob/master/LICENSE.md"))))
          for {
            adapter <- adapter
            interfaceBlock <- interfaceBlock
            supportBlock <- supportBlock
            communityBlock <- communityBlock
            thanksBlock <- thanksBlock
            legalBlock <- legalBlock
          } {
            interfaceBlock appendTo (adapter)
            communityBlock appendTo (adapter)
            supportBlock appendTo (adapter)
            //thanksBlock appendTo (adapter)
            legalBlock appendTo (adapter)
            TabActivity.activity.foreach(ctx => ctx.runOnUiThread(new Runnable { def run = ctx.setListAdapter(adapter) }))
          }
        }
        // initialize every time from onCreate
        // register UpdateInterfaceFilter BroadcastReceiver
        val interfaceFilterUpdateFilter = new IntentFilter(DIntent.UpdateInterfaceFilter)
        interfaceFilterUpdateFilter.addDataScheme("code")
        activity.registerReceiver(interfaceFilterUpdateReceiver, interfaceFilterUpdateFilter)
      case context =>
        log.warn("inappropriate SSHDActivity context " + context)
    }
  }
  @Loggable
  def UpdateActiveInterfaces(state: DState.Value) = state match {
    case DState.Active =>
      interfaceBlock.foreach(_.updateActiveInteraces(false))
    case DState.Passive =>
      interfaceBlock.foreach(_.updateActiveInteraces(true))
    case _ =>
  }
}

