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

package org.digimead.digi.ctrl.sshd.session

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.block.Block
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.R

import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView

class FilterBlock(val context: Activity) extends Block[FilterBlock.Item] with Logging {
  private val header = context.getLayoutInflater.inflate(R.layout.header, null).asInstanceOf[TextView]
  val items = Seq(FilterBlock.Item())
  private lazy val adapter = new FilterBlock.Adapter(context, items)
  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = synchronized {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    header.setText(Html.fromHtml(Android.getString(context, "block_filter_title").getOrElse("connection filters")))
    mergeAdapter.addView(header)
    mergeAdapter.addAdapter(adapter)
  }
  @Loggable
  def onListItemClick(l: ListView, v: View, item: FilterBlock.Item) = {
  }
}

object FilterBlock extends Logging {
  case class Item() extends Block.Item
  class Adapter(context: Activity, data: Seq[Item])
    extends ArrayAdapter[Item](context, R.layout.session_filter_item, android.R.id.text1, data.toArray) {
    private var inflater: LayoutInflater = context.getLayoutInflater
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val item = getItem(position)
      item.view.get match {
        case None =>
          val view = inflater.inflate(R.layout.session_filter_item, null)
          view
        case Some(view) =>
          view
      }
    }
  }
}
