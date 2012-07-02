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
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDCommon
import org.digimead.digi.ctrl.sshd.service.TabActivity

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.Html
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView

object NetworkPort extends TextViewItem with Logging {
  val option: DOption.OptVal = DOption.Value("port", classOf[Int], 2222: java.lang.Integer)

  @Loggable
  override def onListItemClick(l: ListView, v: View) = Futures.future { // leave UI thread
    TabActivity.activity.foreach {
      activity =>
        AppComponent.Inner.showDialogSafe[AlertDialog](activity, "dialog_port", () => {
          val pref = activity.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
          val currentValue = pref.getInt(NetworkPort.option.tag, 2222)
          val maxLengthFilter = new InputFilter.LengthFilter(5)
          val portLayout = LayoutInflater.from(activity).inflate(R.layout.alertdialog_text, null)
          val portField = portLayout.findViewById(android.R.id.edit).asInstanceOf[EditText]
          portField.setInputType(InputType.TYPE_CLASS_NUMBER)
          portField.setText(currentValue.toString)
          portField.setFilters(Array(maxLengthFilter))
          val dialog = new AlertDialog.Builder(activity).
            setTitle(R.string.dialog_port_title).
            setMessage(Html.fromHtml(Android.getString(activity, "dialog_port_message").
              getOrElse("Select new TCP port in range from 1024 to 65535"))).
            setView(portLayout).
            setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
              def onClick(dialog: DialogInterface, whichButton: Int) = try {
                val port = portField.getText.toString.toInt
                log.debug("set port to " + port)
                val pref = activity.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
                val editor = pref.edit()
                editor.putInt(NetworkPort.option.tag, port)
                editor.commit()
                NetworkPort.view.get.foreach(view => {
                  val text = view.findViewById(android.R.id.content).asInstanceOf[TextView]
                  text.setText(port.toString)
                })
                activity.sendBroadcast(new Intent(DIntent.UpdateOption, Uri.parse("code://" + activity.getPackageName + "/" + NetworkPort.option)))
                SSHDCommon.optionChangedOnRestartNotify(activity, NetworkPort.option, NetworkPort.getState[Int](activity).toString)
              } catch {
                case e =>
                  log.error(e.getMessage, e)
              }
            }).
            setNegativeButton(android.R.string.cancel, null).
            setIcon(android.R.drawable.ic_dialog_info).
            create()
          dialog.show()
          val ok = dialog.findViewById(android.R.id.button1)
          ok.setEnabled(false)
          portField.addTextChangedListener(new TextWatcher {
            override def afterTextChanged(s: Editable) = try {
              val rawPort = s.toString
              if (rawPort.nonEmpty) {
                val port = rawPort.toInt
                if (port > 1023 && port < 65536 && port != currentValue)
                  ok.setEnabled(true)
                else
                  ok.setEnabled(false)
              } else
                ok.setEnabled(false)
            } catch {
              case e =>
                log.warn(e.getMessage, e)
            }
            override def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
          })
          dialog
        })
    }
  }
  def getState[T](context: Context)(implicit m: Manifest[T]): T = {
    assert(m.erasure == option.kind)
    val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
    pref.getInt(option.tag, option.default.asInstanceOf[Int]).asInstanceOf[T]
  }
  override def getView(context: Context, inflater: LayoutInflater): View = {
    val view = super.getView(context, inflater)
    val value = view.findViewById(android.R.id.content).asInstanceOf[TextView]
    value.setText(getState[Int](context).toString)
    view
  }
}