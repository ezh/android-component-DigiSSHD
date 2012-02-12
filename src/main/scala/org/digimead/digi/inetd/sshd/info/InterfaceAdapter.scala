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

package org.digimead.digi.ctrl.sshd.info

import scala.collection.JavaConversions._

import org.digimead.digi.ctrl.sshd.R

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

class InterfaceAdapter(context: Context, values: () => Seq[TabActivity.InterfaceItem],
  private val resource: Int = android.R.layout.simple_list_item_1,
  private val fieldId: Int = android.R.id.text1)
  extends ArrayAdapter[TabActivity.InterfaceItem](context, resource, fieldId) {
  private lazy val icActive = context.getResources().getDrawable(R.drawable.ic_button_plus)
  private lazy val icPassive = context.getResources().getDrawable(R.drawable.ic_tab_settings_comm_selected)
  private lazy val icUnused = context.getResources().getDrawable(R.drawable.ic_tab_settings_info_unselected)
  values().foreach(add(_))
  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    val view = super.getView(position, convertView, parent)
    val text = view.asInstanceOf[TextView]
    val item = getItem(position)
    text.setCompoundDrawablePadding(10)
    item match {
      case TabActivity.InterfaceItem(null, null) =>
        text.setText(context.getString(R.string.pending))
      case TabActivity.InterfaceItem(_, Some(true)) =>
        text.setCompoundDrawablesWithIntrinsicBounds(icActive, null, null, null)
      case TabActivity.InterfaceItem(_, Some(false)) =>
        text.setCompoundDrawablesWithIntrinsicBounds(icPassive, null, null, null)
      case TabActivity.InterfaceItem(_, None) =>
        text.setCompoundDrawablesWithIntrinsicBounds(icUnused, null, null, null)
    }
    view
  }
  override def notifyDataSetChanged() = notifyDataSetChanged(false)
  def notifyDataSetChanged(updateValues: Boolean) = synchronized {
    if (updateValues) {
      setNotifyOnChange(false)
      clear()
      values().foreach(add(_))
      setNotifyOnChange(true)
    }
    super.notifyDataSetChanged()
  }
}

