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
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package org.digimead.digi.ctrl.sshd.info

import scala.actors.Futures

import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.block.CommunityBlock
import org.digimead.digi.ctrl.lib.block.LegalBlock
import org.digimead.digi.ctrl.lib.block.SupportBlock
import org.digimead.digi.ctrl.lib.block.ThanksBlock
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.IAmYell
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDActivity
import org.digimead.digi.ctrl.sshd.SSHDTabAdapter
import org.digimead.digi.ctrl.sshd.ext.TabInterface

import com.actionbarsherlock.app.SherlockListFragment
import com.actionbarsherlock.view.Menu
import com.actionbarsherlock.view.MenuInflater
import com.actionbarsherlock.view.MenuItem
import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.text.Html
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.ListView
import android.widget.TextView

class TabContent extends SherlockListFragment with TabInterface with Logging {
  TabContent.fragment = Some(this)
  log.debug("alive")

  @Loggable
  override def onAttach(activity: Activity) {
    super.onAttach(activity)
    TabContent.fragment = Some(this)
  }
  @Loggable
  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = SSHDActivity.ppGroup("info.TabContent.onCreateView") {
    val view = inflater.inflate(R.layout.tab_info, null)
    val context = getSherlockActivity
    // prepare empty view
    // interfaces
    val interfacesHeader = view.findViewById(XResource.getId(context, "nodata_header_interface")).asInstanceOf[TextView]
    interfacesHeader.setText(Html.fromHtml(XResource.getString(context, "block_interface_title").getOrElse("interfaces")))
    // community
    val communityHeader = view.findViewById(XResource.getId(context, "nodata_header_community")).asInstanceOf[TextView]
    communityHeader.setText(Html.fromHtml(XResource.getString(context, "block_community_title").getOrElse("community")))
    // support
    val supportHeader = view.findViewById(XResource.getId(context, "nodata_header_support")).asInstanceOf[TextView]
    supportHeader.setText(Html.fromHtml(XResource.getString(context, "block_support_title").getOrElse("support")))
    // legal
    val legalHeader = view.findViewById(XResource.getId(context, "nodata_header_legal")).asInstanceOf[TextView]
    legalHeader.setText(Html.fromHtml(XResource.getString(context, "block_legal_title").getOrElse("legal")))
    view
  }
  @Loggable
  override def onActivityCreated(savedInstanceState: Bundle) = SSHDActivity.ppGroup("info.TabContent.onActivityCreated") {
    super.onActivityCreated(savedInstanceState)
    setListAdapter(TabContent.adapter)
    setHasOptionsMenu(true)
    registerForContextMenu(getListView)
  }
  @Loggable
  override def onResume() {
    super.onResume
    if (SSHDTabAdapter.getSelectedTab.clazz == getClass)
      showTabDescriptionFragment()
  }
  @Loggable
  def onTabSelected() = if (TabContent.fragment == Some(this) && getSherlockActivity != null)
    showTabDescriptionFragment()
  @Loggable
  override def onDetach() {
    TabContent.fragment = None
    super.onDetach()
  }
  @Loggable
  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) = for {
    interfaceBlock <- TabContent.interfaceBlock
    supportBlock <- TabContent.supportBlock
    communityBlock <- TabContent.communityBlock
    thanksBlock <- TabContent.thanksBlock
    legalBlock <- TabContent.legalBlock
  } {
    TabContent.adapter.getItem(position) match {
      case item: SupportBlock.Item =>
        showTabDescriptionFragment()
        supportBlock.onListItemClick(l, v, item)
      case item: CommunityBlock.Item =>
        showTabDescriptionFragment()
        communityBlock.onListItemClick(l, v, item)
      case item: ThanksBlock.Item =>
        showTabDescriptionFragment()
        thanksBlock.onListItemClick(l, v, item)
      case item: LegalBlock.Item =>
        showTabDescriptionFragment()
        legalBlock.onListItemClick(l, v, item)
      case item: InterfaceBlock.Item =>
        interfaceBlock.onListItemClick(l, v, item)
      case item =>
        log.fatal("unknown item " + item)
    }
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) = for {
    interfaceBlock <- TabContent.interfaceBlock
    supportBlock <- TabContent.supportBlock
    communityBlock <- TabContent.communityBlock
    thanksBlock <- TabContent.thanksBlock
    legalBlock <- TabContent.legalBlock
  } menuInfo match {
    case info: AdapterView.AdapterContextMenuInfo =>
      TabContent.adapter.getItem(info.position) match {
        case item: SupportBlock.Item =>
          supportBlock.onCreateContextMenu(menu, v, menuInfo, item)
        case item: CommunityBlock.Item =>
          communityBlock.onCreateContextMenu(menu, v, menuInfo, item)
        case item: ThanksBlock.Item =>
          thanksBlock.onCreateContextMenu(menu, v, menuInfo, item)
        case item: LegalBlock.Item =>
          legalBlock.onCreateContextMenu(menu, v, menuInfo, item)
          menu.add(Menu.NONE, XResource.getId(getSherlockActivity, "block_legal_coreutils"), 1,
            XResource.getString(getSherlockActivity, "block_legal_coreutils").getOrElse("GNU Coreutils"))
          menu.add(Menu.NONE, XResource.getId(getSherlockActivity, "block_legal_grep"), 1,
            XResource.getString(getSherlockActivity, "block_legal_grep").getOrElse("GNU Grep"))
        case item: InterfaceBlock.Item =>
          interfaceBlock.onCreateContextMenu(menu, v, menuInfo, item)
        case item =>
          log.fatal("unknown item " + item)
      }
    case info =>
      log.fatal("unsupported menu info " + info)
  }
  @Loggable
  override def onContextItemSelected(menuItem: android.view.MenuItem): Boolean = {
    for {
      interfaceBlock <- TabContent.interfaceBlock
      supportBlock <- TabContent.supportBlock
      communityBlock <- TabContent.communityBlock
      thanksBlock <- TabContent.thanksBlock
      legalBlock <- TabContent.legalBlock
    } yield menuItem.getMenuInfo match {
      case info: AdapterContextMenuInfo =>
        if (getListView.getPositionForView(info.targetView) == -1)
          return false
        TabContent.adapter.getItem(info.position) match {
          case item: SupportBlock.Item =>
            supportBlock.onContextItemSelected(menuItem, item)
          case item: CommunityBlock.Item =>
            communityBlock.onContextItemSelected(menuItem, item)
          case item: ThanksBlock.Item =>
            thanksBlock.onContextItemSelected(menuItem, item)
          case item: LegalBlock.Item =>
            menuItem.getItemId match {
              case id if id == XResource.getId(getSherlockActivity, "block_legal_coreutils") =>
                log.debug("open link from " + TabContent.CoreutilsURL)
                try {
                  val intent = new Intent(Intent.ACTION_VIEW, Uri.parse(TabContent.CoreutilsURL))
                  intent.addCategory(Intent.CATEGORY_BROWSABLE)
                  startActivity(intent)
                  true
                } catch {
                  case e =>
                    IAmYell("Unable to open license link " + TabContent.CoreutilsURL, e)
                    false
                }
              case id if id == XResource.getId(getSherlockActivity, "block_legal_grep") =>
                log.debug("open link from " + TabContent.GrepURL)
                try {
                  val intent = new Intent(Intent.ACTION_VIEW, Uri.parse(TabContent.GrepURL))
                  intent.addCategory(Intent.CATEGORY_BROWSABLE)
                  startActivity(intent)
                  true
                } catch {
                  case e =>
                    IAmYell("Unable to open license link " + TabContent.GrepURL, e)
                    false
                }
              case id =>
                legalBlock.onContextItemSelected(menuItem, item)
            }
          case item =>
            log.fatal("unknown item " + item)
            false
        }
    }
  } getOrElse false
  def getTabDescriptionFragment() = TabDescription()
}

object TabContent extends Logging {
  /** profiling support */
  private val ppLoading = SSHDActivity.ppGroup.start("info.TabContent$")
  val legal = """<img src="ic_launcher">The DigiSSHD Project is licensed to you under the terms of the
GNU General Public License (GPL) version 3 or later, a copy of which has been included in the LICENSE file.
Please check the individual source files for details. <br/>
Copyright © 2011-2012 Alexey B. Aksenov/Ezh. All rights reserved."""
  val CoreutilsURL = "http://www.gnu.org/software/coreutils/"
  val GrepURL = "http://www.gnu.org/software/grep/"
  /** TabContent fragment instance */
  @volatile private[info] var fragment: Option[TabContent] = None
  lazy val adapter: MergeAdapter = {
    val adapter = new MergeAdapter()
    interfaceBlock.foreach(_ appendTo (adapter))
    communityBlock.foreach(_ appendTo (adapter))
    supportBlock.foreach(_ appendTo (adapter))
    //thanksBlock appendTo (adapter)
    legalBlock.foreach(_ appendTo (adapter))
    adapter
  }
  private lazy val supportBlock: Option[SupportBlock] = AppComponent.Context.map(context =>
    new SupportBlock(context.getApplicationContext,
      Futures.future[Uri] { Uri.parse(SSHDActivity.info.project) },
      Futures.future[Uri] { Uri.parse(SSHDActivity.info.project + "/issues") },
      Futures.future[String] { SSHDActivity.info.email },
      Futures.future[String] { SSHDActivity.info.name },
      Futures.future[String] { "+18008505240" },
      Futures.future[String] { "ezhariur" },
      Futures.future[String] { "413030952" }))
  private lazy val communityBlock: Option[CommunityBlock] = AppComponent.Context.map(context =>
    new CommunityBlock(context.getApplicationContext,
      Some(Futures.future[Uri] { Uri.parse("http://forum.xda-developers.com/showthread.php?t=1612044") }),
      Some(Futures.future[Uri] { Uri.parse(SSHDActivity.info.project + "/wiki") }),
      Some(Futures.future[Uri] { Uri.parse(SSHDActivity.info.translation) }),
      Some(Futures.future[Uri] { Uri.parse(SSHDActivity.info.translationCommon) })))
  private lazy val thanksBlock: Option[ThanksBlock] = AppComponent.Context.map(context =>
    new ThanksBlock(context.getApplicationContext))
  private lazy val legalBlock: Option[LegalBlock] = AppComponent.Context.map(context =>
    new LegalBlock(context.getApplicationContext,
      List(Futures.future[LegalBlock.Item] { LegalBlock.Item(legal)("https://github.com/ezh/android-component-DigiSSHD/blob/master/LICENSE") })))
  private lazy val interfaceBlock: Option[InterfaceBlock] = AppComponent.Context.map(context =>
    new InterfaceBlock(context))
  log.debug("alive")
  ppLoading.stop

  def accumulator(): Option[Fragment] = fragment orElse AppComponent.AppContext.flatMap {
    context =>
      Option(Fragment.instantiate(context, classOf[org.digimead.digi.ctrl.sshd.info.TabContent].getName, null))
  }
}
