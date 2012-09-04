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
import scala.ref.WeakReference

import org.digimead.digi.lib.ctrl.AnyBase
import org.digimead.digi.lib.ctrl.ext.SafeDialog
import org.digimead.digi.lib.ctrl.ext.XAPI
import org.digimead.digi.lib.ctrl.ext.XDialog
import org.digimead.digi.lib.ctrl.ext.XDialog.dialog2string
import org.digimead.digi.lib.ctrl.ext.XResource
import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.ctrl.base.AppComponent
import org.digimead.digi.lib.ctrl.block.Block
import org.digimead.digi.lib.ctrl.block.Level
import org.digimead.digi.lib.ctrl.declaration.DOption
import org.digimead.digi.lib.ctrl.declaration.DPreference
import org.digimead.digi.lib.ctrl.declaration.DTimeout
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.ctrl.message.IAmMumble
import org.digimead.digi.lib.util.SyncVar
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDPreferences
import org.digimead.digi.ctrl.sshd.SSHDResource
import org.digimead.digi.ctrl.sshd.ext.SSHDAlertDialog
import org.digimead.digi.ctrl.sshd.session.filter.AllowFragment
import org.digimead.digi.ctrl.sshd.session.filter.DenyFragment

import com.commonsware.cwac.merge.MergeAdapter

import android.content.Context
import android.content.Intent
import android.support.v4.app.FragmentActivity
import android.text.Html
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView

class FilterBlock(val context: Context) extends Block[FilterBlock.Item] with Logging {
  FilterBlock.block = Some(this)
  Futures.future { FilterBlock.updateItems }

  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = synchronized {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    Option(FilterBlock.header).foreach(mergeAdapter.addView)
    Option(FilterBlock.adapter).foreach(mergeAdapter.addAdapter)
  }
  @Loggable
  def items = for (i <- 0 until FilterBlock.adapter.getCount) yield FilterBlock.adapter.getItem(i)
  @Loggable
  def onListItemClick(l: ListView, v: View, item: FilterBlock.Item) = {}
  @Loggable
  def onClickButton(v: View) = for {
    fragment <- TabContent.fragment
    dialog <- SSHDResource.serviceSelectPort
  } if (!dialog.isShowing)
    FilterBlock.Dialog.showSelectACLOrder(fragment.getSherlockActivity)
  @Loggable
  def onClickLeftPart(v: View) =
    if (items.head.isFilterADA)
      AllowFragment.show()
    else
      DenyFragment.show()
  @Loggable
  def onClickRightPart(v: View) =
    if (items.head.isFilterADA)
      DenyFragment.show()
    else
      AllowFragment.show()
  @Loggable
  def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) =
    items.head.updateUI
}

object FilterBlock extends Logging {
  /** FilterBlock instance */
  @volatile private var block: Option[FilterBlock] = None
  /** FilterBlock adapter */
  private[session] lazy val adapter = AppComponent.Context match {
    case Some(context) =>
      val adapter = new FilterBlock.Adapter(context.getApplicationContext)
      // add adapter listener for event SSHDPreferences.FilterConnection.*.Changed
      SSHDPreferences.FilterConnection.Allow.subscribe(new SSHDPreferences.FilterConnection.Allow.Sub {
        def notify(pub: SSHDPreferences.FilterConnection.Allow.Pub,
          event: SSHDPreferences.FilterConnection.Changed.type) =
          if (!adapter.isEmpty()) adapter.getItem(0).updateUI
      })
      SSHDPreferences.FilterConnection.Deny.subscribe(new SSHDPreferences.FilterConnection.Deny.Sub {
        def notify(pub: SSHDPreferences.FilterConnection.Deny.Pub,
          event: SSHDPreferences.FilterConnection.Changed.type) =
          if (!adapter.isEmpty()) adapter.getItem(0).updateUI
      })
      adapter
    case None =>
      log.fatal("lost ApplicationContext")
      null
  }
  /** FilterBlock header view */
  private lazy val header = AppComponent.Context match {
    case Some(context) =>
      val view = context.getApplicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].
        inflate(XResource.getId(context.getApplicationContext, "header", "layout"), null).asInstanceOf[TextView]
      view.setText(Html.fromHtml(XResource.getString(context, "block_filter_title").getOrElse("connection filters")))
      view
    case None =>
      log.fatal("lost ApplicationContext")
      null
  }
  private lazy val items = Seq(FilterBlock.Item())
  lazy val iconADA = AppComponent.Context.map(_.getResources.getDrawable(R.drawable.ic_session_acl_devider_ada))
  lazy val iconDAD = AppComponent.Context.map(_.getResources.getDrawable(R.drawable.ic_session_acl_devider_dad))
  lazy val FILTER_REQUEST_ALLOW = AppComponent.Context.map(XResource.getId(_, "filter_request_allow")).getOrElse(0)
  lazy val FILTER_REQUEST_DENY = AppComponent.Context.map(XResource.getId(_, "filter_request_deny")).getOrElse(0)

  @Loggable
  def updateFilterItem() =
    block.foreach(_.items.head.updateUI)
  @Loggable
  def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) =
    block.foreach(_.onActivityResult(requestCode, resultCode, data))
  private def updateItems(): Unit = if (adapter.getItem(0) == null) {
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
    } Futures.future {
      val allowAll = SSHDPreferences.FilterConnection.Allow.get(view.getContext)
      val activeAllow = allowAll.filter(_._2).size
      val totalAllow = allowAll.size
      val denyAll = SSHDPreferences.FilterConnection.Deny.get(view.getContext)
      val activeDeny = denyAll.filter(_._2).size
      val totalDeny = denyAll.size
      AnyBase.runOnUiThread {
        if (isFilterADA) {
          leftPart.setText(Html.fromHtml(XResource.getString(leftPart.getContext, "session_filter_allow_text").
            getOrElse("%1$d<font color='green'> : </font>%2$d").format(activeAllow, totalAllow)))
          rightPart.setText(Html.fromHtml(XResource.getString(leftPart.getContext, "session_filter_deny_text").
            getOrElse("%1$d<font color='red'> : </font>%2$d").format(activeDeny, totalDeny)))
        } else {
          leftPart.setText(Html.fromHtml(XResource.getString(leftPart.getContext, "session_filter_deny_text").
            getOrElse("%1$d<font color='red'> : </font>%2$d").format(activeDeny, totalDeny)))
          rightPart.setText(Html.fromHtml(XResource.getString(leftPart.getContext, "session_filter_allow_text").
            getOrElse("%1$d<font color='green'> : </font>%2$d").format(activeAllow, totalAllow)))
        }
      }
    }
  }
  class Adapter(context: Context)
    extends ArrayAdapter[Item](context, R.layout.element_session_filter_item, android.R.id.text1, new ArrayList[Item](Arrays.asList(null))) {
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
            val view = inflater.inflate(R.layout.element_session_filter_item, null)
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
              FilterBlock.iconADA.foreach(drawable => XAPI.setViewBackground(button, drawable))
            else
              FilterBlock.iconDAD.foreach(drawable => XAPI.setViewBackground(button, drawable))
            view
          case Some(view) =>
            view
        }
    }
  }
  object Dialog {
    @Loggable
    def showSelectACLOrder(activity: FragmentActivity) =
      SSHDResource.sessionSelectACLOrder.foreach(dialog =>
        SafeDialog(activity, dialog, () => dialog).
          transaction(SSHDPreferences.defaultTransaction(dialog)).show())

    class SelectACLOrder
      extends SSHDAlertDialog(Some(android.R.drawable.ic_dialog_alert)) {
      override protected lazy val positive = Some((android.R.string.ok, new XDialog.ButtonListener(new WeakReference(SelectACLOrder.this),
        Some((dialog: SelectACLOrder) => {
          defaultButtonCallback(dialog)
          if (!adapter.isEmpty()) {
            val item = adapter.getItem(0)
            for {
              container <- item.view.get
              button <- Option(container.findViewById(android.R.id.icon1))
            } {
              val context = dialog.getSherlockActivity()
              if (item.isFilterADA) {
                IAmMumble("change ACL order to Deny, Allow, Implicit Deny")
                FilterBlock.iconDAD.foreach(XAPI.setViewBackground(button, _))
                Futures.future {
                  val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
                  val editor = pref.edit()
                  editor.putBoolean(DOption.ACLConnection.tag, false)
                  editor.commit()
                }
              } else {
                IAmMumble("change ACL order to Allow, Deny, Implicit Allow")
                FilterBlock.iconADA.foreach(XAPI.setViewBackground(button, _))
                Futures.future {
                  val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
                  val editor = pref.edit()
                  editor.putBoolean(DOption.ACLConnection.tag, true)
                  editor.commit()
                }
              }
              item.updateUI
              button.invalidate()
              button.refreshDrawableState()
              button.getRootView().postInvalidate()
            }
          }
        }))))
      override protected lazy val negative = Some((android.R.string.cancel, new XDialog.ButtonListener(new WeakReference(SelectACLOrder.this),
        Some(defaultButtonCallback))))

      def tag = "dialog_session_acl_order"
      def title = Html.fromHtml(XResource.getString(getSherlockActivity, "session_acl_order_title").
        getOrElse("Change ACL rules order"))
      def message = Some(Html.fromHtml(if (!adapter.isEmpty() && adapter.getItem(0).isFilterADA())
        XResource.getString(getSherlockActivity, "session_acl_order_ada_message").
        getOrElse("Do you want to set ACL rules order to <font color=\'red\'>Deny</font>, " +
          "<font color=\'green\'>Allow</font>, <font color=\'red\'>Implicit Deny</font>?")
      else
        XResource.getString(getSherlockActivity, "session_acl_order_dad_message").
          getOrElse("Do you want to set ACL rules order to <font color=\'green\'>Allow</font>, " +
            "<font color=\'red\'>Deny</font>, <font color=\'green\'>Implicit Allow</font>?")))
      @Loggable
      override def onResume() {
        for {
          content <- contentView.get
          message <- message
        } content.asInstanceOf[TextView].setText(message)
        super.onResume
      }
    }
  }
}
