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
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDActivity
import org.digimead.digi.ctrl.sshd.SSHDResource
import org.digimead.digi.ctrl.sshd.SSHDTabAdapter
import org.digimead.digi.ctrl.sshd.ext.SSHDAlertDialog
import org.digimead.digi.ctrl.sshd.ext.SSHDFragment

import com.actionbarsherlock.app.SherlockListFragment

import android.app.Activity
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.text.Html
import android.view.View
import android.widget.Button
import android.widget.ListView

class FilterRemoveFragment extends SherlockListFragment with SSHDFragment with TabContent.AccessToTabFragment with Logging {
  FilterRemoveFragment.fragment = Some(this)
  private lazy val footerApply = new WeakReference(getSherlockActivity.
    findViewById(R.id.service_filter_footer_apply).asInstanceOf[Button])
  private var savedTitle: CharSequence = ""
  log.debug("alive")

  def tag = "fragment_service_filter_remove"
  @Loggable
  override def onAttach(activity: Activity) {
    super.onAttach(activity)
    FilterRemoveFragment.fragment = Some(this)
  }
  @Loggable
  override def onActivityCreated(savedInstanceState: Bundle) = SSHDActivity.ppGroup("FilterRemoveFragment.onActivityCreated") {
    super.onActivityCreated(savedInstanceState)
    val inflater = getLayoutInflater(savedInstanceState)
    val lv = getListView
    setHasOptionsMenu(false)
    FilterRemoveAdapter.adapter.foreach(setListAdapter)
    registerForContextMenu(lv)
  }
  @Loggable
  override def onResume() = {
    super.onResume
    val activity = getSherlockActivity
    Futures.future {
      FilterRemoveAdapter.update(activity)
      for {
        adapter <- FilterRemoveAdapter.adapter
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
    activity.setTitle(XResource.getString(activity, "app_name_filter_remove").getOrElse("Remove interface filters"))
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
    FilterRemoveFragment.fragment = None
    super.onDetach()
  }
  @Loggable
  override protected def onListItemClick(l: ListView, v: View, position: Int, id: Long) = for {
    adapter <- FilterRemoveAdapter.adapter
    footerApply <- footerApply.get
  } {
    adapter.onListItemClick(position)
    if (adapter.getPending.isEmpty)
      footerApply.setEnabled(false)
    else
      footerApply.setEnabled(true)
  }
  @Loggable
  def onClickApply(v: View) = SSHDResource.serviceFiltersRemove.foreach {
    dialog =>
      if (!dialog.isShowing)
        FilterRemoveFragment.Dialog.showFiltersRemove(getSherlockActivity)
  }
  @Loggable
  private def onSubmit() = {
    val context = getSherlockActivity
    FilterRemoveAdapter.adapter.foreach(_.submit)
    FilterBlock.updateItems(context)
    getSherlockActivity.getSupportFragmentManager.
      popBackStack(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
  }
}

object FilterRemoveFragment extends Logging {
  /** profiling support */
  private val ppLoading = SSHDActivity.ppGroup.start("service.FilterDelFragment$")
  /** TabContent fragment instance */
  @volatile private[service] var fragment: Option[FilterRemoveFragment] = None
  log.debug("alive")
  ppLoading.stop

  @Loggable
  def show() = SSHDTabAdapter.getSelectedFragment match {
    case Some(currentTabFragment) =>
      SSHDFragment.show(classOf[FilterRemoveFragment], currentTabFragment)
    case None =>
      log.fatal("current tab fragment not found")
  }

  case class FilterItem(val value: String, var pending: Boolean)
  object Dialog {
    @Loggable
    def showFiltersRemove(activity: FragmentActivity) =
      SSHDResource.serviceFiltersRemove.foreach(dialog =>
        SafeDialog(activity, dialog, () => dialog).transaction((ft, fragment, target) => {
          ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
          ft.addToBackStack(dialog)
        }).show())

    class FiltersRemove
      extends SSHDAlertDialog(AppComponent.Context.map(c => (XResource.getId(c, "ic_danger", "drawable")))) with Logging {
      override protected lazy val positive = Some((android.R.string.ok, new XDialog.ButtonListener(new WeakReference(FiltersRemove.this),
        Some((dialog: FiltersRemove) => {
          defaultButtonCallback(dialog)
          Futures.future { FilterRemoveFragment.fragment.foreach(_.onSubmit) }
        }))))
      override protected lazy val negative = Some((android.R.string.cancel, new XDialog.ButtonListener(new WeakReference(FiltersRemove.this),
        Some(defaultButtonCallback))))

      def tag = "dialog_service_filtersremove"
      def title = Html.fromHtml(XResource.getString(getSherlockActivity, "service_filtersremove_title").
        getOrElse("Remove new filters"))
      def message = Some(Html.fromHtml(XResource.getString(getSherlockActivity, "service_filtersadd_message").
        getOrElse("Are you sure you want to remove interface filters?")))
    }
  }
}
