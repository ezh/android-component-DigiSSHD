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

import scala.actors.Futures
import scala.collection.mutable.Undoable
import scala.collection.script.Message
import scala.ref.WeakReference
import scala.util.control.ControlThrowable

import org.digimead.digi.lib.ctrl.AnyBase
import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.ctrl.message.IAmMumble
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDPreferences
import org.digimead.digi.ctrl.sshd.ext.SSHDFragment
import org.digimead.digi.ctrl.sshd.session.TabContent

import com.actionbarsherlock.app.SherlockListFragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.Button
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

abstract class Fragment extends SherlockListFragment with SSHDFragment with Logging {
  @volatile private var headerListNonEmpty = new WeakReference[View](null)
  private lazy val headerListEmpty = new WeakReference(getSherlockActivity.
    findViewById(R.id.element_session_filter_empty_header))
  private lazy val footerApply = new WeakReference(getSherlockActivity.
    findViewById(R.id.session_filter_footer_apply).asInstanceOf[Button])
  private var savedTitle: CharSequence = ""
  protected val updateAdapterFunction: (Context) => Unit

  def onClickAddFilter(v: View): Unit
  def onClickApply(v: View): Unit
  @Loggable
  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_session_filter, container, false)
  protected def onPendingListChanged(adapter: Adapter, event: Message[_]) = {
    Futures.future {
      updateAdapterFunction(getSherlockActivity())
      AnyBase.runOnUiThread {
        log.debug("pending items changed: " + event)
        if (adapter.pendingToInclude.isEmpty && adapter.pendingToDelete.isEmpty)
          footerApply.get.foreach(_.setEnabled(false))
        else
          footerApply.get.foreach(_.setEnabled(true))
      }
    }
  }
  protected def onActivityCreated(adapter: Adapter, updateAdapter: (Context) => Unit, savedInstanceState: Bundle) = {
    super.onActivityCreated(savedInstanceState)
    val lv = getListView
    if (lv.getAdapter() != null)
      lv.setAdapter(null)
    lv.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE)
    val header = getSherlockActivity.getLayoutInflater.inflate(R.layout.element_session_filter_header, null)
    lv.addHeaderView(header)
    headerListNonEmpty = new WeakReference(header)
    lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() with Logging {
      @Loggable
      override def onItemLongClick(parent: AdapterView[_], view: View, position: Int, id: Long): Boolean = {
        adapter.getItem(id.toInt) match {
          case item if item != Adapter.separatorToInclude && item != Adapter.separatorToDelete =>
            item.pending match {
              case None =>
                adapter.pendingToDelete(item.value) = true
              case Some(true) =>
                adapter.pendingToInclude(item) = false
              case Some(false) =>
                adapter.pendingToDelete(item.value) = false
            }
            Futures.future { updateAdapter(getSherlockActivity()) }
            true
          case _ =>
            false
        }
      }
    })
    for {
      header <- headerListEmpty.get
      button <- Option(header.findViewById(R.id.session_filter_header_add).asInstanceOf[ImageButton])
    } button.setOnClickListener(new View.OnClickListener { def onClick(v: View) = onClickAddFilter(v) })
    for {
      header <- headerListNonEmpty.get
      button <- Option(header.findViewById(R.id.session_filter_header_add).asInstanceOf[ImageButton])
    } button.setOnClickListener(new View.OnClickListener { def onClick(v: View) = onClickAddFilter(v) })
    footerApply.get.foreach(_.setOnClickListener(new View.OnClickListener { def onClick(v: View) = onClickApply(v) }))
    setHasOptionsMenu(false)
    registerForContextMenu(getListView)
    setListAdapter(adapter)
  }
  protected def onResume(adapter: Adapter) = {
    super.onResume
    val context = getSherlockActivity
    savedTitle = context.getTitle
    TabContent.fragment.foreach(onTabFragmentShow)
    adapter.pendingToInclude.subscribe(new adapter.pendingToInclude.Sub {
      def notify(pub: adapter.pendingToInclude.Pub, event: Message[Adapter.Item] with Undoable) =
        onPendingListChanged(adapter, event)
    })
    adapter.pendingToDelete.subscribe(new adapter.pendingToDelete.Sub {
      def notify(pub: adapter.pendingToDelete.Pub, event: Message[String] with Undoable) =
        onPendingListChanged(adapter, event)
    })
    if (adapter.pendingToInclude.isEmpty && adapter.pendingToDelete.isEmpty)
      footerApply.get.foreach(_.setEnabled(false))
    Futures.future { updateAdapterFunction(context) }
  }
  protected def onPause(adapter: Adapter) = {
    adapter.pendingToInclude.removeSubscriptions
    adapter.pendingToDelete.removeSubscriptions
    TabContent.fragment.foreach(onTabFragmentHide)
    getSherlockActivity.setTitle(savedTitle)
    super.onPause
  }
  protected def onListItemClick(adapter: Adapter, l: ListView, v: View, position: Int, id: Long) = {
    adapter.getItem(id.toInt) match {
      case item if item != Adapter.separatorToInclude && item != Adapter.separatorToDelete =>
        l.setItemChecked(position, !item.isActive)
        Futures.future { item.isActive = !item.isActive }
      case _ =>
    }
  }
  protected def onClickAddFilter(adapter: Adapter, isActivityAllow: Boolean): Unit = try {
    for {
      header <- if (adapter.isEmpty) headerListEmpty.get else headerListNonEmpty.get
      headerIP1 <- Option(header.findViewById(R.id.session_filter_header_ip1).asInstanceOf[TextView])
      headerIP2 <- Option(header.findViewById(R.id.session_filter_header_ip2).asInstanceOf[TextView])
      headerIP3 <- Option(header.findViewById(R.id.session_filter_header_ip3).asInstanceOf[TextView])
      headerIP4 <- Option(header.findViewById(R.id.session_filter_header_ip4).asInstanceOf[TextView])
    } {
      val context = getSherlockActivity()
      val ip1 = getIP(headerIP1)
      val ip2 = getIP(headerIP2)
      val ip3 = getIP(headerIP3)
      val ip4 = getIP(headerIP4)
      val item: String = ip1 + "." + ip2 + "." + ip3 + "." + ip4
      for (ip <- Seq(ip1, ip2, ip3, ip4) if ip.isInstanceOf[Int]) if (ip.asInstanceOf[Int] > 255) {
        Toast.makeText(context, getString(R.string.session_filter_illegal_outofrange).format(ip), Toast.LENGTH_SHORT).show()
        IAmMumble("filter " + item + " is illegal")
        return
      }
      if (item == "*.*.*.*") {
        IAmMumble("filter " + item + " is illegal")
        Toast.makeText(context, getString(R.string.session_filter_illegal).format(item), Toast.LENGTH_SHORT).show()
      } else if (adapter.exists(item)) {
        log.info("filter " + item + " already exists")
        Toast.makeText(context, getString(R.string.session_filter_exists).format(item), Toast.LENGTH_SHORT).show()
      } else {
        adapter.pendingToInclude(Adapter.Item(item, Some(true))(true, isActivityAllow)) = true
        Toast.makeText(context, getString(if (isActivityAllow)
          R.string.session_filter_add_allow
        else
          R.string.session_filter_add_deny).format(item), Toast.LENGTH_SHORT).show()
      }
      clearHeader
    }
  } catch {
    case ce: ControlThrowable =>
      throw ce // propagate
    case e =>
      log.error(e.getMessage, e)
  }
  protected def onClickApply(adapter: Adapter, isActivityAllow: Boolean) = synchronized {
    val context = getSherlockActivity()
    if (isActivityAllow) {
      if (adapter.pendingToDelete.nonEmpty) {
        IAmMumble("delete allow filter(s) " + adapter.pendingToDelete.mkString(", "))
        SSHDPreferences.FilterConnection.Allow.remove(context, adapter.pendingToDelete.toArray: _*)
      }
      val (includeEnabled, includeDisabled) = adapter.pendingToInclude.partition(_.isActive)
      if (includeEnabled.nonEmpty) {
        IAmMumble("add/set allow filter(s) " + includeEnabled.mkString(", ") + " with state 'enabled'")
        SSHDPreferences.FilterConnection.Allow.enable(context, includeEnabled.toSeq.map(_.toString): _*)
      }
      if (includeDisabled.nonEmpty) {
        IAmMumble("add/set allow filter(s) " + includeDisabled.mkString(", ") + " with state 'disabled'")
        SSHDPreferences.FilterConnection.Allow.disable(context, includeDisabled.toSeq.map(_.toString): _*)
      }
    } else {
      if (adapter.pendingToDelete.nonEmpty) {
        IAmMumble("delete deny filter(s) " + adapter.pendingToDelete.mkString(", "))
        SSHDPreferences.FilterConnection.Deny.remove(context, adapter.pendingToDelete.toArray: _*)
      }
      val (includeEnabled, includeDisabled) = adapter.pendingToInclude.partition(_.isActive)
      if (includeEnabled.nonEmpty) {
        IAmMumble("add/set deny filter(s) " + includeEnabled.mkString(", ") + " with state 'enabled'")
        SSHDPreferences.FilterConnection.Deny.enable(context, includeEnabled.toSeq.map(_.toString): _*)
      }
      if (includeDisabled.nonEmpty) {
        IAmMumble("add/set deny filter(s) " + includeDisabled.mkString(", ") + " with state 'disabled'")
        SSHDPreferences.FilterConnection.Deny.disable(context, includeDisabled.toSeq.map(_.toString): _*)
      }
    }
    adapter.pendingToInclude.clear
    adapter.pendingToDelete.clear
    updateAdapterFunction(context)
    AnyBase.runOnUiThread { clearHeader }
  }
  private def getIP(v: TextView): Any = v.getText.toString match {
    case "" => "*"
    case ip => ip.toInt
  }
  private def clearHeader = {
    for {
      header <- headerListEmpty.get
      headerIP1 <- Option(header.findViewById(R.id.session_filter_header_ip1).asInstanceOf[TextView])
      headerIP2 <- Option(header.findViewById(R.id.session_filter_header_ip2).asInstanceOf[TextView])
      headerIP3 <- Option(header.findViewById(R.id.session_filter_header_ip3).asInstanceOf[TextView])
      headerIP4 <- Option(header.findViewById(R.id.session_filter_header_ip4).asInstanceOf[TextView])
    } {
      headerIP1.setText("")
      headerIP2.setText("")
      headerIP3.setText("")
      headerIP4.setText("")
    }
    for {
      header <- headerListNonEmpty.get
      headerIP1 <- Option(header.findViewById(R.id.session_filter_header_ip1).asInstanceOf[TextView])
      headerIP2 <- Option(header.findViewById(R.id.session_filter_header_ip2).asInstanceOf[TextView])
      headerIP3 <- Option(header.findViewById(R.id.session_filter_header_ip3).asInstanceOf[TextView])
      headerIP4 <- Option(header.findViewById(R.id.session_filter_header_ip4).asInstanceOf[TextView])
    } {
      headerIP1.setText("")
      headerIP2.setText("")
      headerIP3.setText("")
      headerIP4.setText("")
    }
  }
}
