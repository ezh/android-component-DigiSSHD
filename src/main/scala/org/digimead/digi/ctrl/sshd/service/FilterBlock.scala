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

import java.util.ArrayList

import scala.Array.canBuildFrom
import scala.actors.Futures.future
import scala.collection.mutable.ArrayBuffer
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.block.Block
import org.digimead.digi.ctrl.lib.declaration.DMessage.Dispatcher
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.R

import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
import android.content.Context
import android.text.Html
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class FilterBlock(val context: Activity)(implicit @transient val dispatcher: Dispatcher) extends Block[FilterBlock.Item] with Logging {
  private lazy val header = context.getLayoutInflater.inflate(R.layout.service_interface_filters_header, null).asInstanceOf[LinearLayout]
  private lazy val adapter = new FilterBlock.Adapter(context)
  future { updateAdapter }
  def items = for (i <- 0 to adapter.getCount) yield adapter.getItem(i)
  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = synchronized {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    val headerTitle = header.findViewById(android.R.id.title).asInstanceOf[TextView]
    headerTitle.setText(Html.fromHtml(Android.getString(context, "block_filter_title").getOrElse("interface filters")))
    mergeAdapter.addView(header)
    mergeAdapter.addAdapter(adapter)
  }
  @Loggable
  def onListItemClick(l: ListView, v: View, item: FilterBlock.Item) = {
    item.state = !item.state
    context.runOnUiThread(new Runnable {
      def run = {
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
      }
    })
  }
  @Loggable
  def updateAdapter() = synchronized {
    for {
      activity <- TabActivity.activity
      madapter <- TabActivity.adapter
    } {
      context.runOnUiThread(new Runnable {
        def run = {
          adapter.setNotifyOnChange(false)
          adapter.clear
          val pref = context.getSharedPreferences(DPreference.FilterInterface, Context.MODE_WORLD_READABLE)
          val acl = pref.getAll
          if (acl.isEmpty)
            adapter.add(FilterBlock.Item(FilterBlock.ALL, None, new WeakReference(context)))
          else if (acl.size == 1 && acl.containsKey(FilterBlock.ALL))
            adapter.add(FilterBlock.Item(FilterBlock.ALL, Some(pref.getBoolean(FilterBlock.ALL, false)), new WeakReference(context)))
          else
            acl.keySet.toArray.map(_.asInstanceOf[String]).filter(_ != FilterBlock.ALL).sorted.foreach {
              aclMask =>
                adapter.add(FilterBlock.Item(aclMask, Some(pref.getBoolean(aclMask, false)), new WeakReference(context)))
            }
          adapter.setNotifyOnChange(true)
          adapter.notifyDataSetChanged
        }
      })
    }
  }
  def isEmpty() = synchronized { adapter.getCount == 1 && adapter.getItem(0).value == FilterBlock.ALL }
}

object FilterBlock extends Logging {
  val ALL = "*:*.*.*.*"
  case class Item(val value: String, var _state: Option[Boolean], context: WeakReference[Context]) extends Block.Item {
    override def toString() =
      if (value != ALL)
        value
      else
        Android.getString(context, "allow_all").getOrElse("allow all")
    def state: Boolean = synchronized {
      if (_state == None) {
        context.get.foreach {
          context =>
            val pref = context.getSharedPreferences(DPreference.FilterInterface, Context.MODE_WORLD_READABLE)
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
            val pref = context.getSharedPreferences(DPreference.FilterInterface, Context.MODE_WORLD_READABLE)
            val editor = pref.edit()
            editor.putBoolean(value, newState)
            editor.commit()
        }
      }
    }
  }
  class Adapter(context: Activity)
    extends ArrayAdapter[Item](context, android.R.layout.simple_list_item_checked, android.R.id.text1, new ArrayList[Item]()) {
    private var inflater: LayoutInflater = context.getLayoutInflater
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val item = getItem(position)
      item.view.get match {
        case None =>
          val view = inflater.inflate(android.R.layout.simple_list_item_checked, null).asInstanceOf[CheckedTextView]
          view.setText(item.toString)
          view.setChecked(item.state)
          item.view = new WeakReference(view)
          view
        case Some(view) =>
          view
      }
    }
  }
}
