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

package org.digimead.digi.ctrl.sshd.session

import java.util.ArrayList
import java.util.Arrays

import scala.actors.Futures
import scala.actors.Futures.future
import scala.ref.WeakReference

import org.digimead.digi.lib.ctrl.AnyBase
import org.digimead.digi.lib.ctrl.ext.XResource
import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.ctrl.base.AppComponent
import org.digimead.digi.lib.ctrl.block.Block
import org.digimead.digi.lib.ctrl.block.Level
import org.digimead.digi.lib.ctrl.declaration.DIntent
import org.digimead.digi.lib.ctrl.declaration.DOption
import org.digimead.digi.lib.ctrl.declaration.DPreference
import org.digimead.digi.lib.ctrl.declaration.DTimeout
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.util.SyncVar

import com.commonsware.cwac.merge.MergeAdapter

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

class OptionBlock(context: Context) extends Logging {
  OptionBlock.block = Some(this)
  Futures.future { OptionBlock.updateItems }

  @Loggable
  def appendTo(adapter: MergeAdapter) {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    Option(OptionBlock.header).foreach(adapter.addView)
    Option(OptionBlock.adapter).foreach(adapter.addAdapter)
  }
  @Loggable
  def items = for (i <- 0 until OptionBlock.adapter.getCount) yield OptionBlock.adapter.getItem(i)
  @Loggable
  def onListItemClick(item: OptionBlock.Item) = item.view.get.foreach {
    view =>
      val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
      val lastState = checkbox.isChecked
      AnyBase.runOnUiThread { checkbox.setChecked(!lastState) }
      onOptionClick(item, lastState)
  }
  @Loggable
  def onOptionClick(item: OptionBlock.Item, lastState: Boolean) = item.option match {
    case _ =>
      Futures.future {
        val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
        val editor = pref.edit()
        editor.putBoolean(item.option.tag, !lastState)
        editor.commit()
        context.sendBroadcast(new Intent(DIntent.UpdateOption, Uri.parse("code://" + context.getPackageName + "/" + item.option)))
      }
  }
}

object OptionBlock extends Logging {
  /** OptionBlock instance */
  @volatile private var block: Option[OptionBlock] = None
  /** OptionBlock adapter */
  private[session] lazy val adapter = AppComponent.Context match {
    case Some(context) =>
      new OptionBlock.Adapter(context.getApplicationContext,
        XResource.getId(context, "element_option_list_item_multiple_choice", "layout"))
    case None =>
      log.fatal("lost ApplicationContext")
      null
  }
  /** OptionBlock header view */
  private lazy val header = AppComponent.Context match {
    case Some(context) =>
      val view = context.getApplicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].
        inflate(XResource.getId(context.getApplicationContext, "header", "layout"), null).asInstanceOf[TextView]
      view.setText(Html.fromHtml(XResource.getString(context, "block_option_title").getOrElse("options")))
      view
    case None =>
      log.fatal("lost ApplicationContext")
      null
  }
  private val items = Seq(Item(DOption.ConfirmConn.tag, DOption.ConfirmConn))
  //Item(DOption.WriteConnLog, DOption.WriteConnLog))

  private def updateItems = if (adapter.getItem(0) == null) {
    val newItems = SyncVar(items)
    AnyBase.runOnUiThread {
      adapter.setNotifyOnChange(false)
      adapter.clear
      newItems.get.foreach(adapter.add)
      adapter.setNotifyOnChange(true)
      adapter.notifyDataSetChanged
      newItems.unset()
    }
    if (!newItems.waitUnset(DTimeout.normal))
      log.fatal("UI thread hang")
  }

  case class Item(val value: String, val option: DOption.OptVal) extends Block.Item {
    override def toString() = value
    def getState(context: Context): Boolean = {
      val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
      pref.getBoolean(option.tag, option.default.asInstanceOf[Boolean])
    }
  }
  class Adapter(context: Context, textViewResourceId: Int)
    extends ArrayAdapter[Item](context, textViewResourceId, android.R.id.text1, new ArrayList[Item](Arrays.asList(null))) {
    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val item = getItem(position)
      if (item == null) {
        val view = new TextView(parent.getContext)
        view.setText(XResource.getString(context, "loading").getOrElse("loading..."))
        view
      } else
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
            Level.professional(view)
            item.view = new WeakReference(view)
            view
          case Some(view) =>
            view
        }
    }
  }
}