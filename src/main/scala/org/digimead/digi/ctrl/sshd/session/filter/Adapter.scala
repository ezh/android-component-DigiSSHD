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

package org.digimead.digi.ctrl.sshd.session.filter

import scala.collection.mutable.HashSet
import scala.collection.mutable.ObservableSet
import scala.collection.mutable.SynchronizedSet
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDPreferences

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class Adapter(context: Context)
  extends ArrayAdapter[Adapter.Item](context, android.R.layout.simple_list_item_checked, android.R.id.text1) with Logging {
  private val fieldId = android.R.id.text1
  private val resource = android.R.layout.simple_list_item_checked
  private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
  private val separatorInclude = {
    val view = inflater.inflate(R.layout.header, null, false).asInstanceOf[TextView]
    view.setText(R.string.session_filter_include_separator)
    view
  }
  private val separatorDelete = {
    val view = inflater.inflate(R.layout.header, null, false).asInstanceOf[TextView]
    view.setText(R.string.session_filter_delete_separator)
    view
  }
  private[filter] val pendingToInclude = new HashSet[Adapter.Item] with ObservableSet[Adapter.Item] with SynchronizedSet[Adapter.Item]
  private[filter] val pendingToDelete = new HashSet[String] with ObservableSet[String] with SynchronizedSet[String]

  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    getItem(position) match {
      case Adapter.separatorToInclude =>
        separatorInclude
      case Adapter.separatorToDelete =>
        separatorDelete
      case item =>
        item.context.get.getOrElse({ item.context = new WeakReference(context) })
        createViewFromResource(position, convertView, parent, resource)
    }
  }
  def exists(item: Adapter.Item): Boolean = {
    for (i <- 0 until getCount) yield { if (getItem(i).value == item.value) return true }
    false
  }
  def exists(item: String): Boolean = {
    for (i <- 0 until getCount) yield { if (getItem(i).value == item) return true }
    false
  }
  override def isEnabled(position: Int) = getItem(position) match {
    case Adapter.separatorToInclude =>
      false
    case Adapter.separatorToDelete =>
      false
    case _ =>
      super.isEnabled(position)
  }
  def items = for (i <- 0 to getCount) yield getItem(i)
  @Loggable
  def update(lv: ListView, values: Seq[Adapter.Item]) = synchronized {
    assert(Thread.currentThread().getId() == AnyBase.uiThreadID, "def update(lv: ListView) available only from UI thread")
    val normal = values.filter(_.pending == None).sortBy(_.value)
    val toInclude = values.filter(_.pending == Some(true)).sortBy(_.value)
    val toDelete = values.filter(_.pending == Some(false)).sortBy(_.value)
    var id = 1 // position, base 1
    setNotifyOnChange(false)
    clear
    normal.foreach(v => {
      add(v)
      lv.setItemChecked(id, v.isActive) // <-- here
      id += 1
    })
    if (toInclude.nonEmpty) {
      // add header
      add(Adapter.separatorToInclude)
      id += 1
      // add items
      toInclude.foreach(v => {
        add(v)
        lv.setItemChecked(id, v.isActive)
        id += 1
      })
    }
    if (toDelete.nonEmpty) {
      // add header
      add(Adapter.separatorToDelete)
      id += 1
      // add items
      toDelete.foreach(v => {
        add(v)
        lv.setItemChecked(id, v.isActive)
        id += 1
      })
    }
    setNotifyOnChange(true)
    notifyDataSetChanged
  }
  def getFilters(context: Context, f: (Context) => Seq[(String, Boolean)], isActivityAllow: Boolean): Seq[Adapter.Item] =
    f(context).map(t =>
      Adapter.Item(t._1,
        if (pendingToDelete(t._1)) Some(false) else None)(t._2, isActivityAllow)) ++
      pendingToInclude
  private def createViewFromResource(position: Int, convertView: View, parent: ViewGroup, resource: Int): View = {
    val view = if (convertView == null || convertView == separatorInclude || convertView == separatorDelete)
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

object Adapter extends Logging {
  val separatorToInclude = Item(null, Some(true))(false, false)
  val separatorToDelete = Item(null, Some(false))(false, false)
  case class Item(val value: String, var pending: Option[Boolean] = None)(private var _isActive: Boolean, isActivityAllow: Boolean) {
    override def toString() =
      value
    var context = new WeakReference[Context](null)
    def isActive_=(x: Boolean) = synchronized {
      context.get.foreach {
        context =>
          pending match {
            case None =>
              log.debug("update state of normal item " + value + " " + x)
              (isActivityAllow, x) match {
                case (true, true) =>
                  SSHDPreferences.FilterConnection.Allow.enable(context, value)
                case (true, false) =>
                  SSHDPreferences.FilterConnection.Allow.disable(context, value)
                case (false, true) =>
                  SSHDPreferences.FilterConnection.Deny.enable(context, value)
                case (false, false) =>
                  SSHDPreferences.FilterConnection.Deny.disable(context, value)
              }
              AnyBase.runOnUiThread {
                Toast.makeText(context, context.getString(if (x)
                  R.string.session_filter_selected
                else
                  R.string.session_filter_deselected).format(value), Toast.LENGTH_SHORT).show()
              }
            case Some(true) =>
              log.debug("update state of toInclude item " + value + " " + x)
              AnyBase.runOnUiThread {
                Toast.makeText(context, context.getString(if (x)
                  R.string.session_filter_willbeselected
                else
                  R.string.session_filter_willbedeselected).format(value), Toast.LENGTH_SHORT).show()
              }
            case Some(false) =>
              log.debug("update state of toDelete item " + value + " " + x)
              (isActivityAllow, x) match {
                case (true, true) =>
                  SSHDPreferences.FilterConnection.Allow.enable(context, value)
                case (true, false) =>
                  SSHDPreferences.FilterConnection.Allow.disable(context, value)
                case (false, true) =>
                  SSHDPreferences.FilterConnection.Deny.enable(context, value)
                case (false, false) =>
                  SSHDPreferences.FilterConnection.Deny.disable(context, value)
              }
              AnyBase.runOnUiThread {
                Toast.makeText(context, context.getString(if (x)
                  R.string.session_filter_selected
                else
                  R.string.session_filter_deselected).format(value), Toast.LENGTH_SHORT).show()
              }
          }
      }
      _isActive = x
    }
    def isActive =
      _isActive
  }
}
