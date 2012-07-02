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

package org.digimead.digi.ctrl.sshd.service.option

import scala.actors.Futures

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDCommon
import org.digimead.digi.ctrl.sshd.service.TabActivity

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.CheckBox
import android.widget.ListView

object GrantSuperuserPermission extends CheckBoxItem with Logging {
  val option: DOption.OptVal = DOption.AsRoot

  @Loggable
  def onCheckboxClick(view: CheckBox, lastState: Boolean) =
    if (lastState) {
      val context = view.getContext
      val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
      val editor = pref.edit()
      editor.putBoolean(option.tag, !lastState)
      editor.apply
      view.setChecked(!lastState)
      SSHDCommon.optionChangedOnRestartNotify(context, option, getState[Boolean](context).toString)
    } else
      Futures.future { // leave UI thread
        TabActivity.activity.foreach {
          activity =>
            AppComponent.Inner.showDialogSafe[AlertDialog](activity, "dialog_root", () => {
              val dialog = new AlertDialog.Builder(activity).
                setTitle(R.string.dialog_root_title).
                setMessage(R.string.dialog_root_message).
                setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                  def onClick(dialog: DialogInterface, whichButton: Int) {
                    GrantSuperuserPermission.view.get.foreach {
                      view =>
                        val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
                        checkbox.setChecked(!lastState)
                    }
                    val pref = activity.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
                    val editor = pref.edit()
                    editor.putBoolean(option.tag, !lastState)
                    editor.commit()
                    activity.sendBroadcast(new Intent(DIntent.UpdateOption, Uri.parse("code://" + activity.getPackageName + "/" + option)))
                    SSHDCommon.optionChangedOnRestartNotify(activity, option, getState[Boolean](activity).toString)
                  }
                }).
                setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                  def onClick(dialog: DialogInterface, whichButton: Int) {
                    GrantSuperuserPermission.view.get.foreach {
                      view =>
                        val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
                        checkbox.setChecked(lastState)
                    }
                  }
                }).
                setIcon(R.drawable.ic_danger).
                create()
              dialog.show()
              dialog
            })
        }
      }
  @Loggable
  def onListItemClick(l: ListView, v: View) =
    view.get.foreach {
      view =>
        onCheckboxClick(view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox], getState[Boolean](view.getContext))
    }
}