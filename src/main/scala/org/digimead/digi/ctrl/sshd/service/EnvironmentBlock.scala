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

import android.app.Activity
import android.text.Html
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import android.widget.TextView
import com.commonsware.cwac.merge.MergeAdapter
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.block.Block
import org.digimead.digi.ctrl.lib.block.SupportBlock
import org.digimead.digi.ctrl.lib.declaration.DMessage.Dispatcher
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import java.net.URL
import org.digimead.digi.ctrl.lib.base.AppActivity
import android.content.Context
import android.widget.ArrayAdapter
import org.digimead.digi.ctrl.sshd.R
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.LinearLayout

// buy or not

class EnvironmentBlock(val context: Activity)(implicit @transient val dispatcher: Dispatcher) extends Block[EnvironmentBlock.Item] with Logging {
  private lazy val header = context.getLayoutInflater.inflate(R.layout.service_environment_header, null).asInstanceOf[LinearLayout]
  private lazy val adapter = new EnvironmentBlock.Adapter(context, android.R.layout.simple_list_item_1, Seq())
  def items = for(i <- 0 to adapter.getCount) yield adapter.getItem(i)
  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    val headerTitle = header.findViewById(android.R.id.title).asInstanceOf[TextView]
    headerTitle.setText(Html.fromHtml(Android.getString(context, "block_environment_title").getOrElse("environment")))
    mergeAdapter.addView(header)
    mergeAdapter.addAdapter(adapter)
  }
  @Loggable
  def onListItemClick(l: ListView, v: View, item: EnvironmentBlock.Item) = {

  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo, item: EnvironmentBlock.Item) {
    //log.debug("create context menu for " + item.name)
  }
  @Loggable
  override def onContextItemSelected(menuItem: MenuItem, item: EnvironmentBlock.Item): Boolean = {

    false
  }
}

object EnvironmentBlock {
  @volatile private var block: Option[EnvironmentBlock] = None
  case class Item(value: String, version: String, description: String, link: URL) extends Block.Item {
    override def toString() = value
  }
  class Adapter(context: Activity, textViewResourceId: Int, data: Seq[Item])
    extends ArrayAdapter[Item](context, textViewResourceId, android.R.id.text1, data.toArray) {
    private var inflater: LayoutInflater = context.getLayoutInflater
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val item = data(position)
      item.view.get match {
        case None =>
          val view = inflater.inflate(textViewResourceId, null)
          val text1 = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
          //val text2 = view.findViewById(android.R.id.text2).asInstanceOf[TextView]
          view
        case Some(view) =>
          view
      }
    }
  }
}
