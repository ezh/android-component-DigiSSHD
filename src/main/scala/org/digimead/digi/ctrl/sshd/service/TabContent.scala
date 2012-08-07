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

import org.digimead.digi.ctrl.lib.androidext.XResource
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
  /**
   * Issue 7139: MenuItem.getMenuInfo() returns null for sub-menu items
   * affected API 8 - API 16, blame for such 'quality', few years without reaction
   */
  @volatile private var hackForIssue7139: AdapterContextMenuInfo = null
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
      val interfaceFiltersHeader = view.findViewById(XResource.getId(context, "nodata_header_interfacefilter")).asInstanceOf[TextView]
      interfaceFiltersHeader.setText(Html.fromHtml(XResource.getString(context, "block_interfacefilter_title").getOrElse("interface filters")))
      // options
      val optionsHeader = view.findViewById(XResource.getId(context, "nodata_header_option")).asInstanceOf[TextView]
      optionsHeader.setText(Html.fromHtml(XResource.getString(context, "block_option_title").getOrElse("options")))
      // serviceEnvironment
      val serviceEnvironmentHeader = view.findViewById(XResource.getId(context, "nodata_header_serviceenvironment")).asInstanceOf[TextView]
      serviceEnvironmentHeader.setText(Html.fromHtml(XResource.getString(context, "block_serviceenvironment_title").getOrElse("environment")))
      // serviceSoftware
      val serviceSoftwareHeader = view.findViewById(XResource.getId(context, "nodata_header_servicesoftware")).asInstanceOf[TextView]
      serviceSoftwareHeader.setText(Html.fromHtml(XResource.getString(context, "block_components_title").getOrElse("components")))
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
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) = for {
    filterBlock <- TabContent.filterBlock
    optionBlock <- TabContent.optionBlock
    environmentBlock <- TabContent.environmentBlock
    componentBlock <- TabContent.componentBlock
  } menuInfo match {
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
  @Loggable
  override def onContextItemSelected(menuItem: MenuItem): Boolean = {
    for {
      filterBlock <- TabContent.filterBlock
      optionBlock <- TabContent.optionBlock
      environmentBlock <- TabContent.environmentBlock
      componentBlock <- TabContent.componentBlock
    } yield menuItem.getMenuInfo match {
      case info: AdapterContextMenuInfo =>
        if (getListView.getPositionForView(info.targetView) == -1)
          return false
        TabContent.adapter.getItem(info.position) match {
          case item: FilterBlock.Item =>
            filterBlock.onContextItemSelected(menuItem, item)
          case item: OptionBlock.Item =>
            optionBlock.onContextItemSelected(menuItem, item)
          case item: EnvironmentBlock.Item =>
            environmentBlock.onContextItemSelected(menuItem, item)
          case item: ComponentBlock.Item =>
            componentBlock.onContextItemSelected(menuItem, item)
          case id if id == XResource.getId(getSherlockActivity, "users_keys_menu") ||
            id == XResource.getId(getSherlockActivity, "users_details_menu") =>
            hackForIssue7139 = info
            true
          case item =>
            log.debug("skip unknown context menu item " + info)
            false
        }
      case null =>
        log.warn("ignore issue #7139 for menuItem \"" + menuItem.getTitle + "\"")
        false
      case info =>
        log.fatal("unsupported menu info for menuItem \"" + menuItem.getTitle + "\"")
        false
    }
  } getOrElse false
  @Loggable
  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) = for {
    filterBlock <- TabContent.filterBlock
    optionBlock <- TabContent.optionBlock
    environmentBlock <- TabContent.environmentBlock
    componentBlock <- TabContent.componentBlock
  } {
    Option(TabContent.adapter.getItem(position)).getOrElse(hackForIssue7139) match {
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
  /** TabContent fragment instance */
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