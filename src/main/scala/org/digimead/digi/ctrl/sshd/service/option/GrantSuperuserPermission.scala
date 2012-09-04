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

package org.digimead.digi.ctrl.sshd.service.option

import scala.actors.Futures
import scala.ref.WeakReference

import org.digimead.digi.lib.ctrl.AnyBase
import org.digimead.digi.lib.ctrl.ext.SafeDialog
import org.digimead.digi.lib.ctrl.ext.XDialog
import org.digimead.digi.lib.ctrl.ext.XDialog.dialog2string
import org.digimead.digi.lib.ctrl.ext.XResource
import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.ctrl.base.AppComponent
import org.digimead.digi.lib.ctrl.declaration.DOption
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDPreferences
import org.digimead.digi.ctrl.sshd.SSHDResource
import org.digimead.digi.ctrl.sshd.ext.SSHDAlertDialog
import org.digimead.digi.ctrl.sshd.service.TabContent

import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentTransaction
import android.text.Html
import android.view.View
import android.widget.CheckBox
import android.widget.ListView

object GrantSuperuserPermission extends CheckBoxItem with Logging {
  val option: DOption.OptVal = SSHDPreferences.AsRoot.option

  @Loggable
  def onCheckboxClick(view: CheckBox, lastState: Boolean) = if (lastState) {
    Futures.future { SSHDPreferences.AsRoot.set(false, view.getContext, true) }
    if (view.isChecked)
      view.setChecked(false)
  } else for {
    fragment <- TabContent.fragment
    dialog <- SSHDResource.serviceRootRequest
  } if (!dialog.isShowing)
    Dialog.showRootRequest(fragment.getSherlockActivity)
  @Loggable
  def onListItemClick(l: ListView, v: View) =
    view.get.foreach {
      view =>
        onCheckboxClick(view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox], getState[Boolean](view.getContext))
    }

  object Dialog {
    @Loggable
    def showRootRequest(activity: FragmentActivity) =
      SSHDResource.serviceRootRequest.foreach(dialog =>
        SafeDialog(activity, dialog, () => dialog).target(R.id.main_topPanel).
          transaction(SSHDPreferences.defaultTransaction(dialog)).show())

    class RootRequest
      extends SSHDAlertDialog(AppComponent.Context.map(c => (XResource.getId(c, "ic_danger", "drawable")))) with Logging {
      override protected lazy val positive = Some((android.R.string.ok, new XDialog.ButtonListener(new WeakReference(RootRequest.this),
        Some((dialog: RootRequest) => {
          defaultButtonCallback(dialog)
          onPositiveClick
        }))))
      override protected lazy val negative = Some((android.R.string.cancel, new XDialog.ButtonListener(new WeakReference(RootRequest.this),
        Some((dialog: RootRequest) => {
          defaultButtonCallback(dialog)
          onNegativeClick
        }))))

      def tag = "dialog_service_rootrequest"
      def title = Html.fromHtml(XResource.getString(getSherlockActivity, "service_rootrequest_title").
        getOrElse("Access Permission"))
      def message = Some(Html.fromHtml(XResource.getString(getSherlockActivity, "service_rootrequest_message").
        getOrElse("Are you sure you want to grant superuser permission to native components?<br/><br/>" +
          "Please note that your software must support this function. This option is ignored " +
          "if you have a firmware with limited functionality.")))
      def onPositiveClick() =
        GrantSuperuserPermission.view.get.foreach(view => {
          Futures.future { SSHDPreferences.AsRoot.set(true, view.getContext, true) }
          val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
          if (!checkbox.isChecked)
            checkbox.setChecked(true)
        })
      def onNegativeClick(): Any =
        GrantSuperuserPermission.view.get.foreach(view => {
          val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
          if (checkbox.isChecked)
            AnyBase.runOnUiThread { checkbox.setChecked(false) }
        })
    }
  }
}
