/*
 * DigiSSHD - DigiINETD component for Android Platform
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

package org.digimead.digi.inetd.sshd.service

import scala.collection.JavaConversions._

import org.digimead.digi.inetd.sshd.R

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

class InterfaceAdapter(context: Context, textViewResourceId: Int, values: Seq[TabActivity.InterfaceItem])
  extends ArrayAdapter[TabActivity.InterfaceItem](context, textViewResourceId, values) {
  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
    val rowView = inflater.inflate(R.layout.service_interface_row, parent, false)
    val textView = rowView.findViewById(R.id.label).asInstanceOf[TextView]
    val imageView = rowView.findViewById(R.id.icon).asInstanceOf[ImageView]
    val item = values(position)
/*    if (item.value == null) {
      imageView.setVisibility(View.GONE)
      textView.setText(context.getString(R.string.all))
    } else {
      imageView.setImageResource(R.drawable.ic_on)
      textView.setText(item.value)
    }*/
    rowView
  }
}

