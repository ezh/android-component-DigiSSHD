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
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.androidext.SafeDialog
import org.digimead.digi.ctrl.lib.androidext.XDialog
import org.digimead.digi.ctrl.lib.androidext.XDialog.dialog2string
import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDActivity
import org.digimead.digi.ctrl.sshd.SSHDResource
import org.digimead.digi.ctrl.sshd.SSHDTabAdapter
import org.digimead.digi.ctrl.sshd.ext.SSHDAlertDialog
import org.digimead.digi.ctrl.sshd.ext.SSHDFragment

import com.actionbarsherlock.app.SherlockListFragment

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.text.Html
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class FilterAddFragment extends SherlockListFragment with SSHDFragment with TabContent.AccessToTabFragment with Logging {
  FilterAddFragment.fragment = Some(this)
  private lazy val headerInterface = new WeakReference(getSherlockActivity.
    findViewById(R.id.service_filter_header_interface).asInstanceOf[TextView])
  private lazy val headerIP1 = new WeakReference(getSherlockActivity.
    findViewById(R.id.service_filter_header_ip1).asInstanceOf[TextView])
  private lazy val headerIP2 = new WeakReference(getSherlockActivity.
    findViewById(R.id.service_filter_header_ip2).asInstanceOf[TextView])
  private lazy val headerIP3 = new WeakReference(getSherlockActivity.
    findViewById(R.id.service_filter_header_ip3).asInstanceOf[TextView])
  private lazy val headerIP4 = new WeakReference(getSherlockActivity.
    findViewById(R.id.service_filter_header_ip4).asInstanceOf[TextView])
  private lazy val footerApply = new WeakReference(getSherlockActivity.
    findViewById(R.id.service_filter_footer_apply).asInstanceOf[Button])
  private var savedTitle: CharSequence = ""
  log.debug("alive")

  def tag = "fragment_service_filter_add"
  @Loggable
  override def onAttach(activity: Activity) {
    super.onAttach(activity)
    FilterAddFragment.fragment = Some(this)
  }
  @Loggable
  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    SSHDActivity.ppGroup("FilterAddFragment.onCreateView") {
      inflater.inflate(R.layout.fragment_service_filter_add, container, false)
    }
  @Loggable
  override def onActivityCreated(savedInstanceState: Bundle) = SSHDActivity.ppGroup("FilterAddFragment.onActivityCreated") {
    super.onActivityCreated(savedInstanceState)
    val inflater = getLayoutInflater(savedInstanceState)
    val lv = getListView
    setHasOptionsMenu(false)
    FilterAddAdapter.adapter.foreach(setListAdapter)
    registerForContextMenu(lv)
  }
  @Loggable
  override def onResume() = {
    super.onResume
    val activity = getSherlockActivity
    Futures.future {
      FilterAddAdapter.update(activity)
      for {
        adapter <- FilterAddAdapter.adapter
        footerApply <- footerApply.get
      } AnyBase.runOnUiThread {
        Option(footerApply.findViewById(R.id.service_filter_footer_apply)).foreach {
          button =>
            button.setOnClickListener(new View.OnClickListener() {
              def onClick(view: View) = onClickApply(view)
            })
        }
        if (adapter.getPending.isEmpty)
          footerApply.setEnabled(false)
        else
          footerApply.setEnabled(true)
      }
    }
    savedTitle = activity.getTitle
    activity.setTitle(XResource.getString(activity, "app_name_filter_add").getOrElse("Add interface filters"))
    tabFragment.foreach(onTabFragmentShow)
  }
  @Loggable
  override def onPause() = {
    tabFragment.foreach(onTabFragmentHide)
    getSherlockActivity.setTitle(savedTitle)
    super.onPause
  }
  @Loggable
  override def onDetach() {
    FilterAddFragment.fragment = None
    super.onDetach()
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo) = for {
    adapter <- FilterAddAdapter.adapter
    headerInterface <- headerInterface.get
    headerIP1 <- headerIP1.get
    headerIP2 <- headerIP2.get
    headerIP3 <- headerIP3.get
    headerIP4 <- headerIP4.get
  } {
    if (v.getId() == android.R.id.list) {
      val info = menuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo]
      adapter.itemLongClick(info.position)
      val item = adapter.getItem(info.position - 1)
      if (item != null) {
        val Array(interface, ip) = item.split(":")
        val Array(ip1, ip2, ip3, ip4) = ip.split("""\.""")
        headerInterface.setText(interface.replaceAll("""\*""", ""))
        headerIP1.setText(ip1.replaceAll("""\*""", ""))
        headerIP2.setText(ip2.replaceAll("""\*""", ""))
        headerIP3.setText(ip3.replaceAll("""\*""", ""))
        headerIP4.setText(ip4.replaceAll("""\*""", ""))
      }
    }
  }
  @Loggable
  override protected def onListItemClick(l: ListView, v: View, position: Int, id: Long) = for {
    adapter <- FilterAddAdapter.adapter
    footerApply <- footerApply.get
  } {
    adapter.onListItemClick(position - 1)
    if (adapter.getPending.isEmpty)
      footerApply.setEnabled(false)
    else
      footerApply.setEnabled(true)
  }
  @Loggable
  def onClickCustom(v: View) = for {
    adapter <- FilterAddAdapter.adapter
    headerInterface <- headerInterface.get
    headerIP1 <- headerIP1.get
    headerIP2 <- headerIP2.get
    headerIP3 <- headerIP3.get
    headerIP4 <- headerIP4.get
    footerApply <- footerApply.get
  } {
    val context = getSherlockActivity
    val item: String = headerInterface.getText.toString.replaceFirst("^$", "*") + ":" +
      headerIP1.getText.toString.replaceFirst("^$", "*") + "." +
      headerIP2.getText.toString.replaceFirst("^$", "*") + "." +
      headerIP3.getText.toString.replaceFirst("^$", "*") + "." +
      headerIP4.getText.toString.replaceFirst("^$", "*")
    val checkAlreaySaved = Futures.future { FilterBlock.listFilters(context).contains(item) }
    if (item == "*:*.*.*.*") {
      log.info("filter *:*.*.*.* is illegal")
      Toast.makeText(context, getString(R.string.service_filter_illegal).format(item), DConstant.toastTimeout).show()
    } else if (adapter.contains(item)) {
      log.info("filter " + item + " already exists")
      Toast.makeText(context, getString(R.string.service_filter_exists).format(item), DConstant.toastTimeout).show()
    } else if (checkAlreaySaved()) {
      log.info("filter " + item + " already exists")
      Toast.makeText(context, getString(R.string.service_filter_exists).format(item), DConstant.toastTimeout).show()
    } else {
      adapter.addPending(item)
      if (adapter.getPending.isEmpty)
        footerApply.setEnabled(false)
      else
        footerApply.setEnabled(true)
      Futures.future { FilterAddAdapter.update(context) }
      Toast.makeText(context, getString(R.string.service_filter_select).format(item), DConstant.toastTimeout).show()
    }
  }
  @Loggable
  def onClickApply(v: View) = SSHDResource.serviceFiltersAdd.foreach {
    dialog =>
      if (!dialog.isShowing)
        FilterAddFragment.Dialog.showFiltersAdd(getSherlockActivity)
  }
  @Loggable
  private def onSubmit() = {
    val context = getSherlockActivity
    FilterAddAdapter.adapter.foreach(_.submit)
    FilterBlock.updateItems(context)
    getSherlockActivity.getSupportFragmentManager.
      popBackStack(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
  }
}

object FilterAddFragment extends Logging {
  /** profiling support */
  private val ppLoading = SSHDActivity.ppGroup.start("service.FilterAddFragment$")
  /** TabContent fragment instance */
  @volatile private[service] var fragment: Option[FilterAddFragment] = None
  private val STATE_PENDING = "pending"
  private val STATE_HTEXT1 = "htext1"
  private val STATE_HTEXT2 = "htext2"
  private val STATE_HTEXT3 = "htext3"
  private val STATE_HTEXT4 = "htext4"
  private val STATE_HTEXT5 = "htext5"
  log.debug("alive")
  ppLoading.stop

  @Loggable
  def show() = SSHDTabAdapter.getSelectedFragment match {
    case Some(currentTabFragment) =>
      SSHDFragment.show(classOf[FilterAddFragment], currentTabFragment)
    case None =>
      log.fatal("current tab fragment not found")
  }
  @Loggable
  def onClickCustom(v: View) = fragment.foreach(_.onClickCustom(v))

  case class FilterItem(value: String)
  object Dialog {
    @Loggable
    def showFiltersAdd(activity: FragmentActivity) =
      SSHDResource.serviceFiltersAdd.foreach(dialog =>
        SafeDialog(activity, dialog, () => dialog).transaction((ft, fragment, target) => {
          ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
          ft.addToBackStack(dialog)
        }).show())

    class FiltersAdd
      extends SSHDAlertDialog(AppComponent.Context.map(c => (XResource.getId(c, "ic_danger", "drawable")))) with Logging {
      override protected lazy val positive = Some((android.R.string.ok, new XDialog.ButtonListener(new WeakReference(FiltersAdd.this),
        Some((dialog: FiltersAdd) => {
          defaultButtonCallback(dialog)
          Futures.future { FilterAddFragment.fragment.foreach(_.onSubmit) }
        }))))
      override protected lazy val negative = Some((android.R.string.cancel, new XDialog.ButtonListener(new WeakReference(FiltersAdd.this),
        Some(defaultButtonCallback))))

      def tag = "dialog_service_filtersadd"
      def title = Html.fromHtml(XResource.getString(getSherlockActivity, "service_filtersadd_title").
        getOrElse("Add new filters"))
      def message = Some(Html.fromHtml(XResource.getString(getSherlockActivity, "service_filtersadd_message").
        getOrElse("Are you sure you want to add interface filters?")))
      @Loggable
      override def onResume() = {
        // hide keyboard
        val imm = getSherlockActivity.getSystemService(Context.INPUT_METHOD_SERVICE).asInstanceOf[InputMethodManager]
        for {
          fragment <- fragment
          headerIP1 <- fragment.headerIP1.get
          headerIP2 <- fragment.headerIP2.get
          headerIP3 <- fragment.headerIP3.get
          headerIP4 <- fragment.headerIP4.get
        } {
          imm.hideSoftInputFromWindow(headerIP1.getWindowToken(), 0)
          imm.hideSoftInputFromWindow(headerIP2.getWindowToken(), 0)
          imm.hideSoftInputFromWindow(headerIP3.getWindowToken(), 0)
          imm.hideSoftInputFromWindow(headerIP4.getWindowToken(), 0)
        }
        super.onResume
      }
    }
  }
}
