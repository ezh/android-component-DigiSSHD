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

import java.util.ArrayList

import scala.Array.canBuildFrom
import scala.Option.option2Iterable
import scala.actors.Futures
import scala.collection.JavaConversions._
import scala.collection.mutable.HashMap

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.base.AppControl
import org.digimead.digi.ctrl.lib.block.Block
import org.digimead.digi.ctrl.lib.block.Level
import org.digimead.digi.ctrl.lib.block.SupportBlock
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.service.FilterBlock

import com.commonsware.cwac.merge.MergeAdapter

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.text.Html
import android.view.ContextMenu
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView

class InterfaceBlock(val context: Context)(implicit @transient val dispatcher: Dispatcher)
  extends Block[InterfaceBlock.Item] with Logging {
  Futures.future { InterfaceBlock.updateActiveInteraces(context, true) }

  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    Option(InterfaceBlock.header).foreach(mergeAdapter.addView)
    Option(InterfaceBlock.adapter).foreach(mergeAdapter.addAdapter)
  }
  def items = for (i <- 0 until InterfaceBlock.adapter.getCount) yield InterfaceBlock.adapter.getItem(i)
  @Loggable
  def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo, item: SupportBlock.Item) =
    log.debug("create context menu for " + item.name)
  @Loggable
  def onContextItemSelected(menuItem: MenuItem, item: SupportBlock.Item): Boolean = false
  @Loggable
  def onListItemClick(l: ListView, v: View, item: InterfaceBlock.Item) = for {
    fragment <- TabContent.fragment
    dialog <- InterfaceBlock.Dialog.legend
  } {
    if (fragment.isTopPanelAvailable) {
      val ft = fragment.getActivity.getSupportFragmentManager.beginTransaction
      ft.replace(R.id.main_topPanel, dialog)
      ft.commit()
    } else
      dialog.show(fragment.getActivity.getSupportFragmentManager, "dialog_info_interfaces")
  }
}

object InterfaceBlock extends Logging {
  /** InterfaceBlock adapter */
  private[info] lazy val adapter = AppComponent.Context match {
    case Some(context) =>
      new InterfaceBlock.Adapter(context.getApplicationContext)
    case None =>
      log.fatal("lost ApplicationContext")
      null
  }
  /** InterfaceBlock header view */
  private lazy val header = AppComponent.Context match {
    case Some(context) =>
      val view = context.getApplicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].
        inflate(XResource.getId(context.getApplicationContext, "header", "layout"), null).asInstanceOf[TextView]
      view.setText(Html.fromHtml(XResource.getString(context, "block_interface_title").getOrElse("interfaces")))
      view
    case None =>
      log.fatal("lost ApplicationContext")
      null
  }
  /** List of active interfaces */
  @volatile private var activeInterfaces: Option[Seq[String]] = None

  @Loggable
  private def updateAdapter() = synchronized {
    TabContent.fragment.foreach {
      fragment =>
        val context = fragment.getActivity
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
        log.trace("active interfaces updated")
        // update adapter
        AnyBase.runOnUiThread {
          adapter.setNotifyOnChange(false)
          adapter.clear
          interfaces.keys.toSeq.sorted.foreach {
            interface =>
              adapter.add(InterfaceBlock.Item(interface, interfaces(interface)))
          }
          adapter.setNotifyOnChange(true)
          adapter.notifyDataSetChanged
        }
    }
  }
  /**
   * query list of active interfaces from DigiControl
   */
  def updateActiveInteraces(context: Context, allInterfacesArePassive: Boolean) = synchronized {
    if (allInterfacesArePassive) {
      activeInterfaces = None
      updateAdapter
    } else {
      activeInterfaces = AppControl.Inner.callListActiveInterfaces(context.getPackageName)()
      updateAdapter
    }
  }
  /*
   * status:
   * None - unused
   * Some(false) - passive
   * Some(true) - active
   */
  case class Item(val value: String, val status: Option[Boolean]) extends Block.Item {
    override def toString() = value
  }
  class Adapter(context: Context,
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
          text.setText(XResource.getString(context, "loading").getOrElse("loading..."))
        case InterfaceBlock.Item(_, Some(true)) =>
          text.setCompoundDrawablesWithIntrinsicBounds(icActive, null, null, null)
        case InterfaceBlock.Item(_, Some(false)) =>
          text.setCompoundDrawablesWithIntrinsicBounds(icPassive, null, null, null)
        case InterfaceBlock.Item(_, None) =>
          text.setCompoundDrawablesWithIntrinsicBounds(icUnused, null, null, null)
      }
      Level.novice(view)
      view
    }
  }
  object Dialog {
    private[InterfaceBlock] lazy val legend = AppComponent.Context.map(context =>
      Fragment.instantiate(context.getApplicationContext, classOf[Legend].getName, null).asInstanceOf[DialogFragment])
    class Legend extends DialogFragment {
      override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) =
        inflater.inflate(R.layout.dialog_info_interfaces, null)
      override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
        val layout = onCreateView(getActivity.getLayoutInflater, null, null)
        val dialog = new AlertDialog.Builder(new ContextThemeWrapper(getActivity, R.style.InterfacesLegendDialog)).
          setTitle(R.string.dialog_interfaces_legend_title).
          setIcon(R.drawable.ic_info).
          setView(layout).
          setPositiveButton(android.R.string.ok, null).
          create
        layout.setOnClickListener(new View.OnClickListener() {
          override def onClick(v: View) = dialog.dismiss()
        })
        dialog.setCanceledOnTouchOutside(true)
        dialog
      }
    }
  }
}
