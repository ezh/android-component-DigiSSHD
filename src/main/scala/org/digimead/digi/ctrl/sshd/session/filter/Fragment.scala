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

import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.ext.SSHDFragment
import org.digimead.digi.ctrl.sshd.session.TabContent

import com.actionbarsherlock.app.SherlockListFragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button

abstract class Fragment extends SherlockListFragment with SSHDFragment with Logging {
  private lazy val footerApply = new WeakReference(getSherlockActivity.
    findViewById(R.id.session_filter_footer_apply).asInstanceOf[Button])
  //  private val pendingToInclude = new HashSet[FilterAdapter.Item] with ObservableSet[FilterAdapter.Item] with SynchronizedSet[FilterAdapter.Item]
  //  private val pendingToDelete = new HashSet[String] with ObservableSet[String] with SynchronizedSet[String]
  private var savedTitle: CharSequence = ""

  @Loggable
  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_session_filter, container, false)
  @Loggable
  override def onActivityCreated(savedInstanceState: Bundle) = {
    super.onActivityCreated(savedInstanceState)
    val inflater = getLayoutInflater(savedInstanceState)
    val lv = getListView
    lv.addHeaderView(inflater.inflate(R.layout.element_session_filter_header, null))
    setHasOptionsMenu(false)
    registerForContextMenu(getListView)
  }
  @Loggable
  override def onResume() = {
    super.onResume
    val activity = getSherlockActivity
    savedTitle = activity.getTitle
    TabContent.fragment.foreach(onTabFragmentShow)
  }
  @Loggable
  override def onPause() = {
    TabContent.fragment.foreach(onTabFragmentHide)
    getSherlockActivity.setTitle(savedTitle)
    super.onPause
  }

  /*  lazy val adapter = new FilterAdapter(this, () => try {
    (if (isActivityAllow)
      SSHDPreferences.FilterConnection.Allow.get(this)
    else
      SSHDPreferences.FilterConnection.Deny.get(this)).
      map(t => FilterAdapter.Item(t._1, if (pendingToDelete(t._1)) Some(false) else None)(t._2, isActivityAllow)) ++ pendingToInclude
  } catch {
    case e =>
      log.error(e.getMessage, e)
      Seq()
  })

  pendingToInclude.subscribe(new pendingToInclude.Sub {
    def notify(pub: pendingToInclude.Pub, event: Message[FilterAdapter.Item] with Undoable) =
      FilterActivity.this.onPendingListChanged(event)
  })
  pendingToDelete.subscribe(new pendingToDelete.Sub {
    def notify(pub: pendingToDelete.Pub, event: Message[String] with Undoable) =
      FilterActivity.this.onPendingListChanged(event)
  })

  @Loggable
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    AnyBase.init(this, false)
    AnyBase.preventShutdown(this)
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_BEHIND)
    setContentView(R.layout.session_filter)
    getIntent.getIntExtra("requestCode", 0) match {
      case id if id == FilterBlock.FILTER_REQUEST_ALLOW =>
        setTitle(XResource.getString(this, "app_name_filter_allow").getOrElse("DigiSSHD: Allow filter"))
        isActivityAllow = true
      case id if id == FilterBlock.FILTER_REQUEST_DENY =>
        setTitle(XResource.getString(this, "app_name_filter_deny").getOrElse("DigiSSHD: Deny filter"))
        isActivityAllow = false
      case id =>
        log.fatal("unknown activity resultCode " + id)
        finish()
    }
    val lv = getListView()
    lv.addHeaderView(headerListNonEmpty, null, false)
    lv.addFooterView(footerListNonEmpty, null, false)
    lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE)
    setListAdapter(adapter)
    lv.setOnItemLongClickListener(new OnItemLongClickListener() with Logging {
      @Loggable
      override def onItemLongClick(parent: AdapterView[_], view: View, position: Int, id: Long): Boolean = {
        adapter.getItem(id.toInt) match {
          case item if item != FilterAdapter.separatorToInclude && item != FilterAdapter.separatorToDelete =>
            item.pending match {
              case None =>
                pendingToDelete(item.value) = true
              case Some(true) =>
                pendingToInclude(item) = false
              case Some(false) =>
                pendingToDelete(item.value) = false
            }
            future { adapter.updateAdapter }
            true
          case _ =>
            false
        }
      }
    })
  }
  @Loggable
  override protected def onSaveInstanceState(savedInstanceState: Bundle) = {
    //    savedInstanceState.putStringArray(FilterAddActivity.STATE_PENDING, adapter.getPending.toArray)
    /*    savedInstanceState.putString(FilterAddActivity.STATE_HTEXT2, headerIP1.getText.toString)
    savedInstanceState.putString(FilterAddActivity.STATE_HTEXT3, headerIP2.getText.toString)
    savedInstanceState.putString(FilterAddActivity.STATE_HTEXT4, headerIP3.getText.toString)
    savedInstanceState.putString(FilterAddActivity.STATE_HTEXT5, headerIP4.getText.toString)*/
    super.onSaveInstanceState(savedInstanceState)
  }
  @Loggable
  override protected def onRestoreInstanceState(savedInstanceState: Bundle) = {
    super.onRestoreInstanceState(savedInstanceState)
    /*    runOnUiThread(new Runnable {
      def run = {
        adapter.setPending(savedInstanceState.getStringArray(FilterAddActivity.STATE_PENDING))
        if (adapter.getPending.isEmpty)
          footerApply.setEnabled(false)
        else
          footerApply.setEnabled(true)
      }
      headerInterface.setText(savedInstanceState.getString(FilterAddActivity.STATE_HTEXT1))
      headerIP1.setText(savedInstanceState.getString(FilterAddActivity.STATE_HTEXT2))
      headerIP2.setText(savedInstanceState.getString(FilterAddActivity.STATE_HTEXT3))
      headerIP3.setText(savedInstanceState.getString(FilterAddActivity.STATE_HTEXT4))
      headerIP4.setText(savedInstanceState.getString(FilterAddActivity.STATE_HTEXT5))
      adapter.notifyDataSetChanged()
    })*/
  }
  @Loggable
  override protected def onListItemClick(l: ListView, v: View, position: Int, id: Long) = {
    adapter.getItem(id.toInt) match {
      case item if item != FilterAdapter.separatorToInclude && item != FilterAdapter.separatorToDelete =>
        item.isActive = !item.isActive
        l.setItemChecked(position, item.isActive)
      case _ =>
    }
  }
  @Loggable
  def onClickAddFilter(v: View): Unit = try {
    var headerIP1: TextView = null
    var headerIP2: TextView = null
    var headerIP3: TextView = null
    var headerIP4: TextView = null
    if (adapter.isEmpty) {
      headerIP1 = headerListEmpty.findViewById(R.id.session_filter_header_ip1).asInstanceOf[TextView]
      headerIP2 = headerListEmpty.findViewById(R.id.session_filter_header_ip2).asInstanceOf[TextView]
      headerIP3 = headerListEmpty.findViewById(R.id.session_filter_header_ip3).asInstanceOf[TextView]
      headerIP4 = headerListEmpty.findViewById(R.id.session_filter_header_ip4).asInstanceOf[TextView]
    } else {
      headerIP1 = headerListNonEmpty.findViewById(R.id.session_filter_header_ip1).asInstanceOf[TextView]
      headerIP2 = headerListNonEmpty.findViewById(R.id.session_filter_header_ip2).asInstanceOf[TextView]
      headerIP3 = headerListNonEmpty.findViewById(R.id.session_filter_header_ip3).asInstanceOf[TextView]
      headerIP4 = headerListNonEmpty.findViewById(R.id.session_filter_header_ip4).asInstanceOf[TextView]
    }
    val ip1 = getIP(headerIP1)
    val ip2 = getIP(headerIP2)
    val ip3 = getIP(headerIP3)
    val ip4 = getIP(headerIP4)
    val item: String = ip1 + "." + ip2 + "." + ip3 + "." + ip4
    for (ip <- Seq(ip1, ip2, ip3, ip4) if ip.isInstanceOf[Int]) if (ip.asInstanceOf[Int] > 255) {
      Toast.makeText(this, getString(R.string.session_filter_illegal_outofrange).format(ip), DConstant.toastTimeout).show()
      IAmMumble("filter " + item + " is illegal")
      return
    }
    if (item == "*.*.*.*") {
      IAmMumble("filter " + item + " is illegal")
      Toast.makeText(this, getString(R.string.session_filter_illegal).format(item), DConstant.toastTimeout).show()
    } else if (adapter.exists(item)) {
      log.info("filter " + item + " already exists")
      Toast.makeText(this, getString(R.string.session_filter_exists).format(item), DConstant.toastTimeout).show()
    } else {
      pendingToInclude(FilterAdapter.Item(item, Some(true))(true, isActivityAllow)) = true
      Toast.makeText(this, getString(if (isActivityAllow)
        R.string.session_filter_add_allow
      else
        R.string.session_filter_add_deny).format(item), DConstant.toastTimeout).show()
      future { adapter.updateAdapter }
    }
  } catch {
    case ce: ControlThrowable => throw ce // propagate
    case e =>
      log.error(e.getMessage, e)
  }
  @Loggable
  def onClickApply(v: View) {
    if (isActivityAllow) {
      if (pendingToDelete.nonEmpty) {
        IAmMumble("delete allow filter(s) " + pendingToDelete.mkString(", "))
        SSHDPreferences.FilterConnection.Allow.remove(this, pendingToDelete.toArray: _*)
      }
      val (includeEnabled, includeDisabled) = pendingToInclude.partition(_.isActive)
      if (includeEnabled.nonEmpty) {
        IAmMumble("add allow filter(s) " + includeEnabled.mkString(", ") + " with state 'enabled'")
        SSHDPreferences.FilterConnection.Allow.enable(this, includeEnabled.toSeq.map(_.toString): _*)
      }
      if (includeDisabled.nonEmpty) {
        IAmMumble("add allow filter(s) " + includeDisabled.mkString(", ") + " with state 'disabled'")
        SSHDPreferences.FilterConnection.Allow.disable(this, includeDisabled.toSeq.map(_.toString): _*)
      }
    } else {
      if (pendingToDelete.nonEmpty) {
        IAmMumble("delete deny filter(s) " + pendingToDelete.mkString(", "))
        SSHDPreferences.FilterConnection.Deny.remove(this, pendingToDelete.toArray: _*)
      }
      val (includeEnabled, includeDisabled) = pendingToInclude.partition(_.isActive)
      if (includeEnabled.nonEmpty) {
        IAmMumble("add deny filter(s) " + includeEnabled.mkString(", ") + " with state 'enabled'")
        SSHDPreferences.FilterConnection.Deny.enable(this, includeEnabled.toSeq.map(_.toString): _*)
      }
      if (includeDisabled.nonEmpty) {
        IAmMumble("add deny filter(s) " + includeDisabled.mkString(", ") + " with state 'disabled'")
        SSHDPreferences.FilterConnection.Deny.disable(this, includeDisabled.toSeq.map(_.toString): _*)
      }
    }
    setResult(Activity.RESULT_OK)
    finish()
  }
  @Loggable
  def onClickCancel(v: View) {
    setResult(Activity.RESULT_CANCELED)
    finish()
  }
  private def onPendingListChanged(event: Message[_]) {
    log.debug("pending items changed: " + event)
    runOnUiThread(new Runnable {
      def run {
        if (pendingToInclude.isEmpty && pendingToDelete.isEmpty) {
          footerApply.setVisibility(View.GONE)
          footerCancel.setVisibility(View.GONE)
          footerBack.setVisibility(View.VISIBLE)
        } else {
          footerApply.setVisibility(View.VISIBLE)
          footerCancel.setVisibility(View.VISIBLE)
          footerBack.setVisibility(View.GONE)
        }
      }
    })
  }
  private def getIP(v: TextView): Any = v.getText.toString match {
    case "" => "*"
    case ip => ip.toInt
  }*/
}
