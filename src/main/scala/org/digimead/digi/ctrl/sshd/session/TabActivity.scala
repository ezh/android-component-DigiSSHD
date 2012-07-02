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

package org.digimead.digi.ctrl.sshd.session

import scala.ref.WeakReference
import scala.actors.Futures.future

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.base.AppControl
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDActivity

import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.ListActivity
import android.content.DialogInterface
import android.os.Bundle
import android.text.Html
import android.view.View.OnClickListener
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.Button
import android.widget.ListView
import android.widget.TextView

class TabActivity extends ListActivity with Logging {
  private lazy val lv = getListView()

  @Loggable
  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.session)
    TabActivity.activity = Some(this)

    // prepare empty view
    // filters
    val filtersHeader = findViewById(Android.getId(this, "nodata_header_connectionfilter")).asInstanceOf[TextView]
    filtersHeader.setText(Html.fromHtml(Android.getString(this, "block_connectionfilter_title").getOrElse("connection filters")))
    // options
    val optionsHeader = findViewById(Android.getId(this, "nodata_header_option")).asInstanceOf[TextView]
    optionsHeader.setText(Html.fromHtml(Android.getString(this, "block_option_title").getOrElse("options")))
    // sessions
    val sessionsHeader = findViewById(Android.getId(this, "nodata_header_session")).asInstanceOf[TextView]
    sessionsHeader.setText(Html.fromHtml(Android.getString(this, "block_session_title").getOrElse("sessions")))
    // prepare active view
    val lv = getListView()
    registerForContextMenu(lv)
    TabActivity.adapter.foreach(adapter => runOnUiThread(new Runnable { def run = setListAdapter(adapter) }))
  }
  @Loggable
  override def onCreateDialog(id: Int, data: Bundle): Dialog = id match {
    case id if id == TabActivity.Dialog.SessionDisconnect =>
      log.debug("create dialog " + TabActivity.Dialog.SessionDisconnect)
      new AlertDialog.Builder(this).
        setTitle(Android.getString(this, "session_disconnect_title").
          getOrElse("Disconnect")).
        setMessage(Android.getString(this, "session_disconnect_content").
          getOrElse("Disconnect session?")).
        setPositiveButton(android.R.string.ok, null).
        setNegativeButton(android.R.string.cancel, null).
        create()
    case id if id == TabActivity.Dialog.SessionDisconnectAll =>
      log.debug("create dialog " + TabActivity.Dialog.SessionDisconnect)
      new AlertDialog.Builder(this).
        setTitle(Android.getString(this, "session_disconnect_all_title").
          getOrElse("Disconnect All")).
        setMessage(Android.getString(this, "session_disconnect_all_content").
          getOrElse("Disconnect all sessions?")).
        setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener {
          def onClick(dialog: DialogInterface, which: Int) {
            TabActivity.sessionBlock.foreach(_.items.foreach(i =>
              future { AppControl.Inner.callDisconnect(i.component.componentPackage, i.processID, i.connection.connectionID) }))
          }
        }).
        setNegativeButton(android.R.string.cancel, null).
        create()
    case id =>
      log.fatal("unknown dialog id " + id)
      null
  }
  @Loggable
  override def onPrepareDialog(id: Int, dialog: Dialog, args: Bundle): Unit = {
    id match {
      case id if id == TabActivity.Dialog.SessionDisconnect =>
        log.debug("prepare TabActivity.Dialog.SessionDisconnect dialog")
        AppComponent.Inner.setDialogSafe(Some("TabActivity.Dialog.SessionDisconnect"), Some(dialog))
        val componentPackage = args.getString("componentPackage")
        val processID = args.getInt("processID")
        val connectionID = args.getInt("connectionID")
        val ok = dialog.findViewById(android.R.id.button1).asInstanceOf[Button]
        ok.setOnClickListener(new OnClickListener() {
          def onClick(v: View) = future {
            val wd = new WeakReference(dialog)
            future { AppControl.Inner.callDisconnect(componentPackage, processID, connectionID) }
            wd.get.foreach(_.dismiss)
          }
        })
      case id if id == TabActivity.Dialog.SessionDisconnectAll =>
        log.debug("prepare TabActivity.Dialog.SessionDisconnectAll dialog")
        AppComponent.Inner.setDialogSafe(Some("TabActivity.Dialog.SessionDisconnectAll"), Some(dialog))
      case id =>
        log.fatal("unknown dialog id " + id)
        AppComponent.Inner.setDialogSafe(None, None)
    }
  }
  @Loggable
  override protected def onListItemClick(l: ListView, v: View, position: Int, id: Long) = for {
    adapter <- TabActivity.adapter
    filterBlock <- TabActivity.filterBlock
    optionBlock <- TabActivity.optionBlock
    sessionBlock <- TabActivity.sessionBlock
  } {
    adapter.getItem(position) match {
      case item: OptionBlock.Item =>
        optionBlock.onListItemClick(item)
      case item: SessionBlock.Item =>
        sessionBlock.onListItemClick(l, v, item)
      case item: FilterBlock.Item =>
        filterBlock.onListItemClick(l, v, item)
      case item =>
        log.fatal("unsupported context menu item " + item)
    }
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) = for {
    adapter <- TabActivity.adapter
    filterBlock <- TabActivity.filterBlock
    optionBlock <- TabActivity.optionBlock
    sessionBlock <- TabActivity.sessionBlock
  } {
    super.onCreateContextMenu(menu, v, menuInfo)
    menuInfo match {
      case info: AdapterContextMenuInfo =>
        adapter.getItem(info.position) match {
          case item: FilterBlock.Item =>
          case item: OptionBlock.Item =>
          case item: SessionBlock.Item =>
            sessionBlock.onCreateContextMenu(menu, v, menuInfo, item)
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
      sessionBlock <- TabActivity.sessionBlock
    } yield {
      val info = menuItem.getMenuInfo.asInstanceOf[AdapterContextMenuInfo]
      adapter.getItem(info.position) match {
        case item: FilterBlock.Item =>
          false
        case item: OptionBlock.Item =>
          false
        case item: SessionBlock.Item =>
          sessionBlock.onContextItemSelected(menuItem, item)
        case item =>
          log.fatal("unknown item " + item)
          false
      }
    }
  } getOrElse false
}

object TabActivity extends Logging {
  @volatile private[session] var activity: Option[TabActivity] = None
  @volatile private var adapter: Option[MergeAdapter] = None
  @volatile private var filterBlock: Option[FilterBlock] = None
  @volatile private var optionBlock: Option[OptionBlock] = None
  @volatile private var sessionBlock: Option[SessionBlock] = None

  def addLazyInit = AppComponent.LazyInit("session.TabActivity initialize onCreate", 100) {
    SSHDActivity.activity match {
      case Some(activity) if activity.isInstanceOf[Activity] =>
        // initialize once from onCreate
        if (adapter == None) {
          adapter = Some(new MergeAdapter())
          filterBlock = Some(new FilterBlock(activity))
          optionBlock = Some(new OptionBlock(activity))
          sessionBlock = Some(new SessionBlock(activity))
          for {
            adapter <- adapter
            filterBlock <- filterBlock
            optionBlock <- optionBlock
            sessionBlock <- sessionBlock
          } {
            filterBlock appendTo (adapter)
            optionBlock appendTo (adapter)
            sessionBlock appendTo (adapter)
            TabActivity.activity.foreach(ctx => ctx.runOnUiThread(new Runnable { def run = ctx.setListAdapter(adapter) }))
          }
        }
        // initialize every time from onCreate
        (for {
          sessionBlock <- sessionBlock
        } yield {
          SessionBlock.updateCursor()
        }).getOrElse({ log.fatal("unable to update sessionBlock") })
      case context =>
        log.fatal("inappropriate SSHDActivity context " + context)
    }
  }
  def addLazyInitOnResume = AppComponent.LazyInit("service.TabActivity initialize onResume", 1000) {
    SessionBlock.updateCursor
  }

  object Dialog {
    val SessionDisconnect = AppComponent.Context.map(a => Android.getId(a, "session_disconnect")).getOrElse(0)
    val SessionDisconnectAll = AppComponent.Context.map(a => Android.getId(a, "session_disconnect_all")).getOrElse(0)
  }
}
