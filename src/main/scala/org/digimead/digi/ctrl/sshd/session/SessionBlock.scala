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

package org.digimead.digi.ctrl.sshd.session

import java.net.InetAddress

import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.block.Block
import org.digimead.digi.ctrl.lib.block.Level
import org.digimead.digi.ctrl.lib.declaration.DConnection
import org.digimead.digi.ctrl.lib.info.ComponentInfo
import org.digimead.digi.ctrl.lib.info.ExecutableInfo
import org.digimead.digi.ctrl.lib.info.UserInfo
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R

import com.commonsware.cwac.merge.MergeAdapter

import android.content.Context
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

class SessionBlock(val context: Context) extends Block[SessionBlock.Item] with Logging {
  def items = SessionBlock.adapter.item.values.toSeq
  def appendTo(adapter: MergeAdapter) {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    Option(SessionBlock.header).foreach(adapter.addView)
    Option(SessionBlock.adapter).foreach(adapter.addAdapter)
  }
  @Loggable
  override def onListItemClick(l: ListView, v: View, item: SessionBlock.Item) = TabContent.fragment.foreach {
    fragment =>
      IAmMumble("disconnect session " + item)
      val bundle = new Bundle
      bundle.putString("componentPackage", item.component.componentPackage)
      bundle.putInt("processID", item.processID)
      bundle.putInt("connectionID", item.connection.connectionID)
    //      AppComponent.Inner.showDialogSafe(fragment.getActivity, "TabActivity.Dialog.SessionDisconnect", TabContent.Dialog.SessionDisconnect, bundle)
  }
  /*  

  private lazy val inflater = LayoutInflater.from(context)
  private var disconnectButton = new WeakReference[Button](null)
  private val updateInProgressLock = new AtomicInteger(0)
  SessionBlock.block = new WeakReference(this)

  def items = adapter.item.values.toSeq


  @Loggable
  private def updateCursor(): Unit = {
    if (updateInProgressLock.getAndIncrement() == 0) {
      log.debug("recreate session cursor")
      val cursor = context.getContentResolver().query(Uri.parse(DControlProvider.Uri.Session.toString), null, null, null, null)
      if (cursor == null) {
        log.warn("cursor from " + DControlProvider.Uri.Session + " unavailable")
        updateInProgressLock.set(0)
        return
      }
      context.runOnUiThread(new Runnable() {
        def run() {
          if (cursor.getCount == 0) {
            header.findViewById(android.R.id.custom).setVisibility(View.VISIBLE)
            disconnectButton.get.foreach(_.setEnabled(false))
          } else {
            header.findViewById(android.R.id.custom).setVisibility(View.GONE)
            disconnectButton.get.foreach(_.setEnabled(true))
          }
          adapter.changeCursor(cursor)
          adapter.notifyDataSetChanged
        }
      })
      if (updateInProgressLock.decrementAndGet != 0) {
        Thread.sleep(100)
        updateInProgressLock.set(0)
        updateCursor()
      } else
        updateInProgressLock.set(0)
    } else
      log.warn("update of session cursor already in progress, lock counter " + updateInProgressLock.get)
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo, item: SessionBlock.Item): Unit = {
    val ip = try {
      InetAddress.getByAddress(BigInt(item.connection.remoteIP).toByteArray).getHostAddress
    } catch {
      case e =>
        log.warn(e.getMessage)
        val text = XResource.getString(context, "session_context_menu_unavailable_for_unknown_source").
          getOrElse("optional actions unavailable for session from unknown source")
        context.runOnUiThread(new Runnable { def run = Toast.makeText(context, text, DConstant.toastTimeout).show() })
        return
    }
    log.debug("create context menu for " + item.connection.connectionID + " with IP " + ip)
    menu.setHeaderTitle(ip)
    menu.setHeaderIcon(XResource.getId(context, "ic_launcher", "drawable"))
    if (!SSHDPreferences.FilterConnection.Allow.contains(context, ip))
      menu.add(Menu.NONE, XResource.getId(context, "session_always_allow"), 1,
        XResource.getString(context, "session_always_allow").getOrElse("Allow %1$s").format(ip))
    if (!SSHDPreferences.FilterConnection.Deny.contains(context, ip))
      menu.add(Menu.NONE, XResource.getId(context, "session_always_deny"), 3,
        XResource.getString(context, "session_always_deny").getOrElse("Deny %1$s").format(ip))
    ip.split("""\.""") match {
      case Array(ip1, ip2, ip3, ip4) =>
        val acl = ip1 + "." + ip2 + "." + ip3 + ".*"
        if (!SSHDPreferences.FilterConnection.Allow.contains(context, acl))
          menu.add(Menu.NONE, XResource.getId(context, "session_always_allow_net"), 2,
            XResource.getString(context, "session_always_allow_net").getOrElse("Allow %1$s").format(acl))
        if (!SSHDPreferences.FilterConnection.Deny.contains(context, acl))
          menu.add(Menu.NONE, XResource.getId(context, "session_always_deny_net"), 4,
            XResource.getString(context, "session_always_deny_net").getOrElse("Deny %1$s").format(acl))
      case _ =>
    }
  }
  @Loggable
  override def onContextItemSelected(menuItem: MenuItem, item: SessionBlock.Item): Boolean = {
    val ip = (try {
      Some(InetAddress.getByAddress(BigInt(item.connection.remoteIP).toByteArray).getHostAddress)
    } catch {
      case e =>
        log.warn(e.getMessage)
        None
    }).getOrElse(XResource.getString(context, "unknown_source").getOrElse("unknown source"))
    menuItem.getItemId match {
      case id if id == XResource.getId(context, "session_always_allow") =>
        if (!SSHDPreferences.FilterConnection.Allow.contains(context, ip)) {
          val msg = XResource.getString(context, "session_filter_add_allow").getOrElse("add allow filter %1$s").format(ip)
          IAmMumble(msg)
          SSHDPreferences.FilterConnection.Allow.enable(context, ip)
          Toast.makeText(context, msg, DConstant.toastTimeout).show()
          FilterBlock.updateFilterItem
        } else
          log.warn("allow filter already exists " + ip)
        true
      case id if id == XResource.getId(context, "session_always_deny") =>
        if (!SSHDPreferences.FilterConnection.Deny.contains(context, ip)) {
          val msg = XResource.getString(context, "session_filter_add_deny").getOrElse("add deny filter %1$s").format(ip)
          IAmMumble(msg)
          SSHDPreferences.FilterConnection.Deny.enable(context, ip)
          Toast.makeText(context, msg, DConstant.toastTimeout).show()
          FilterBlock.updateFilterItem
        } else
          log.warn("deny filter already exists " + ip)
        true
      case id if id == XResource.getId(context, "session_always_allow_net") =>
        ip.split("""\.""") match {
          case Array(ip1, ip2, ip3, ip4) =>
            val acl = ip1 + "." + ip2 + "." + ip3 + ".*"
            if (!SSHDPreferences.FilterConnection.Allow.contains(context, acl)) {
              val msg = XResource.getString(context, "session_filter_add_allow").getOrElse("add allow filter %1$s").format(acl)
              IAmMumble(msg)
              SSHDPreferences.FilterConnection.Allow.enable(context, acl)
              Toast.makeText(context, msg, DConstant.toastTimeout).show()
              FilterBlock.updateFilterItem
            } else
              log.warn("allow filter already exists " + acl)
          case _ =>
            log.warn("allow filter source ip incorrect " + ip)
        }
        true
      case id if id == XResource.getId(context, "session_always_deny_net") =>
        ip.split("""\.""") match {
          case Array(ip1, ip2, ip3, ip4) =>
            val acl = ip1 + "." + ip2 + "." + ip3 + ".*"
            if (!SSHDPreferences.FilterConnection.Deny.contains(context, acl)) {
              val msg = XResource.getString(context, "session_filter_add_deny").getOrElse("add deny filter %1$s").format(acl)
              IAmMumble(msg)
              SSHDPreferences.FilterConnection.Deny.enable(context, acl)
              Toast.makeText(context, msg, DConstant.toastTimeout).show()
              FilterBlock.updateFilterItem
            } else
              log.warn("deny filter already exists " + acl)
          case _ =>
            log.warn("deny filter source ip incorrect " + ip)
        }
        true
      case item =>
        log.fatal("unknown item " + item)
        false
    }
  }*/
}

object SessionBlock extends Logging {
  @volatile protected var block = new WeakReference[SessionBlock](null)
  /** InterfaceBlock adapter */
  private[session] lazy val adapter = AppComponent.Context match {
    case Some(context) =>
      new SessionAdapter(context.getApplicationContext, XResource.getId(context, "element_session_item", "layout"))
    case None =>
      log.fatal("lost ApplicationContext")
      null
  }
  /** InterfaceBlock header view */
  private lazy val header = AppComponent.Context match {
    case Some(context) =>
      val view = context.getApplicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].
        inflate(XResource.getId(context.getApplicationContext, "element_session_header", "layout"), null).asInstanceOf[LinearLayout]
      val headerTitle = view.findViewById(android.R.id.title).asInstanceOf[TextView]
      headerTitle.setText(Html.fromHtml(XResource.getString(context, "block_session_title").getOrElse("sessions")))
      val emptyView = view.findViewById(android.R.id.empty)
      emptyView.findViewById(android.R.id.text1).asInstanceOf[TextView].setText(R.string.no_sessions)
      Level.intermediate(emptyView)
      emptyView.setVisibility(View.VISIBLE)
      view
    case None =>
      log.fatal("lost ApplicationContext")
      null
  }
  //  @Loggable
  //  def updateCursor(): Unit =
  //    future { block.get.foreach(_.updateCursor()) }

  class Item(val id: Int,
    val processID: Int,
    val component: ComponentInfo,
    val executable: ExecutableInfo,
    val connection: DConnection,
    @volatile var user: Option[UserInfo]) extends Block.Item {
    @volatile var durationField: WeakReference[TextView] = new WeakReference(null)
    @volatile var position: Option[Int] = None
    def updateTitle() = view.get.foreach {
      view =>
        val title = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
        val ip = try {
          Some(InetAddress.getByAddress(BigInt(connection.remoteIP).toByteArray).getHostAddress)
        } catch {
          case e =>
            log.warn(e.getMessage)
            None
        }
        val source = ip.getOrElse(XResource.getString(title.getContext, "unknown_source").getOrElse("unknown source"))
        val text = user match {
          case Some(user) =>
            XResource.getString(title.getContext, "session_title_user").getOrElse("<b>%1$s</b> from %2$s to %3$s").
              format(user.name, source, executable.name)
          case None =>
            XResource.getString(title.getContext, "session_title_nouser").getOrElse("%1$s to %2$s").
              format(source, executable.name)
        }
        title.setText(Html.fromHtml(text))
    }
  }
}
