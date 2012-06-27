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

import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.block.Block
import org.digimead.digi.ctrl.lib.block.Level
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDPreferences

import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.Html
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView

class FilterBlock(val context: Activity) extends Block[FilterBlock.Item] with Logging {
  private val header = context.getLayoutInflater.inflate(R.layout.header, null).asInstanceOf[TextView]
  val items = Seq(FilterBlock.Item())
  private lazy val adapter = new FilterBlock.Adapter(context, items)
  FilterBlock.block = Some(this)

  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = synchronized {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    header.setText(Html.fromHtml(Android.getString(context, "block_filter_title").getOrElse("connection filters")))
    mergeAdapter.addView(header)
    mergeAdapter.addAdapter(adapter)
  }
  @Loggable
  def onListItemClick(l: ListView, v: View, item: FilterBlock.Item) = {}
  @Loggable
  def onClickButton(v: View) = {
    val item = items.head
    if (item.isFilterADA) {
      IAmMumble("change ACL order to Deny, Allow, Implicit Deny")
      FilterBlock.iconDAD.foreach(v.setBackgroundDrawable)
      val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
      val editor = pref.edit()
      editor.putBoolean(DOption.ACLConnection.tag, false)
      editor.commit()
    } else {
      IAmMumble("change ACL order to Allow, Deny, Implicit Allow")
      FilterBlock.iconADA.foreach(v.setBackgroundDrawable)
      val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
      val editor = pref.edit()
      editor.putBoolean(DOption.ACLConnection.tag, true)
      editor.commit()
    }
    item.updateUI
    v.invalidate()
    v.refreshDrawableState()
    v.getRootView().postInvalidate()
  }
  @Loggable
  def onClickLeftPart(v: View) {
    val item = items.head
    if (item.isFilterADA) {
      log.debug("start FILTER_REQUEST_ALLOW activity with request code " + FilterBlock.FILTER_REQUEST_ALLOW)
      val intent = new Intent(context, classOf[FilterActivity])
      intent.putExtra("requestCode", FilterBlock.FILTER_REQUEST_ALLOW)
      context.startActivityForResult(intent, FilterBlock.FILTER_REQUEST_ALLOW)
    } else {
      log.debug("start FILTER_REQUEST_DENY activity with request code " + FilterBlock.FILTER_REQUEST_DENY)
      val intent = new Intent(context, classOf[FilterActivity])
      intent.putExtra("requestCode", FilterBlock.FILTER_REQUEST_DENY)
      context.startActivityForResult(intent, FilterBlock.FILTER_REQUEST_DENY)
    }
  }
  @Loggable
  def onClickRightPart(v: View) {
    val item = items.head
    if (item.isFilterADA) {
      log.debug("start FILTER_REQUEST_DENY activity with request code " + FilterBlock.FILTER_REQUEST_DENY)
      val intent = new Intent(context, classOf[FilterActivity])
      intent.putExtra("requestCode", FilterBlock.FILTER_REQUEST_DENY)
      context.startActivityForResult(intent, FilterBlock.FILTER_REQUEST_DENY)
    } else {
      log.debug("start FILTER_REQUEST_ALLOW activity with request code " + FilterBlock.FILTER_REQUEST_ALLOW)
      val intent = new Intent(context, classOf[FilterActivity])
      intent.putExtra("requestCode", FilterBlock.FILTER_REQUEST_ALLOW)
      context.startActivityForResult(intent, FilterBlock.FILTER_REQUEST_ALLOW)
    }
  }
  @Loggable
  def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) =
    items.head.updateUI
}

object FilterBlock extends Logging {
  @volatile private var block: Option[FilterBlock] = None
  lazy val iconADA = AppComponent.Context.map(_.getResources.getDrawable(R.drawable.ic_session_acl_devider_ada))
  lazy val iconDAD = AppComponent.Context.map(_.getResources.getDrawable(R.drawable.ic_session_acl_devider_dad))
  lazy val FILTER_REQUEST_ALLOW = AppComponent.Context.map(Android.getId(_, "filter_request_allow")).getOrElse(0)
  lazy val FILTER_REQUEST_DENY = AppComponent.Context.map(Android.getId(_, "filter_request_deny")).getOrElse(0)

  @Loggable
  def updateFilterItem() =
    block.foreach(_.items.head.updateUI)
  @Loggable
  def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) =
    block.foreach(_.onActivityResult(requestCode, resultCode, data))
  @Loggable
  def initialize(context: Context) =
    SSHDPreferences.FilterConnection.initialize(context)
  case class Item() extends Block.Item {
    var leftPart = new WeakReference[TextView](null)
    var rightPart = new WeakReference[TextView](null)
    def isFilterADA(): Boolean = view.get.map { // Allow, Deny, Allow
      view =>
        val pref = view.getContext.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
        pref.getBoolean(DOption.ACLConnection.tag, true)
    } getOrElse (true)
    // run only from UI thread
    def updateUI() = for {
      view <- view.get
      leftPart <- leftPart.get
      rightPart <- rightPart.get
    } {
      val allowAll = SSHDPreferences.FilterConnection.Allow.get(view.getContext)
      val activeAllow = allowAll.filter(_._2).size
      val totalAllow = allowAll.size
      val denyAll = SSHDPreferences.FilterConnection.Deny.get(view.getContext)
      val activeDeny = denyAll.filter(_._2).size
      val totalDeny = denyAll.size
      if (isFilterADA) {
        leftPart.setText(Html.fromHtml(Android.getString(leftPart.getContext, "session_filter_allow_text").
          getOrElse("%1$d<font color='green'> : </font>%2$d").format(activeAllow, totalAllow)))
        rightPart.setText(Html.fromHtml(Android.getString(leftPart.getContext, "session_filter_deny_text").
          getOrElse("%1$d<font color='red'> : </font>%2$d").format(activeDeny, totalDeny)))
      } else {
        leftPart.setText(Html.fromHtml(Android.getString(leftPart.getContext, "session_filter_deny_text").
          getOrElse("%1$d<font color='red'> : </font>%2$d").format(activeDeny, totalDeny)))
        rightPart.setText(Html.fromHtml(Android.getString(leftPart.getContext, "session_filter_allow_text").
          getOrElse("%1$d<font color='green'> : </font>%2$d").format(activeAllow, totalAllow)))
      }
    }
  }
  class Adapter(context: Activity, data: Seq[Item])
    extends ArrayAdapter[Item](context, R.layout.session_filter_item, android.R.id.text1, data.toArray) {
    private var inflater: LayoutInflater = context.getLayoutInflater
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val item = getItem(position)
      item.view.get match {
        case None =>
          val view = inflater.inflate(R.layout.session_filter_item, null)
          val leftPart = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
          val rightPart = view.findViewById(android.R.id.text2).asInstanceOf[TextView]
          val button = view.findViewById(android.R.id.icon1).asInstanceOf[ImageView]
          item.leftPart = new WeakReference(leftPart)
          item.rightPart = new WeakReference(rightPart)
          button.setOnTouchListener(new View.OnTouchListener {
            def onTouch(v: View, event: MotionEvent): Boolean = {
              if (event.getAction() == MotionEvent.ACTION_DOWN)
                block.foreach(_.onClickButton(v))
              false // no, it isn't
            }
          })
          leftPart.setOnTouchListener(new View.OnTouchListener {
            def onTouch(v: View, event: MotionEvent): Boolean = {
              if (event.getAction() == MotionEvent.ACTION_DOWN)
                block.foreach(_.onClickLeftPart(v))
              false // no, it isn't
            }
          })
          rightPart.setOnTouchListener(new View.OnTouchListener {
            def onTouch(v: View, event: MotionEvent): Boolean = {
              if (event.getAction() == MotionEvent.ACTION_DOWN)
                block.foreach(_.onClickRightPart(v))
              false // no, it isn't
            }
          })
          Level.professional(view)
          item.view = new WeakReference(view)
          item.updateUI
          if (item.isFilterADA)
            FilterBlock.iconADA.foreach(button.setBackgroundDrawable)
          else
            FilterBlock.iconDAD.foreach(button.setBackgroundDrawable)
          view
        case Some(view) =>
          view
      }
    }
  }
}
