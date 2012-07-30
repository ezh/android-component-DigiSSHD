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

import scala.Option.option2Iterable

import org.digimead.digi.ctrl.lib.androidext.SafeDialog
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDPreferences
import org.digimead.digi.ctrl.sshd.ext.SherlockSafeDialogFragment
import org.digimead.digi.ctrl.sshd.service.TabContent

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView

object GrantSuperuserPermission extends CheckBoxItem with Logging {
  val option: DOption.OptVal = SSHDPreferences.AsRoot.option

  @Loggable
  def onCheckboxClick(view: CheckBox, lastState: Boolean) =
    if (lastState) {
      SSHDPreferences.AsRoot.set(false, view.getContext, true)
      if (view.isChecked)
        view.setChecked(false)
    } else for {
      fragment <- TabContent.fragment
      dialog <- GrantSuperuserPermission.Dialog.rootRequest
    } if (dialog.isShowing) {
      //  AnyBase.runOnUiThread { dialog.updateContent(info) }
    } else {
      SafeDialog.show(fragment.getSherlockActivity, Some(R.id.main_topPanel), dialog.toString, () => dialog)
    }
  @Loggable
  def onListItemClick(l: ListView, v: View) =
    view.get.foreach {
      view =>
        onCheckboxClick(view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox], getState[Boolean](view.getContext))
    }
  object Dialog {
    lazy val rootRequest = AppComponent.Context.map(context =>
      Fragment.instantiate(context.getApplicationContext, classOf[RootRequest].getName, null).asInstanceOf[RootRequest])
    class RootRequest extends SherlockSafeDialogFragment with Logging {
      @volatile private var dirtyHackForDirtyFramework = false
      @volatile private var cachedDialog: Option[AlertDialog] = None

      override def toString = "dialog_rootrequest"
      @Loggable
      override def onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
      }
      @Loggable
      override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
        if (dirtyHackForDirtyFramework && inflater != null) {
          log.warn("workaround for \"requestFeature() must be called before adding content\"")
          dirtyHackForDirtyFramework = false
          return super.onCreateView(inflater, container, savedInstanceState)
        } else if (inflater == null)
          dirtyHackForDirtyFramework = true
        if (cachedDialog.nonEmpty)
          return null
        val context = getSherlockActivity
        val view = new TextView(context)
        view.setText(R.string.dialog_root_message)
        view
      }
      @Loggable
      override def onCreateDialog(savedInstanceState: Bundle): Dialog = cachedDialog match {
        case Some(dialog) =>
          dialog.show
          dialog
        case None =>
          val context = getSherlockActivity
          val dialog = new AlertDialog.Builder(context).
            setIcon(R.drawable.ic_danger).
            setTitle(R.string.dialog_root_title).
            setMessage(R.string.dialog_root_message).
            setPositiveButton(android.R.string.ok, RootRequest.PositiveButtonListener).
            setNegativeButton(android.R.string.cancel, RootRequest.NegativeButtonListener).
            create()
          dialog.show
          cachedDialog = Some(dialog)
          dialog
      }
    }
    object RootRequest {
      object PositiveButtonListener extends DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, whichButton: Int) =
          GrantSuperuserPermission.view.get.foreach(view => {
            SSHDPreferences.AsRoot.set(true, view.getContext, true)
            val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
            if (!checkbox.isChecked)
              checkbox.setChecked(true)
          })
      }
      object NegativeButtonListener extends DialogInterface.OnClickListener {
        def onClick(dialog: DialogInterface, whichButton: Int) =
          GrantSuperuserPermission.view.get.foreach(view => {
            val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
            if (checkbox.isChecked)
              checkbox.setChecked(false)
          })
      }
    }
  }
}