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

import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.SSHDPreferences

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ListView
import android.widget.TextView

object AuthentificationMode extends TextViewItem with Logging {
  val option = SSHDPreferences.DOption.AuthentificationMode

  @Loggable
  override def onListItemClick(l: ListView, v: View) = Futures.future { // leave UI thread
    /*   TabActivity.activity.foreach {
      activity =>
        AppComponent.Inner.showDialogSafe[AlertDialog](activity, "dialog_auth", () => {
          val authTypeValue = new AtomicInteger(getState[Int](activity))
          val dialog = new AlertDialog.Builder(activity).
            setTitle(R.string.dialog_auth_title).
            setSingleChoiceItems(R.array.auth_type, SSHDPreferences.AuthentificationMode.get(activity).id - 1, new DialogInterface.OnClickListener() {
              def onClick(dialog: DialogInterface, which: Int) { authTypeValue.set(which + 1) }
            }).
            setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
              def onClick(dialog: DialogInterface, whichButton: Int) {
                log.debug("change authentification mode")
                val context = dialog.asInstanceOf[AlertDialog].getContext
                val authType = AuthType(authTypeValue.get)
                SSHDPreferences.AuthentificationMode.set(authType, context, true)
                AuthentificationMode.view.get.foreach(view => {
                  val text = view.findViewById(android.R.id.content).asInstanceOf[TextView]
                  XResource.getString(activity, "option_auth_" + authType.toString.replaceAll(""" """, """_""")) match {
                    case Some(string) =>
                      text.setText(string)
                    case None =>
                      text.setText(authType.toString.toLowerCase.replaceAll(""" """, "\n"))
                  }
                })
                // update DefaultUser enable/disable flag
                DefaultUser.view.get.foreach(v => DefaultUser.updateCheckbox(v.findViewById(_root_.android.R.id.checkbox).asInstanceOf[CheckBox]))
              }
            }).
            setNegativeButton(android.R.string.cancel, null).
            setIcon(android.R.drawable.ic_dialog_alert).
            create()
          dialog.show()
          dialog
        })
    }*/
  }
  def getState[T](context: Context)(implicit m: Manifest[T]): T = {
    assert(m.erasure == option.kind)
    SSHDPreferences.AuthentificationMode.get(context).id.asInstanceOf[T]
  }
  def getStateExt(context: Context) =
    AuthType(getState[Int](context))
  object AuthType extends Enumeration {
    val None = Value("none")
    val SingleUser = Value("single user")
    val MultiUser = Value("multi user")
  }
  override def getView(context: Context, inflater: LayoutInflater): View = {
    val view = super.getView(context, inflater)
    val value = view.findViewById(android.R.id.content).asInstanceOf[TextView]
    val authType = SSHDPreferences.AuthentificationMode.get(context).toString
    XResource.getString(context, "option_auth_" + authType.replaceAll(""" """, """_""")) match {
      case Some(string) =>
        value.setText(string)
      case None =>
        value.setText(authType.toLowerCase.replaceAll(""" """, "\n"))
    }
    view
  }
}
