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

import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDActivity

import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
import android.app.ListActivity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class TabActivity extends ListActivity with Logging {
  private[service] lazy val lv = new WeakReference(getListView())
  private var interfaceRemoveButton: Button = null
  @Loggable
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.service)
    TabActivity.activity = Some(this)

    // prepare empty view
    // interfaceFilters
    val interfaceFiltersHeader = findViewById(Android.getId(this, "nodata_header_interfacefilter")).asInstanceOf[TextView]
    interfaceFiltersHeader.setText(Html.fromHtml(Android.getString(this, "block_interfacefilter_title").getOrElse("interface filters")))
    // options
    val optionsHeader = findViewById(Android.getId(this, "nodata_header_option")).asInstanceOf[TextView]
    optionsHeader.setText(Html.fromHtml(Android.getString(this, "block_option_title").getOrElse("options")))
    // serviceEnvironment
    val serviceEnvironmentHeader = findViewById(Android.getId(this, "nodata_header_serviceenvironment")).asInstanceOf[TextView]
    serviceEnvironmentHeader.setText(Html.fromHtml(Android.getString(this, "block_serviceenvironment_title").getOrElse("environment")))
    // serviceSoftware
    val serviceSoftwareHeader = findViewById(Android.getId(this, "nodata_header_servicesoftware")).asInstanceOf[TextView]
    serviceSoftwareHeader.setText(Html.fromHtml(Android.getString(this, "block_servicesoftware_title").getOrElse("software")))
    // prepare active view
    getListView.setChoiceMode(ListView.CHOICE_MODE_NONE)
    val lv = getListView()
    registerForContextMenu(lv)
    TabActivity.adapter.foreach(adapter => runOnUiThread(new Runnable { def run = setListAdapter(adapter) }))
  }
  @Loggable
  override protected def onListItemClick(l: ListView, v: View, position: Int, id: Long) = for {
    adapter <- TabActivity.adapter
    filterBlock <- TabActivity.filterBlock
    optionBlock <- TabActivity.optionBlock
    environmentBlock <- TabActivity.environmentBlock
    componentBlock <- TabActivity.componentBlock
  } {
    adapter.getItem(position) match {
      case filterItem: FilterBlock.Item =>
        TabActivity.filterBlock.foreach(_.onListItemClick(l, v, filterItem))
      case optionItem: OptionBlock.Item =>
        TabActivity.optionBlock.foreach(_.onListItemClick(l, v, optionItem))
      case componentItem: ComponentBlock.Item =>
        TabActivity.componentBlock.foreach(_.onListItemClick(l, v, componentItem))
      case item =>
        log.fatal("unknown item " + item + " at " + position)
    }
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) = for {
    adapter <- TabActivity.adapter
    filterBlock <- TabActivity.filterBlock
    optionBlock <- TabActivity.optionBlock
    environmentBlock <- TabActivity.environmentBlock
    componentBlock <- TabActivity.componentBlock
  } {
    super.onCreateContextMenu(menu, v, menuInfo)
    menuInfo match {
      case info: AdapterContextMenuInfo =>
        adapter.getItem(info.position) match {
          case item: FilterBlock.Item =>
            filterBlock.onCreateContextMenu(menu, v, menuInfo, item)
          case item: OptionBlock.Item =>
            optionBlock.onCreateContextMenu(menu, v, menuInfo, item)
          case item: EnvironmentBlock.Item =>
            environmentBlock.onCreateContextMenu(menu, v, menuInfo, item)
          case item: ComponentBlock.Item =>
            componentBlock.onCreateContextMenu(menu, v, menuInfo, item)
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
      filterBlock <- TabActivity.filterBlock
      optionBlock <- TabActivity.optionBlock
      environmentBlock <- TabActivity.environmentBlock
      componentBlock <- TabActivity.componentBlock
    } yield {
      val info = menuItem.getMenuInfo.asInstanceOf[AdapterContextMenuInfo]
      adapter.getItem(info.position) match {
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
  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = TabActivity.filterBlock.foreach {
    filterBlock =>
      if (resultCode == Activity.RESULT_OK && requestCode == TabActivity.FILTER_REQUEST) {
        // most of the time this broadcast spread before onResume
        sendBroadcast(new Intent(DIntent.UpdateInterfaceFilter, Uri.parse("code://" + getPackageName + "/")))
        filterBlock.updateAdapter
      }
  }
  @Loggable
  def onClickServiceFilterAdd(v: View) =
    startActivityForResult(new Intent(this, classOf[FilterAddActivity]), TabActivity.FILTER_REQUEST)
  @Loggable
  def onClickServiceFilterRemove(v: View) = TabActivity.filterBlock.foreach {
    filterBlock =>
      if (filterBlock.isEmpty)
        Toast.makeText(this, getString(R.string.service_filter_unable_remove), DConstant.toastTimeout).show()
      else
        startActivityForResult(new Intent(this, classOf[FilterRemoveActivity]), TabActivity.FILTER_REQUEST)
  }
  @Loggable
  def onClickServiceReinstall(v: View) = {
    IAmMumble("reinstall files/force prepare evironment")
    Toast.makeText(this, Android.getString(this, "reinstall").getOrElse("reinstall"), DConstant.toastTimeout).show()
    AppActivity.Inner ! AppActivity.Message.PrepareEnvironment(this, false, true, (success) =>
      runOnUiThread(new Runnable() {
        def run = if (success)
          Toast.makeText(TabActivity.this, Android.getString(TabActivity.this,
            "reinstall_complete").getOrElse("reinstall complete"), DConstant.toastTimeout).show()
      }))
  }
  @Loggable
  def onClickServiceReset(v: View) = {
    IAmMumble("reset settings")
  }
}

object TabActivity extends Logging {
  @volatile private[service] var activity: Option[TabActivity] = None
  @volatile private[service] var adapter: Option[MergeAdapter] = None
  @volatile private var filterBlock: Option[FilterBlock] = None
  @volatile private var optionBlock: Option[OptionBlock] = None
  @volatile private var environmentBlock: Option[EnvironmentBlock] = None
  @volatile private var componentBlock: Option[ComponentBlock] = None
  val FILTER_REQUEST = 10000
  val DIALOG_FILTER_REMOVE_ID = 0
  def addLazyInit = AppActivity.LazyInit("initialize service adapter") {
    SSHDActivity.activity match {
      case Some(activity) =>
        adapter = Some(new MergeAdapter())
        filterBlock = Some(new FilterBlock(activity))
        optionBlock = Some(new OptionBlock(activity))
        environmentBlock = Some(new EnvironmentBlock(activity))
        componentBlock = Some(new ComponentBlock(activity))
        for {
          adapter <- adapter
          filterBlock <- filterBlock
          optionBlock <- optionBlock
          environmentBlock <- environmentBlock
          componentBlock <- componentBlock
        } {
          filterBlock appendTo (adapter)
          optionBlock appendTo (adapter)
          environmentBlock appendTo (adapter)
          componentBlock appendTo (adapter)
          TabActivity.activity.foreach(ctx => ctx.runOnUiThread(new Runnable { def run = ctx.setListAdapter(adapter) }))
        }
      case None =>
        log.fatal("lost SSHDActivity context")
    }
  }
  def getActivity() = activity
}
