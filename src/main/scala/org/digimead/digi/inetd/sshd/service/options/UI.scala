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

package org.digimead.digi.ctrl.sshd.service.options

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.Common
import org.digimead.digi.ctrl.sshd.R

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class UI(ctx: org.digimead.digi.ctrl.sshd.service.TabActivity) {
  private val header = ctx.getLayoutInflater.inflate(R.layout.header, null).asInstanceOf[TextView]
  private val options = Seq(
    UI.Item(Common.Option.service_asroot, ctx))
  private val adapter = new UI.Adapter(ctx, options)
  header.setText(ctx.getString(R.string.service_options))
  ctx.adapter.addView(header)
  ctx.adapter.addAdapter(adapter)
  @Loggable
  def onListItemClick(item: UI.Item) = {
/*    val i = new Intent(Intent.ACTION_VIEW);
    i.setData(Uri.parse(item.link.toString()))
    ctx.startActivity(i)*/
  }
}

object UI {
  case class Item(option: Common.Option.OptVal, ctx: Context)
  class Adapter(ctx: org.digimead.digi.ctrl.sshd.service.TabActivity, values: Seq[Item])
    extends ArrayAdapter[Item](ctx, android.R.layout.simple_list_item_multiple_choice, android.R.id.text1) {
    values.foreach(add(_))
    ctx.lv.post(new Runnable { def run = notifyDataSetChanged() })
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val view = super.getView(position, convertView, parent)
      //      val item = getItem(position)
      //      val name = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
      //      val description = view.findViewById(android.R.id.text2).asInstanceOf[TextView]
      //      val version = view.findViewById(R.id.text3).asInstanceOf[TextView]
      //      name.setText(item.name)
      //    description.setText(item.description)
      //      version.setText(item.version)
      view
    }
  }
}
