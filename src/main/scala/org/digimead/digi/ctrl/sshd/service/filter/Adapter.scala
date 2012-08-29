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

package org.digimead.digi.ctrl.sshd.service.filter

import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.R

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast

abstract class Adapter(context: Context)
  extends ArrayAdapter[String](context, android.R.layout.simple_expandable_list_item_1, android.R.id.text1) with Logging {
  protected val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
  protected lazy val separator = inflater.inflate(R.layout.header, null, false).asInstanceOf[TextView]
  @volatile protected var availableFilters = Seq[(String, Boolean)]() // Value, isPending
  @volatile protected var pendingFilters = Seq[String]()
  @volatile protected var skipLongClickClick = -1
  separator.setText(R.string.selected_filters)

  override def isEnabled(position: Int) = getItem(position) != null
  def contains(item: String) =
    availableFilters.exists(_._1 == item) || pendingFilters.contains(item)
  def addPending(item: String) = synchronized {
    pendingFilters = pendingFilters :+ item
  }
  def getPending(): Seq[String] = synchronized {
    pendingFilters
  }
  def setPending(newFilters: Seq[String]): Seq[String] = synchronized {
    val result = pendingFilters
    availableFilters = availableFilters.map(t => (t._1, newFilters.exists(_ == t._1)))
    pendingFilters = newFilters
    result
  }
  override def getView(position: Int, convertView: View, parent: ViewGroup): View =
    if (getItem(position) == null)
      separator
    else
      createViewFromResource(position, convertView, parent, android.R.layout.simple_expandable_list_item_1)
  private def createViewFromResource(position: Int, convertView: View, parent: ViewGroup, resource: Int): View = {
    val view = if (convertView == null || convertView == separator)
      inflater.inflate(resource, parent, false)
    else
      convertView

    val text = try {
      if (android.R.id.text1 == 0)
        //  If no custom field is assigned, assume the whole resource is a TextView
        view.asInstanceOf[TextView]
      else
        //  Otherwise, find the TextView field within the layout
        view.findViewById(android.R.id.text1).asInstanceOf[TextView]
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
  def onListItemClick(position: Int) = synchronized {
    if (position != skipLongClickClick) {
      val item = getItem(position)
      if (item != null) {
        val pendingPos = pendingFilters.indexOf(item)
        val availablePos = availableFilters.indexWhere(_._1 == item)
        if (availablePos != -1 && pendingPos == -1) {
          log.debug("available item click at position " + availablePos)
          pendingFilters = pendingFilters :+ item
          availableFilters = availableFilters.updated(availablePos, (item, true))
          update
          Toast.makeText(context, context.getString(R.string.service_filter_select).format(item), Toast.LENGTH_SHORT).show()
        } else if (pendingPos != -1) {
          log.debug("pending item click at position " + pendingPos)
          if (availablePos != -1)
            availableFilters = availableFilters.updated(availablePos, (item, false))
          val (l1, l2) = pendingFilters splitAt pendingPos
          pendingFilters = l1 ++ (l2 drop 1)
          update
          Toast.makeText(context, context.getString(R.string.service_filter_remove).format(item), Toast.LENGTH_SHORT).show()
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
  def update() {
    setNotifyOnChange(false)
    clear()
    availableFilters.foreach(t => if (!t._2) add(t._1)) // except already pending
    add(null) // separator
    pendingFilters.foreach(add)
    setNotifyOnChange(true)
    notifyDataSetChanged
  }
}
