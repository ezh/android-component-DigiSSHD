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

package org.digimead.digi.ctrl.sshd.info

import java.util.ArrayList

import scala.actors.Futures.future
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConversions._

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppControl
import org.digimead.digi.ctrl.lib.block.Block
import org.digimead.digi.ctrl.lib.block.SupportBlock
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.service.FilterBlock
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.sshd.SSHDActivity
import org.digimead.digi.ctrl.lib.base.AppComponent

import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
import android.content.Context
import android.text.Html
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.ContextThemeWrapper

class InterfaceBlock(val context: Activity)(implicit @transient val dispatcher: Dispatcher) extends Block[InterfaceBlock.Item] with Logging {
  private lazy val header = context.getLayoutInflater.inflate(Android.getId(context, "header", "layout"), null).asInstanceOf[TextView]
  private lazy val adapter = new InterfaceBlock.Adapter(context)
  @volatile private var activeInterfaces: Option[Seq[String]] = None
  future { updateActiveInteraces(false) }
  def items = for (i <- 0 to adapter.getCount) yield adapter.getItem(i)
  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    header.setText(Html.fromHtml(Android.getString(context, "block_interface_title").getOrElse("interfaces")))
    mergeAdapter.addView(header)
    mergeAdapter.addAdapter(adapter)
  }
  @Loggable
  def onListItemClick(l: ListView, v: View, item: InterfaceBlock.Item) = { // leave UI thread
    future {
      SSHDActivity.activity.foreach {
        activity =>
          AppComponent.Inner.showDialogSafe[AlertDialog](activity, "info_interfaces_dialog", () => {
            val inflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
            val layout = inflater.inflate(R.layout.info_interfaces_dialog, null).asInstanceOf[ViewGroup]
            val dialog = new AlertDialog.Builder(new ContextThemeWrapper(activity, R.style.InterfacesLegendDialog)).
              setTitle(R.string.dialog_interfaces_legend_title).
              setIcon(R.drawable.ic_info).
              setView(layout).
              setPositiveButton(android.R.string.ok, null).
              create
            layout.setOnClickListener(new View.OnClickListener() {
              override def onClick(v: View) = dialog.dismiss()
            })
            dialog.setCanceledOnTouchOutside(true)
            dialog.show()
            dialog
          })
      }
    }
  }
  @Loggable
  def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo, item: SupportBlock.Item) {
    log.debug("create context menu for " + item.name)
  }
  @Loggable
  def onContextItemSelected(menuItem: MenuItem, item: SupportBlock.Item): Boolean = {
    false
  }
  @Loggable // Seq(InterfaceBlock.Item(null, null))
  def updateAdapter() = synchronized {
    for {
      activity <- TabActivity.activity
      madapter <- TabActivity.adapter
    } {
      context.runOnUiThread(new Runnable {
        def run = {
          adapter.setNotifyOnChange(false)
          adapter.clear
          val pref = context.getSharedPreferences(DPreference.FilterInterface, Context.MODE_PRIVATE)
          val acl = pref.getAll
          val interfaces = HashMap[String, Option[Boolean]](Common.listInterfaces.map(i => i -> None): _*)
          log.debug("available interfaces: " + interfaces.keys.mkString(", "))
          // stage 1: set unused interfaces to passive
          if (acl.isEmpty) {
            // all adapters enabled
            interfaces.keys.foreach(k => interfaces(k) = Some(false))
          } else if (acl.size == 1 && acl.containsKey(FilterBlock.ALL)) {
            // all adapters enabled/disabled
            if (pref.getBoolean(FilterBlock.ALL, false))
              interfaces.keys.foreach(k => interfaces(k) = Some(false))
          } else {
            // custom adapters state
            acl.keySet.toArray.map(_.asInstanceOf[String]).filter(_ != FilterBlock.ALL).sorted.foreach {
              aclMask =>
                // if aclMask enabled (true)
                if (pref.getBoolean(aclMask, false)) {
                  interfaces.filter(t => t._2 == None).keys.foreach(interface =>
                    if (Common.checkInterfaceInUse(interface, aclMask))
                      interfaces(interface) = Some(false))
                }
            }
          }
          log.debug("active interfaces: " + activeInterfaces.mkString(", "))
          // stage 2: set particular interfaces to active
          activeInterfaces.foreach(_ match {
            case n if n.isEmpty =>
              interfaces.foreach(i => if (i._2 == Some(false)) interfaces(i._1) = Some(true))
            case active =>
              interfaces.foreach(i => if (active.exists(_ == i._1)) interfaces(i._1) = Some(true))
          })
          interfaces.keys.toSeq.sorted.foreach {
            interface =>
              adapter.add(InterfaceBlock.Item(interface, interfaces(interface)))
          }
          log.trace("active interfaces updated")
          adapter.setNotifyOnChange(true)
          adapter.notifyDataSetChanged
          log.trace("exit from updateAdapter()")
        }
      })
    }
  }
  def updateActiveInteraces(allInterfacesArePassive: Boolean) = synchronized {
    if (allInterfacesArePassive) {
      activeInterfaces = None
      updateAdapter
    } else {
      activeInterfaces = AppControl.Inner.callListActiveInterfaces(context.getPackageName)()
      updateAdapter
    }
  }
}

object InterfaceBlock extends Logging {
  /*
   * status:
   * None - unused
   * Some(false) - passive
   * Some(true) - active
   */
  case class Item(val value: String, val status: Option[Boolean]) extends Block.Item {
    override def toString() = value
  }
  class Adapter(context: Activity,
    private val resource: Int = android.R.layout.simple_list_item_1,
    private val fieldId: Int = android.R.id.text1)
    extends ArrayAdapter[InterfaceBlock.Item](context, resource, fieldId, new ArrayList[Item](List(Item(null, null)))) {
    private lazy val icActive = context.getResources().getDrawable(R.drawable.ic_interface_active)
    private lazy val icPassive = context.getResources().getDrawable(R.drawable.ic_interface_passive)
    private lazy val icUnused = context.getResources().getDrawable(R.drawable.ic_interface_unused)
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val view = super.getView(position, convertView, parent)
      val text = view.asInstanceOf[TextView]
      val item = getItem(position)
      text.setCompoundDrawablePadding(10)
      item match {
        case InterfaceBlock.Item(null, null) =>
          text.setText(context.getString(R.string.pending))
        case InterfaceBlock.Item(_, Some(true)) =>
          text.setCompoundDrawablesWithIntrinsicBounds(icActive, null, null, null)
        case InterfaceBlock.Item(_, Some(false)) =>
          text.setCompoundDrawablesWithIntrinsicBounds(icPassive, null, null, null)
        case InterfaceBlock.Item(_, None) =>
          text.setCompoundDrawablesWithIntrinsicBounds(icUnused, null, null, null)
      }
      view.setBackgroundDrawable(Block.Resources.noviceDrawable)
      view
    }
  }
}
