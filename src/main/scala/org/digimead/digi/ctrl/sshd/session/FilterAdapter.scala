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
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package org.digimead.digi.ctrl.sshd.session

import scala.actors.Futures.future
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.R

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast

class FilterAdapter(context: FilterActivity, values: () => Seq[FilterAdapter.Item],
  private val resource: Int = android.R.layout.simple_list_item_checked,
  private val fieldId: Int = android.R.id.text1)
  extends ArrayAdapter[FilterAdapter.Item](context, resource, fieldId) with Logging {
  private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
  val separatorInclude = inflater.inflate(R.layout.header, context.getListView(), false).asInstanceOf[TextView]
  val separatorDelete = inflater.inflate(R.layout.header, context.getListView(), false).asInstanceOf[TextView]
  separatorInclude.setText(R.string.session_filter_include_separator)
  separatorDelete.setText(R.string.session_filter_delete_separator)

  future { updateAdapter }
  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    getItem(position) match {
      case FilterAdapter.separatorToInclude =>
        separatorInclude
      case FilterAdapter.separatorToDelete =>
        separatorDelete
      case item =>
        item.context.get.getOrElse({ item.context = new WeakReference(context) })
        createViewFromResource(position, convertView, parent, resource)
    }
  }
  @Loggable
  def updateAdapter() = synchronized {
    val lv = context.getListView
    var id = 1 // position, base 1
    // TODO rewrite
    val normal = values().filter(_.pending == None).sortBy(_.value)
    val toInclude = values().filter(_.pending == Some(true)).sortBy(_.value)
    val toDelete = values().filter(_.pending == Some(false)).sortBy(_.value)
    setNotifyOnChange(false)
    // ! Only the original thread that created a view hierarchy can touch its views.
    context.runOnUiThread(new Runnable {
      def run {
        clear
        normal.foreach(v => {
          add(v)
          lv.setItemChecked(id, v.isActive) // <-- here
          id += 1
        })
        if (toInclude.nonEmpty) {
          // add header
          add(FilterAdapter.separatorToInclude)
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
          add(FilterAdapter.separatorToDelete)
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
    })
  }
  def exists(item: FilterAdapter.Item): Boolean = {
    for (i <- 0 until getCount) yield { if (getItem(i).value == item.value) return true }
    false
  }
  def exists(item: String): Boolean = {
    for (i <- 0 until getCount) yield { if (getItem(i).value == item) return true }
    false
  }
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
  override def isEnabled(position: Int) = getItem(position) match {
    case FilterAdapter.separatorToInclude =>
      false
    case FilterAdapter.separatorToDelete =>
      false
    case _ =>
      super.isEnabled(position)
  }
}

object FilterAdapter extends Logging {
  val separatorToInclude = Item(null, Some(true))(false, false)
  val separatorToDelete = Item(null, Some(false))(false, false)
  case class Item(val value: String, var pending: Option[Boolean] = None)(private var _isActive: Boolean, isActivityAllow: Boolean) {
    private val prefFilter = if (isActivityAllow) DPreference.FilterConnectionAllow else DPreference.FilterConnectionDeny
    override def toString() =
      value
    var context = new WeakReference[FilterActivity](null)
    def isActive_=(x: Boolean) = {
      context.get.foreach {
        context =>
          pending match {
            case None =>
              log.debug("update state of normal item " + value + " " + x)
              val pref = context.getSharedPreferences(prefFilter, Context.MODE_PRIVATE)
              val editor = pref.edit()
              editor.putBoolean(value, x)
              editor.commit()
              context.runOnUiThread(new Runnable {
                def run =
                  Toast.makeText(context, context.getString(if (x)
                    R.string.session_filter_selected
                  else
                    R.string.session_filter_deselected).format(value), DConstant.toastTimeout).show()
              })
            case Some(true) =>
              log.debug("update state of toInclude item " + value + " " + x)
              context.runOnUiThread(new Runnable {
                def run =
                  Toast.makeText(context, context.getString(if (x)
                    R.string.session_filter_willbeselected
                  else
                    R.string.session_filter_willbedeselected).format(value), DConstant.toastTimeout).show()
              })
            case Some(false) =>
              log.debug("update state of toDelete item " + value + " " + x)
              val pref = context.getSharedPreferences(prefFilter, Context.MODE_PRIVATE)
              val editor = pref.edit()
              editor.putBoolean(value, x)
              editor.commit()
              context.runOnUiThread(new Runnable {
                def run =
                  Toast.makeText(context, context.getString(if (x)
                    R.string.session_filter_selected
                  else
                    R.string.session_filter_deselected).format(value), DConstant.toastTimeout).show()
              })
          }
      }
      _isActive = x
    }
    def isActive =
      _isActive
  }
}