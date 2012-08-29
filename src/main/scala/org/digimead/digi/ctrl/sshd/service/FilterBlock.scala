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

import java.util.ArrayList
import java.util.Arrays

import scala.Array.canBuildFrom
import scala.actors.Futures
import scala.collection.JavaConversions._
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.block.Block
import org.digimead.digi.ctrl.lib.block.Level
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.util.SyncVar
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.service.filter.AddFragment
import org.digimead.digi.ctrl.sshd.service.filter.RemoveFragment

import com.commonsware.cwac.merge.MergeAdapter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class FilterBlock(val context: Context)(implicit @transient val dispatcher: Dispatcher) extends Block[FilterBlock.Item] with Logging {
  FilterBlock.block = Some(this)
  Futures.future { FilterBlock.updateItems(context) }

  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = synchronized {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    Option(FilterBlock.header).foreach(mergeAdapter.addView)
    Option(FilterBlock.adapter).foreach(mergeAdapter.addAdapter)
  }
  @Loggable
  def items = for (i <- 0 until FilterBlock.adapter.getCount) yield FilterBlock.adapter.getItem(i)
  @Loggable
  def onListItemClick(l: ListView, v: View, item: FilterBlock.Item) = {
    item.state = !item.state
    AnyBase.runOnUiThread {
      if (item.value == FilterBlock.ALL) {
        if (item.state)
          Toast.makeText(context, context.getString(R.string.service_filter_enabled_all).format(item), DConstant.toastTimeout).show()
        else
          Toast.makeText(context, context.getString(R.string.service_filter_disabled_all).format(item), DConstant.toastTimeout).show()
      } else {
        if (item.state)
          Toast.makeText(context, context.getString(R.string.service_filter_enabled).format(item), DConstant.toastTimeout).show()
        else
          Toast.makeText(context, context.getString(R.string.service_filter_disabled).format(item), DConstant.toastTimeout).show()
      }
      item.view.get.foreach(_.asInstanceOf[CheckedTextView].setChecked(item.state))
      // TODO change pref
      context.sendBroadcast(new Intent(DIntent.UpdateInterfaceFilter, Uri.parse("code://" + context.getPackageName + "/")))
    }
  }
  @Loggable
  def updateAdapter() = synchronized {
    TabContent.fragment.foreach {
      fragment =>
        AnyBase.runOnUiThread {
          FilterBlock.adapter.setNotifyOnChange(false)
          FilterBlock.adapter.clear
          val pref = context.getSharedPreferences(DPreference.FilterInterface, Context.MODE_PRIVATE)
          val acl = pref.getAll
          if (acl.isEmpty)
            FilterBlock.adapter.add(FilterBlock.Item(FilterBlock.ALL, None, new WeakReference(context)))
          else if (acl.size == 1 && acl.containsKey(FilterBlock.ALL))
            FilterBlock.adapter.add(FilterBlock.Item(FilterBlock.ALL, Some(pref.getBoolean(FilterBlock.ALL, false)), new WeakReference(context)))
          else
            acl.keySet.toArray.map(_.asInstanceOf[String]).filter(_ != FilterBlock.ALL).sorted.foreach {
              aclMask =>
                FilterBlock.adapter.add(FilterBlock.Item(aclMask, Some(pref.getBoolean(aclMask, false)), new WeakReference(context)))
            }
          FilterBlock.adapter.setNotifyOnChange(true)
          FilterBlock.adapter.notifyDataSetChanged
        }
    }
  }
  def isEmpty() = synchronized { FilterBlock.adapter.getCount == 1 && FilterBlock.adapter.getItem(0).value == FilterBlock.ALL }
  @Loggable
  def onClickServiceFilterAdd(v: View) = AddFragment.show
  @Loggable
  def onClickServiceFilterRemove(v: View) = Futures.future {
    if (FilterBlock.listFilters(context).isEmpty)
      AnyBase.runOnUiThread {
        Toast.makeText(context, XResource.getString(context, "service_filter_unable_remove").
          getOrElse("unable to remove last filter"), Toast.LENGTH_SHORT).show()
      }
    else
      RemoveFragment.show
  }
}

object FilterBlock extends Logging {
  /** FilterBlock instance */
  @volatile private var block: Option[FilterBlock] = None
  /** FilterBlock adapter */
  private[service] lazy val adapter = AppComponent.Context match {
    case Some(context) =>
      new FilterBlock.Adapter(context)
    case None =>
      log.fatal("lost ApplicationContext")
      null
  }
  /** FilterBlock header view */
  private lazy val header = AppComponent.Context match {
    case Some(context) =>
      val view = context.getApplicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].
        inflate(XResource.getId(context.getApplicationContext, "element_service_filter_header", "layout"), null).asInstanceOf[LinearLayout]
      val headerTitle = view.findViewById(android.R.id.title).asInstanceOf[TextView]
      headerTitle.setText(Html.fromHtml(XResource.getString(context, "block_filter_title").getOrElse("interface filters")))
      Level.professional(view.findViewById(android.R.id.custom))
      val onClickServiceFilterAddButton = view.findViewById(R.id.service_filter_add_button).asInstanceOf[Button]
      onClickServiceFilterAddButton.setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View) = FilterBlock.block.foreach(_.onClickServiceFilterAdd(v))
      })
      val onClickServiceFilterRemoveButton = view.findViewById(R.id.service_filter_remove_button).asInstanceOf[Button]
      onClickServiceFilterRemoveButton.setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View) = FilterBlock.block.foreach(_.onClickServiceFilterRemove(v))
      })
      view
    case None =>
      log.fatal("lost ApplicationContext")
      null
  }
  val ALL = "*:*.*.*.*"

  @Loggable(result = false)
  private[service] def listFilters(context: Context): Seq[String] =
    context.getSharedPreferences(DPreference.FilterInterface, Context.MODE_PRIVATE).getAll().map(t => t._1).toSeq
  private[service] def updateItems(context: Context) = {
    val pref = context.getSharedPreferences(DPreference.FilterInterface, Context.MODE_PRIVATE)
    val acl = pref.getAll
    val newItems = SyncVar(
      if (acl.isEmpty)
        Array(FilterBlock.Item(FilterBlock.ALL, None, new WeakReference(context)))
      else if (acl.size == 1 && acl.containsKey(FilterBlock.ALL))
        Array(FilterBlock.Item(FilterBlock.ALL, Some(pref.getBoolean(FilterBlock.ALL, false)), new WeakReference(context)))
      else
        acl.keySet.toArray.map(_.asInstanceOf[String]).filter(_ != FilterBlock.ALL).sorted.map(aclMask =>
          FilterBlock.Item(aclMask, Some(pref.getBoolean(aclMask, false)), new WeakReference(context))))
    AnyBase.runOnUiThread {
      adapter.setNotifyOnChange(false)
      adapter.clear
      newItems.get.foreach(adapter.add)
      adapter.setNotifyOnChange(true)
      adapter.notifyDataSetChanged
      newItems.unset()
    }
    if (!newItems.waitUnset(DTimeout.normal))
      log.fatal("UI thread hang")
  }

  case class Item(val value: String, var _state: Option[Boolean], context: WeakReference[Context]) extends Block.Item {
    override def toString() =
      if (value != ALL)
        value
      else
        XResource.getString(context, "allow_all").getOrElse("allow all")
    def state: Boolean = synchronized {
      if (_state == None) {
        context.get.foreach {
          context =>
            val pref = context.getSharedPreferences(DPreference.FilterInterface, Context.MODE_PRIVATE)
            _state = Some(if (value == ALL) pref.getBoolean(FilterBlock.ALL, true) else pref.getBoolean(value, false))
        }
      }
      _state.getOrElse(false)
    }
    def state_=(newState: Boolean): Unit = synchronized {
      if (Some(newState) != _state) {
        _state = Some(newState)
        context.get.foreach {
          context =>
            val pref = context.getSharedPreferences(DPreference.FilterInterface, Context.MODE_PRIVATE)
            val editor = pref.edit()
            editor.putBoolean(value, newState)
            editor.commit()
        }
      }
    }
  }
  class Adapter(context: Context)
    extends ArrayAdapter[Item](context, android.R.layout.simple_list_item_checked, android.R.id.text1, new ArrayList[Item](Arrays.asList(null))) {
    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val item = getItem(position)
      if (item == null) {
        val view = new TextView(parent.getContext)
        view.setText(XResource.getString(context, "loading").getOrElse("loading..."))
        view
      } else
        item.view.get match {
          case None =>
            val view = inflater.inflate(android.R.layout.simple_list_item_checked, null).asInstanceOf[CheckedTextView]
            view.setText(item.toString)
            view.setChecked(item.state)
            item.view = new WeakReference(view)
            Level.professional(view)
            view
          case Some(view) =>
            view
        }
    }
  }
}
