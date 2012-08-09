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

package org.digimead.digi.ctrl.sshd.service

import scala.actors.Futures
import scala.collection.JavaConversions._

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.sshd.R

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast

class FilterAddAdapter(context: Context)
  extends ArrayAdapter[String](context, android.R.layout.simple_expandable_list_item_1, android.R.id.text1) with Logging {
  private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
  lazy val separator = inflater.inflate(R.layout.header, null, false).asInstanceOf[TextView]
  @volatile private var availableFilters = Seq[(String, Boolean)]() // Value, isPending
  @volatile private var pendingFilters = Seq[String]()
  @volatile private var skipLongClickClick = -1
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
  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    if (getItem(position) == null) {
      separator
    } else
      createViewFromResource(position, convertView, parent, android.R.layout.simple_expandable_list_item_1)
  }
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
      val item = getItem(position - 1)
      if (item != null) {
        val pendingPos = pendingFilters.indexOf(item)
        val availablePos = availableFilters.indexWhere(_._1 == item)
        if (availablePos != -1 && pendingPos == -1) {
          log.debug("available item click at position " + availablePos)
          pendingFilters = pendingFilters :+ item
          availableFilters = availableFilters.updated(availablePos, (item, true))
          FilterAddAdapter.update(context)
          Toast.makeText(context, context.getString(R.string.service_filter_select).format(item), Toast.LENGTH_SHORT).show()
        } else if (pendingPos != -1) {
          log.debug("pending item click at position " + pendingPos)
          if (availablePos != -1)
            availableFilters = availableFilters.updated(availablePos, (item, false))
          val (l1, l2) = pendingFilters splitAt pendingPos
          pendingFilters = l1 ++ (l2 drop 1)
          FilterAddAdapter.update(context)
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
  @Loggable
  def submit() = synchronized {
    val pref = context.getSharedPreferences(DPreference.FilterInterface, Context.MODE_PRIVATE)
    val editor = pref.edit()
    setPending(Seq()).foreach(filter => editor.putBoolean(filter, true))
    editor.commit()
    AnyBase.runOnUiThread { update }
  }
  @Loggable
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

object FilterAddAdapter extends Logging {
  private[service] lazy val adapter: Option[FilterAddAdapter] = AppComponent.Context map {
    context =>
      Some(new FilterAddAdapter(context))
  } getOrElse { log.fatal("unable to create FilterAddAdaper"); None }
  log.debug("alive")

  @Loggable
  def update(context: Context) = adapter.foreach {
    adapter =>
      Futures.future {
        // get list of predefined filters - active filters 
        val actual = predefinedFilters.diff(savedFilters(context))
        // get list of actual filters - pending filters 
        adapter.availableFilters = actual.map(v => (v, adapter.pendingFilters.exists(_ == v)))
        AnyBase.runOnUiThread { adapter.update }
      }
  }
  @Loggable
  private def predefinedFilters(): Seq[String] = {
    log.debug("predefinedFilters(...)")
    Common.listInterfaces().map(entry => {
      val Array(interface, ip) = entry.split(":")
      if (ip == "0.0.0.0")
        Seq(interface + ":*.*.*.*")
      else
        Seq(entry, interface + ":*.*.*.*", "*:" + ip)
    }).flatten
  }
  @Loggable
  private[service] def savedFilters(context: Context) =
    context.getSharedPreferences(DPreference.FilterInterface, Context.MODE_PRIVATE).getAll().map(t => t._1).toSeq
}
