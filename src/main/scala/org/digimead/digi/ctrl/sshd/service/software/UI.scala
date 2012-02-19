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

package org.digimead.digi.ctrl.sshd.service.software

import java.net.URL
import scala.xml.XML
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.AppActivity
import org.digimead.digi.ctrl.sshd.R
import org.slf4j.LoggerFactory
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import org.digimead.digi.ctrl.lib.aop.Logging
import org.digimead.digi.ctrl.sshd.service.TabActivity

class UI(ctx: TabActivity) extends Logging {
  protected val log = Logging.getLogger(this)
  private val header = ctx.getLayoutInflater.inflate(R.layout.header, null).asInstanceOf[TextView]
  private val adapter = new UI.Adapter(ctx, getAppSeq)
  header.setText(ctx.getString(R.string.service_software))
  ctx.adapter.addView(header)
  ctx.adapter.addAdapter(adapter)
  ctx.lv.post(new Runnable {
    def run = {
      adapter.notifyDataSetChanged()
    }
  })
  @Loggable
  def onListItemClick(item: UI.Item) = {
    val i = new Intent(Intent.ACTION_VIEW);
    i.setData(Uri.parse(item.link.toString()))
    ctx.startActivity(i)
  }
  @Loggable
  def getAppSeq(): Seq[UI.Item] = AppActivity.Inner.flatMap(_.nativeManifest).map {
    nativeManifest =>
      try {
        (for (app <- nativeManifest \\ "application") yield {
          try {
            val item = UI.Item((app \ "name").text, (app \ "version").text, (app \ "description").text, new URL((app \ "link").text))
            log.debug("add item " + item)
            item
          } catch {
            case e =>
              log.error(e.getMessage(), e)
              null
          }
        }).filter(_ != null)
      } catch {
        case e =>
          log.error(e.getMessage(), e)
          Seq()
      }
  } getOrElse Seq()
}

object UI {
  case class Item(name: String, var version: String, description: String, link: URL)
  class Adapter(context: Context, values: () => Seq[Item])
    extends ArrayAdapter[Item](context, R.layout.service_software_row, android.R.id.text1) {
    values().foreach(add(_))
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val view = super.getView(position, convertView, parent)
      val item = getItem(position)
      val name = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
      val description = view.findViewById(android.R.id.text2).asInstanceOf[TextView]
      val version = view.findViewById(R.id.text3).asInstanceOf[TextView]
      name.setText(item.name)
      description.setText(item.description)
      version.setText(item.version)
      view
    }
    def notifyDataSetChanged(updateValues: Boolean) = synchronized {
      setNotifyOnChange(false)
      clear()
      values().foreach(add(_))
      setNotifyOnChange(true)
      super.notifyDataSetChanged()
    }
  }
}