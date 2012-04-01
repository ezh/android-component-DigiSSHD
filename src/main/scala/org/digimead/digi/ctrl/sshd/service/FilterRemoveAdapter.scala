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

import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.R

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast

class FilterRemoveAdapter(context: FilterRemoveActivity, values: Seq[FilterRemoveActivity.FilterItem],
  private val resource: Int = android.R.layout.simple_list_item_1,
  private val fieldId: Int = android.R.id.text1)
  extends ArrayAdapter[String](context, resource, fieldId) with Logging {
  private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
  private var separatorPos = values.length
  private val separator = inflater.inflate(R.layout.header, context.getListView(), false).asInstanceOf[TextView]
  separator.setText(R.string.selected_filters)
  values.foreach(v => add(v.value))
  add(null) // separator
  context.runOnUiThread(new Runnable { def run = notifyDataSetChanged() })
  def itemClick(position: Int) = synchronized {
    def update() {
      clear()
      values.foreach(v => if (!v.pending) add(v.value))
      add(null) // separator
      values.foreach(v => if (v.pending) add(v.value))
      notifyDataSetChanged()
    }
    if (position < separatorPos) {
      log.debug("available item click at position " + position)
      val want = position + 1
      var filtered = 0
      val selected = values.indexWhere(v => {
        if (v.pending == false)
          filtered += 1
        filtered == want
      })
      values(selected).pending = true
      separatorPos -= 1
      update()
      Toast.makeText(context, context.getString(R.string.service_filter_select).format(values(selected).value), DConstant.toastTimeout).show()
    } else if (position > separatorPos) {
      log.debug("pending item click at position " + position)
      val want = position - separatorPos
      var filtered = 0
      val selected = values.indexWhere(v => {
        if (v.pending == true)
          filtered += 1
        filtered == want
      })
      values(selected).pending = false
      separatorPos += 1
      update()
      Toast.makeText(context, context.getString(R.string.service_filter_select).format(values(selected).value), DConstant.toastTimeout).show()
    }
  }
  def getPending() = values.filter(_.pending == true)
  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    if (getItem(position) == null) {
      separator
    } else
      createViewFromResource(position, convertView, parent, resource)
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