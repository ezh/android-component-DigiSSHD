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

import org.digimead.digi.ctrl.sshd.R
import org.slf4j.LoggerFactory
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import org.digimead.digi.ctrl.lib.aop.Logging
import org.digimead.digi.ctrl.lib.declaration.DConstant

class FilterAddAdapter(context: FilterAddActivity, values: () => Seq[String],
  private val resource: Int = android.R.layout.simple_expandable_list_item_1,
  private val fieldId: Int = android.R.id.text1)
  extends ArrayAdapter[String](context, resource, fieldId) with Logging {
  private var skipLongClickClick = -1
  private var availableFilters = Seq[(String, Boolean)]() // Value, isPending
  private var pendingFilters = Seq[String]()
  private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
  val separator = inflater.inflate(R.layout.header, context.getListView(), false).asInstanceOf[TextView]
  separator.setText(R.string.selected_filters)
  notifyDataSetChanged()
  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    if (getItem(position) == null) {
      separator
    } else
      createViewFromResource(position, convertView, parent, resource)
  }
  override def isEnabled(position: Int) = getItem(position) != null
  override def notifyDataSetChanged() = notifyDataSetChanged(false)
  def notifyDataSetChanged(updateValues: Boolean) = synchronized {
    // update
    if (updateValues)
      availableFilters = values().map(v => (v, pendingFilters.exists(_ == v))) // TODO ! add saved
    // refresh
    setNotifyOnChange(false)
    clear()
    availableFilters.foreach(t => if (!t._2) add(t._1)) // except already pending
    add(null) // separator
    pendingFilters.foreach(add(_))
    setNotifyOnChange(true)
    super.notifyDataSetChanged()
  }
  def itemClick(position: Int) = synchronized {
    if (position != skipLongClickClick) {
      val item = getItem(position - 1)
      if (item != null) {
        val pendingPos = pendingFilters.indexOf(item)
        val availablePos = availableFilters.indexWhere(_._1 == item)
        if (availablePos != -1 && pendingPos == -1) {
          log.debug("available item click at position " + availablePos)
          pendingFilters = pendingFilters :+ item
          availableFilters = availableFilters.updated(availablePos, (item, true))
          notifyDataSetChanged()
          Toast.makeText(context, context.getString(R.string.service_filter_select).format(item), DConstant.toastTimeout).show()
        } else if (pendingPos != -1) {
          log.debug("pending item click at position " + pendingPos)
          if (availablePos != -1)
            availableFilters = availableFilters.updated(availablePos, (item, false))
          val (l1, l2) = pendingFilters splitAt pendingPos
          pendingFilters = l1 ++ (l2 drop 1)
          notifyDataSetChanged()
          Toast.makeText(context, context.getString(R.string.service_filter_remove).format(item), DConstant.toastTimeout).show()
        } else {
          log.error("unknown item click at position " + position)
        }
      }
    }
    skipLongClickClick = -1
  }
  def itemLongClick(position: Int) = synchronized {
    skipLongClickClick = position
  }
  def exists(item: String) = availableFilters.exists(_._1 == item) || pendingFilters.exists(_ == item)
  def addPending(item: String) {
    pendingFilters = pendingFilters :+ item
  }
  def getPending() = pendingFilters
  def setPending(newFilters: Seq[String]) {
    availableFilters = availableFilters.map(t => (t._1, newFilters.exists(_ == t._1)))
    pendingFilters = newFilters
  }
  /*
   * skip separator view from RecycleBin
   */
  private def createViewFromResource(position: Int, convertView: View, parent: ViewGroup, resource: Int): View = {
    val view = if (convertView == null || convertView == separator)
      inflater.inflate(resource, parent, false)
    else
      convertView

    val text = try {
      if (fieldId == 0)
        //  If no custom field is assigned, assume the whole resource is a TextView
        view.asInstanceOf[TextView]
      else
        //  Otherwise, find the TextView field within the layout
        view.findViewById(fieldId).asInstanceOf[TextView]
    } catch {
      case e: ClassCastException =>
        log.error("You must supply a resource ID for a TextView")
        throw new IllegalStateException(
          "ArrayAdapter requires the resource ID to be a TextView", e)
    }

    getItem(position) match {
      case item: CharSequence =>
        text.setText(item)
      case item =>
        text.setText(item.toString())
    }

    view
  }
}

