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

import org.digimead.digi.ctrl.lib.androidext.SafeDialog
import org.digimead.digi.ctrl.lib.androidext.XDialog
import org.digimead.digi.ctrl.lib.androidext.XDialog.dialog2string
import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDPreferences
import org.digimead.digi.ctrl.sshd.SSHDResource
import org.digimead.digi.ctrl.sshd.ext.SSHDAlertDialog
import org.digimead.digi.ctrl.sshd.service.TabContent

import android.content.Context
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentTransaction
import android.text.Editable
import android.text.Html
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView

object NetworkPort extends TextViewItem with Logging {
  val option = SSHDPreferences.NetworkPort.option

  @Loggable
  override def onListItemClick(l: ListView, v: View) = for {
    fragment <- TabContent.fragment
    dialog <- SSHDResource.serviceSelectPort
  } if (!dialog.isShowing)
    Dialog.showSelectPort(fragment.getSherlockActivity)
  def getState[T](context: Context)(implicit m: Manifest[T]): T = {
    assert(m.erasure == option.kind)
    SSHDPreferences.NetworkPort.get(context).asInstanceOf[T]
  }
  override def getView(context: Context, inflater: LayoutInflater): View = {
    val view = super.getView(context, inflater)
    val value = view.findViewById(android.R.id.content).asInstanceOf[TextView]
    value.setText(getState[Int](context).toString)
    view
  }

  object Dialog {
    @Loggable
    def showSelectPort(activity: FragmentActivity) =
      SSHDResource.serviceSelectPort.foreach(dialog =>
        SafeDialog(activity, dialog, () => dialog).transaction((ft, fragment, target) => {
          ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
          ft.addToBackStack(dialog)
        }).show())

    class SelectPort
      extends SSHDAlertDialog(Some(android.R.drawable.ic_menu_preferences)) with Logging {
      @volatile private var portInitialValue: Option[Int] = None
      @volatile private var portField = new WeakReference[EditText](null)
      override lazy val extContent = {
        val view = SelectPort.customContent
        view.foreach(v => v.findViewById(android.R.id.edit).asInstanceOf[EditText].addTextChangedListener(textChangedListener))
        view
      }
      override protected lazy val positive = Some((android.R.string.ok, new XDialog.ButtonListener(new WeakReference(SelectPort.this),
        Some((dialog: SelectPort) => {
          defaultButtonCallback(dialog)
          onPositiveClick
        }))))
      override protected lazy val negative = Some((android.R.string.cancel, new XDialog.ButtonListener(new WeakReference(SelectPort.this),
        Some(defaultButtonCallback))))
      private lazy val textChangedListener = new SelectPort.PortFieldTextChangedListener(new WeakReference(this))

      def tag = "dialog_service_selectport"
      def title = Html.fromHtml(XResource.getString(getSherlockActivity, "service_selectport_title").
        getOrElse("Select network port"))
      def message = Some(Html.fromHtml(XResource.getString(getSherlockActivity, "service_selectport_message").
        getOrElse("Select new TCP port in range from 1 to 65535")))
      @Loggable
      override def onResume() {
        super.onResume
        portField = new WeakReference(extContent.map(_.findViewById(android.R.id.edit).
          asInstanceOf[EditText]).getOrElse(null))
        portInitialValue = Option(SSHDPreferences.NetworkPort.get(getSherlockActivity))
        positiveView.get.foreach(_.setEnabled(false))
        for {
          portInitialValue <- portInitialValue
          portField <- portField.get
        } portField.setText(portInitialValue.toString)
      }
      def onPositiveClick() = portField.get.foreach {
        port =>
          val context = getSherlockActivity
          val newValue = port.getText.toString.toInt
          NetworkPort.view.get.foreach(view => {
            val text = view.findViewById(android.R.id.content).asInstanceOf[TextView]
            text.setText(newValue.toString)
          })
          Futures.future { SSHDPreferences.NetworkPort.set(newValue, context) }
      }
    }
    object SelectPort {
      private lazy val customContent = AppComponent.Context.map {
        context =>
          val maxLengthFilter = new InputFilter.LengthFilter(5)
          val container = LayoutInflater.from(context).inflate(R.layout.dialog_edittext, null).asInstanceOf[ScrollView]
          val view = container.getChildAt(0).asInstanceOf[LinearLayout]
          view.setOrientation(LinearLayout.VERTICAL)
          val portField = view.findViewById(android.R.id.edit).asInstanceOf[EditText]
          portField.setInputType(InputType.TYPE_CLASS_NUMBER)
          portField.setFilters(Array(maxLengthFilter))
          container
      }
      class PortFieldTextChangedListener(dialog: WeakReference[SelectPort]) extends TextWatcher {
        override def afterTextChanged(s: Editable) = try {
          for {
            dialog <- dialog.get
            positiveButton <- dialog.positiveView.get
            portInitialValue <- dialog.portInitialValue
          } {
            val rawPort = s.toString
            if (rawPort.nonEmpty) {
              val port = rawPort.toInt
              if (port > 1 && port < 65536 && port != portInitialValue)
                positiveButton.setEnabled(true)
              else
                positiveButton.setEnabled(false)
            } else
              positiveButton.setEnabled(false)
          }
        } catch {
          case e =>
            log.warn(e.getMessage, e)
        }
        override def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
      }
    }
  }
}
