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

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDPreferences

import android.app.AlertDialog
import android.content.DialogInterface
import android.view.View
import android.widget.CheckBox
import android.widget.ListView

object GrantSuperuserPermission extends CheckBoxItem with Logging {
  val option: DOption.OptVal = SSHDPreferences.AsRoot.option

  @Loggable
  def onCheckboxClick(view: CheckBox, lastState: Boolean) =
    if (lastState) {
      SSHDPreferences.AsRoot.set(false, view.getContext, true)
      if (view.isChecked)
        view.setChecked(false)
    } else
      Futures.future { // leave UI thread
/*        TabActivity.activity.foreach {
          activity =>
            AppComponent.Inner.showDialogSafe[AlertDialog](activity, "dialog_root", () => {
              val dialog = new AlertDialog.Builder(activity).
                setTitle(R.string.dialog_root_title).
                setMessage(R.string.dialog_root_message).
                setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                  def onClick(dialog: DialogInterface, whichButton: Int) = {
                    GrantSuperuserPermission.view.get.foreach(view => {
                      SSHDPreferences.AsRoot.set(true, view.getContext, true)
                      val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
                      if (!checkbox.isChecked)
                        checkbox.setChecked(true)
                    })
                  }
                }).
                setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                  def onClick(dialog: DialogInterface, whichButton: Int) = {
                    GrantSuperuserPermission.view.get.foreach(view => {
                      val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
                      if (checkbox.isChecked)
                        checkbox.setChecked(lastState)
                    })
                  }
                }).
                setIcon(R.drawable.ic_danger).
                create()
              dialog.show()
              dialog
            })
        }*/
      }
  @Loggable
  def onListItemClick(l: ListView, v: View) =
    view.get.foreach {
      view =>
        onCheckboxClick(view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox], getState[Boolean](view.getContext))
    }
}