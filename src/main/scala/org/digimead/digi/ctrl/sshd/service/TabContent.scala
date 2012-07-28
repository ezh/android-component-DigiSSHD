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

package org.digimead.digi.ctrl.sshd.service

import scala.annotation.target.beanGetter
import scala.annotation.target.beanSetter
import scala.annotation.target.getter
import scala.annotation.target.setter

import org.digimead.digi.ctrl.lib.androidext.Util
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDActivity
import org.digimead.digi.ctrl.sshd.SSHDTabAdapter
import org.digimead.digi.ctrl.sshd.ext.TabInterface

import com.actionbarsherlock.app.SherlockListFragment
import com.actionbarsherlock.view.Menu
import com.actionbarsherlock.view.MenuInflater
import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.text.Html
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
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
  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    SSHDActivity.ppGroup("service.TabContent.onCreateView") {
      val view = inflater.inflate(R.layout.tab_service, null)
      val context = getSherlockActivity
      // prepare empty view
      // interfaceFilters
      val interfaceFiltersHeader = view.findViewById(Util.getId(context, "nodata_header_interfacefilter")).asInstanceOf[TextView]
      interfaceFiltersHeader.setText(Html.fromHtml(Util.getString(context, "block_interfacefilter_title").getOrElse("interface filters")))
      // options
      val optionsHeader = view.findViewById(Util.getId(context, "nodata_header_option")).asInstanceOf[TextView]
      optionsHeader.setText(Html.fromHtml(Util.getString(context, "block_option_title").getOrElse("options")))
      // serviceEnvironment
      val serviceEnvironmentHeader = view.findViewById(Util.getId(context, "nodata_header_serviceenvironment")).asInstanceOf[TextView]
      serviceEnvironmentHeader.setText(Html.fromHtml(Util.getString(context, "block_serviceenvironment_title").getOrElse("environment")))
      // serviceSoftware
      val serviceSoftwareHeader = view.findViewById(Util.getId(context, "nodata_header_servicesoftware")).asInstanceOf[TextView]
      serviceSoftwareHeader.setText(Html.fromHtml(Util.getString(context, "block_components_title").getOrElse("components")))
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
    if (SSHDTabAdapter.getSelected.clazz == getClass)
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
  override def onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) = {
    import com.actionbarsherlock.view.MenuItem
    menu.add(0, 1, 1, android.R.string.cancel).setIcon(android.R.drawable.ic_menu_camera).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    menu.add(0, 2, 2, android.R.string.cut).setIcon(android.R.drawable.ic_menu_agenda).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    menu.add(0, 3, 3, android.R.string.paste).setIcon(android.R.drawable.ic_menu_compass).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    menu.add(0, 4, 4, android.R.string.search_go).setIcon(android.R.drawable.ic_menu_upload).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) = for {
    filterBlock <- TabContent.filterBlock
    optionBlock <- TabContent.optionBlock
    environmentBlock <- TabContent.environmentBlock
    componentBlock <- TabContent.componentBlock
  } {
    super.onCreateContextMenu(menu, v, menuInfo)
    menuInfo match {
      case info: AdapterContextMenuInfo =>
        TabContent.adapter.getItem(info.position) match {
          case item: FilterBlock.Item =>
            filterBlock.onCreateContextMenu(menu, v, menuInfo, item)
          case item: OptionBlock.Item =>
            optionBlock.onCreateContextMenu(menu, v, menuInfo, item)
          case item: EnvironmentBlock.Item =>
            environmentBlock.onCreateContextMenu(menu, v, menuInfo, item)
          case item: ComponentBlock.Item =>
            componentBlock.onCreateContextMenu(menu, v, menuInfo, item)
          case null =>
          // loading...
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
      filterBlock <- TabContent.filterBlock
      optionBlock <- TabContent.optionBlock
      environmentBlock <- TabContent.environmentBlock
      componentBlock <- TabContent.componentBlock
    } yield {
      val info = menuItem.getMenuInfo.asInstanceOf[AdapterContextMenuInfo]
      TabContent.adapter.getItem(info.position) match {
        case item: FilterBlock.Item =>
          filterBlock.onContextItemSelected(menuItem, item)
        case item: OptionBlock.Item =>
          optionBlock.onContextItemSelected(menuItem, item)
        case item: EnvironmentBlock.Item =>
          environmentBlock.onContextItemSelected(menuItem, item)
        case item: ComponentBlock.Item =>
          componentBlock.onContextItemSelected(menuItem, item)
        case item =>
          log.fatal("unknown item " + item)
          false
      }
    }
  } getOrElse false
  @Loggable
  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) = for {
    filterBlock <- TabContent.filterBlock
    optionBlock <- TabContent.optionBlock
    environmentBlock <- TabContent.environmentBlock
    componentBlock <- TabContent.componentBlock
  } {
    TabContent.adapter.getItem(position) match {
      case filterItem: FilterBlock.Item =>
        TabContent.filterBlock.foreach(_.onListItemClick(l, v, filterItem))
      case optionItem: OptionBlock.Item =>
        TabContent.optionBlock.foreach(_.onListItemClick(l, v, optionItem))
      case componentItem: ComponentBlock.Item =>
        TabContent.componentBlock.foreach(_.onListItemClick(l, v, componentItem))
      case null =>
      // loading...
      case item =>
      // skip clicks on splitter, footer, etc...
    }
  }
  def getTabDescriptionFragment() = TabDescription()
}

object TabContent extends Logging {
  /** profiling support */
  private val ppLoading = SSHDActivity.ppGroup.start("sessions.TabContent$")
  /** */
  @volatile private[service] var fragment: Option[TabContent] = None
  lazy val adapter: MergeAdapter = {
    val adapter = new MergeAdapter()
    componentBlock.foreach(_ appendTo (adapter))
    optionBlock.foreach(_ appendTo (adapter))
    environmentBlock.foreach(_ appendTo (adapter))
    filterBlock.foreach(_ appendTo (adapter))
    adapter
  }
  private lazy val componentBlock: Option[ComponentBlock] = AppComponent.Context.map(context => new ComponentBlock(context))
  private lazy val optionBlock: Option[OptionBlock] = AppComponent.Context.map(context => new OptionBlock(context))
  private lazy val environmentBlock: Option[EnvironmentBlock] = AppComponent.Context.map(context => new EnvironmentBlock(context))
  private lazy val filterBlock: Option[FilterBlock] = AppComponent.Context.map(context => new FilterBlock(context))
  log.debug("alive")
  ppLoading.stop

  def accumulator(): Option[Fragment] = fragment orElse AppComponent.AppContext.flatMap {
    context =>
      Option(Fragment.instantiate(context, classOf[org.digimead.digi.ctrl.sshd.service.TabContent].getName, null))
  }
}