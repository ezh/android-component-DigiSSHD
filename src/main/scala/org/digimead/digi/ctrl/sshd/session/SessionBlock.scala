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

import java.net.InetAddress

import scala.actors.Futures.future
import scala.actors.threadpool.AtomicInteger
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.block.Block
import org.digimead.digi.ctrl.lib.declaration.DConnection
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.declaration.DControlProvider
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.info.ComponentInfo
import org.digimead.digi.ctrl.lib.info.ExecutableInfo
import org.digimead.digi.ctrl.lib.info.UserInfo
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.SyncVar
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R

import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class SessionBlock(val context: Activity) extends Block[SessionBlock.Item] with Logging {
  implicit def weakActivity2Activity(a: WeakReference[Activity]): Activity = a.get.get
  private val header = context.getLayoutInflater.inflate(R.layout.session_header, null).asInstanceOf[LinearLayout]
  private[session] lazy val adapter = {
    val result: SyncVar[SessionAdapter] = new SyncVar
    context.runOnUiThread(new Runnable { def run = result.set(new SessionAdapter(context, R.layout.session_item)) })
    result.get(DTimeout.long).getOrElse({ log.fatal("unable to create SessionAdapter"); null })
  }
  private lazy val inflater = LayoutInflater.from(context)
  private var disconnectButton = new WeakReference[Button](null)
  private val updateInProgressLock = new AtomicInteger(0)
  SessionBlock.block = new WeakReference(this)

  def items = adapter.item.values.toSeq
  def appendTo(adapter: MergeAdapter) {
    val headerTitle = header.findViewById(android.R.id.title).asInstanceOf[TextView]
    headerTitle.setText(Html.fromHtml(Android.getString(context, "block_session_title").getOrElse("sessions")))
    header.findViewById(android.R.id.custom).setBackgroundDrawable(Block.Resources.intermediateDrawable)
    adapter.addView(header)
    adapter.addAdapter(this.adapter)
    val footer = inflater.inflate(Android.getId(context, "session_footer", "layout"), null)
    adapter.addView(footer)
    disconnectButton = new WeakReference(footer.findViewById(Android.getId(context, "session_footer_disconnect_all")).asInstanceOf[Button])
    disconnectButton.get.foreach(_.setOnTouchListener(new View.OnTouchListener {
      def onTouch(v: View, event: MotionEvent): Boolean = {
        if (event.getAction() == MotionEvent.ACTION_DOWN)
          future {
            TabActivity.activity.foreach {
              activity =>
                IAmMumble("disconnect all sessions")
                AppComponent.Inner.showDialogSafe(activity, "TabActivity.Dialog.SessionDisconnectAll", TabActivity.Dialog.SessionDisconnectAll)
            }
          }
        false
      }
    }))
    header.findViewById(android.R.id.custom).setVisibility(View.VISIBLE)
    disconnectButton.get.foreach(_.setEnabled(false))
  }
  @Loggable
  override def onListItemClick(l: ListView, v: View, item: SessionBlock.Item) = TabActivity.activity.foreach {
    activity =>
      IAmMumble("disconnect session " + item)
      val bundle = new Bundle
      bundle.putString("componentPackage", item.component.componentPackage)
      bundle.putInt("processID", item.processID)
      bundle.putInt("connectionID", item.connection.connectionID)
      AppComponent.Inner.showDialogSafe(activity, "TabActivity.Dialog.SessionDisconnect", TabActivity.Dialog.SessionDisconnect, bundle)
  }
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
        val text = Android.getString(context, "session_context_menu_unavailable_for_unknown_source").
          getOrElse("optional actions unavailable for session from unknown source")
        context.runOnUiThread(new Runnable { def run = Toast.makeText(context, text, DConstant.toastTimeout).show() })
        return
    }
    log.debug("create context menu for " + item.connection.connectionID + " with IP " + ip)
    val propAllow = context.getSharedPreferences(DPreference.FilterConnectionAllow, Context.MODE_PRIVATE)
    val propDeny = context.getSharedPreferences(DPreference.FilterConnectionDeny, Context.MODE_PRIVATE)
    menu.setHeaderTitle(ip)
    menu.setHeaderIcon(Android.getId(context, "ic_launcher", "drawable"))
    if (!propAllow.contains(ip))
      menu.add(Menu.NONE, Android.getId(context, "session_always_allow"), 1,
        Android.getString(context, "session_always_allow").getOrElse("Allow %1$s").format(ip))
    if (!propDeny.contains(ip))
      menu.add(Menu.NONE, Android.getId(context, "session_always_deny"), 3,
        Android.getString(context, "session_always_deny").getOrElse("Deny %1$s").format(ip))
    ip.split("""\.""") match {
      case Array(ip1, ip2, ip3, ip4) =>
        val acl = ip1 + "." + ip2 + "." + ip3 + ".*"
        if (!propAllow.contains(acl))
          menu.add(Menu.NONE, Android.getId(context, "session_always_allow_net"), 2,
            Android.getString(context, "session_always_allow_net").getOrElse("Allow %1$s").format(acl))
        if (!propDeny.contains(acl))
          menu.add(Menu.NONE, Android.getId(context, "session_always_deny_net"), 4,
            Android.getString(context, "session_always_deny_net").getOrElse("Deny %1$s").format(acl))
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
    }).getOrElse(Android.getString(context, "unknown_source").getOrElse("unknown source"))
    val propAllow = context.getSharedPreferences(DPreference.FilterConnectionAllow, Context.MODE_PRIVATE)
    val propDeny = context.getSharedPreferences(DPreference.FilterConnectionDeny, Context.MODE_PRIVATE)
    menuItem.getItemId match {
      case id if id == Android.getId(context, "session_always_allow") =>
        if (!propAllow.contains(ip)) {
          val msg = Android.getString(context, "session_filter_add_allow").getOrElse("add allow filter %1$s").format(ip)
          IAmMumble(msg)
          val editor = propAllow.edit
          editor.putBoolean(ip, true)
          editor.commit
          Toast.makeText(context, msg, DConstant.toastTimeout).show()
          FilterBlock.updateFilterItem
        } else
          log.warn("allow filter already exists " + ip)
        true
      case id if id == Android.getId(context, "session_always_deny") =>
        if (!propDeny.contains(ip)) {
          val msg = Android.getString(context, "session_filter_add_deny").getOrElse("add deny filter %1$s").format(ip)
          IAmMumble(msg)
          val editor = propDeny.edit
          editor.putBoolean(ip, true)
          editor.commit
          Toast.makeText(context, msg, DConstant.toastTimeout).show()
          FilterBlock.updateFilterItem
        } else
          log.warn("deny filter already exists " + ip)
        true
      case id if id == Android.getId(context, "session_always_allow_net") =>
        ip.split("""\.""") match {
          case Array(ip1, ip2, ip3, ip4) =>
            val acl = ip1 + "." + ip2 + "." + ip3 + ".*"
            if (!propAllow.contains(acl)) {
              val msg = Android.getString(context, "session_filter_add_allow").getOrElse("add allow filter %1$s").format(acl)
              IAmMumble(msg)
              val editor = propAllow.edit
              editor.putBoolean(acl, true)
              editor.commit
              Toast.makeText(context, msg, DConstant.toastTimeout).show()
              FilterBlock.updateFilterItem
            } else
              log.warn("allow filter already exists " + acl)
          case _ =>
            log.warn("allow filter source ip incorrect " + ip)
        }
        true
      case id if id == Android.getId(context, "session_always_deny_net") =>
        ip.split("""\.""") match {
          case Array(ip1, ip2, ip3, ip4) =>
            val acl = ip1 + "." + ip2 + "." + ip3 + ".*"
            if (!propDeny.contains(acl)) {
              val msg = Android.getString(context, "session_filter_add_deny").getOrElse("add deny filter %1$s").format(acl)
              IAmMumble(msg)
              val editor = propDeny.edit
              editor.putBoolean(acl, true)
              editor.commit
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
  }
}

object SessionBlock extends Logging {
  @volatile protected var block = new WeakReference[SessionBlock](null)

  @Loggable
  def updateCursor(): Unit =
    future { block.get.foreach(_.updateCursor()) }

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
        val source = ip.getOrElse(Android.getString(title.getContext, "unknown_source").getOrElse("unknown source"))
        val text = user match {
          case Some(user) =>
            Android.getString(title.getContext, "session_title_user").getOrElse("<b>%1$s</b> from %2$s to %3$s").
              format(user.name, source, executable.name)
          case None =>
            Android.getString(title.getContext, "session_title_nouser").getOrElse("%1$s to %2$s").
              format(source, executable.name)
        }
        title.setText(Html.fromHtml(text))
    }
  }
}
