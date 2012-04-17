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

import scala.actors.Futures.future
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.block.Block
import org.digimead.digi.ctrl.lib.declaration.DOption.OptVal.value2string_id
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.R

import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.TextView

class OptionBlock(context: Activity) extends Logging {
  private val header = context.getLayoutInflater.inflate(R.layout.header, null).asInstanceOf[TextView]
  private val items = Seq(
    OptionBlock.Item(DOption.ConfirmConn, DOption.ConfirmConn),
    OptionBlock.Item(DOption.WriteConnLog, DOption.WriteConnLog))
  private lazy val adapter = new OptionBlock.Adapter(context, Android.getId(context, "option_list_item_multiple_choice", "layout"), items)
  OptionBlock.block = Some(this)
  def appendTo(adapter: MergeAdapter) {
    header.setText(context.getString(R.string.comm_option_block))
    adapter.addView(header)
    adapter.addAdapter(this.adapter)
  }
  @Loggable
  def onListItemClick(item: OptionBlock.Item) = item.view.get.foreach {
    view =>
      val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
      val lastState = checkbox.isChecked
      context.runOnUiThread(new Runnable { def run = checkbox.setChecked(!lastState) })
      onOptionClick(item, lastState)
  }
  @Loggable
  def onOptionClick(item: OptionBlock.Item, lastState: Boolean) = item.option match {
    case _ =>
      val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_WORLD_READABLE)
      val editor = pref.edit()
      editor.putBoolean(item.option, !lastState)
      editor.commit()
      context.sendBroadcast(new Intent(DIntent.UpdateOption, Uri.parse("code://" + context.getPackageName + "/" + item.option)))
  }
}

object OptionBlock extends Logging {
  @volatile private var block: Option[OptionBlock] = None
  case class Item(val value: String, val option: DOption.OptVal) extends Block.Item {
    override def toString() = value
    def getState(context: Context): Boolean = {
      val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_WORLD_READABLE)
      pref.getBoolean(option, false)
    }
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
          val text2 = view.findViewById(android.R.id.text2).asInstanceOf[TextView]
          val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
          checkbox.setOnTouchListener(new View.OnTouchListener {
            def onTouch(v: View, event: MotionEvent): Boolean = {
              // don't want check for tap or TOOL_TYPE_
              val box = v.asInstanceOf[CheckBox]
              val lastState = box.isChecked()
              if (event.getAction() == MotionEvent.ACTION_DOWN) {
                box.setPressed(true)
                box.invalidate()
                box.refreshDrawableState()
                v.getRootView().postInvalidate()
                // apply immediately
                future { block.foreach(_.onOptionClick(item, lastState)) }
              } else {
                box.setChecked(!lastState)
                box.setPressed(false)
                box.invalidate()
                box.refreshDrawableState()
                v.getRootView().postInvalidate()
              }
              true // yes, it is
            }
          })
          checkbox.setFocusable(false)
          checkbox.setFocusableInTouchMode(false)
          checkbox.setChecked(item.getState(context))
          text2.setVisibility(View.VISIBLE)
          text1.setText(Html.fromHtml(item.option.name(context)))
          text2.setText(Html.fromHtml(item.option.description(context)))
          item.view = new WeakReference(view)
          view
        case Some(view) =>
          view
      }
    }
  }
}