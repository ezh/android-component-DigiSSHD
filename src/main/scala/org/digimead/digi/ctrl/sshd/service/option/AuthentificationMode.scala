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

import java.util.concurrent.atomic.AtomicInteger

import scala.actors.Futures
import scala.ref.WeakReference

import org.digimead.digi.lib.ctrl.ext.SafeDialog
import org.digimead.digi.lib.ctrl.ext.XDialog
import org.digimead.digi.lib.ctrl.ext.XDialog.dialog2string
import org.digimead.digi.lib.ctrl.ext.XResource
import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.ctrl.base.AppComponent
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDPreferences
import org.digimead.digi.ctrl.sshd.SSHDResource
import org.digimead.digi.ctrl.sshd.ext.SSHDListDialog
import org.digimead.digi.ctrl.sshd.service.TabContent

import android.content.Context
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentTransaction
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView

object AuthentificationMode extends TextViewItem with Logging {
  val option = SSHDPreferences.DOption.AuthentificationMode

  @Loggable
  override def onListItemClick(l: ListView, v: View) = for {
    fragment <- TabContent.fragment
    dialog <- SSHDResource.serviceSelectAuth
  } if (!dialog.isShowing)
    Dialog.showSelectAuth(fragment.getSherlockActivity)
  def getState[T](context: Context)(implicit m: Manifest[T]): T = {
    assert(m.erasure == option.kind)
    SSHDPreferences.AuthentificationMode.get(context).id.asInstanceOf[T]
  }
  def getStateExt(context: Context) = SSHDPreferences.AuthentificationType(getState[Int](context))
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

  object Dialog {
    @Loggable
    def showSelectAuth(activity: FragmentActivity) =
      SSHDResource.serviceSelectAuth.foreach(dialog =>
        SafeDialog(activity, dialog, () => dialog).target(R.id.main_topPanel).
          transaction(SSHDPreferences.defaultTransaction(dialog)).show())

    class SelectAuth
      extends SSHDListDialog(AppComponent.Context.map(c => (XResource.getId(c, "ic_users", "drawable")))) {
      @volatile private var authInitialValue: Option[Int] = None
      private lazy val currentValue = new AtomicInteger()
      override protected lazy val positive = Some((android.R.string.ok, new XDialog.ButtonListener(new WeakReference(SelectAuth.this),
        Some((dialog: SelectAuth) => {
          defaultButtonCallback(dialog)
          onPositiveClick
        }))))
      override protected lazy val negative = Some((android.R.string.cancel, new XDialog.ButtonListener(new WeakReference(SelectAuth.this),
        Some(defaultButtonCallback))))
      protected lazy val adapter = ArrayAdapter.createFromResource(getSherlockActivity, R.array.auth_type, android.R.layout.simple_list_item_single_choice)
      lazy val onClickListener = new AdapterView.OnItemClickListener {
        def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long) = SelectAuth.this.onItemClick(position)
      }

      def tag = "dialog_service_authmode"
      def title = Html.fromHtml(XResource.getString(getSherlockActivity, "service_authmode_title").
        getOrElse("Authentification mode"))
      def message = None
      @Loggable
      override def onResume() {
        authInitialValue = Option(defaultSelection)
        authInitialValue.foreach {
          authInitialValue =>
            currentValue.set(authInitialValue)
            customView.get.map {
              case list: ListView =>
                log.debug("update list attributes")
                list.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE)
                list.setItemChecked(authInitialValue, true)
                list.setOnItemClickListener(onClickListener)
              case view =>
                log.fatal("unexpected view " + view)
            }
        }
        positiveView.get.foreach(_.setEnabled(false))
        super.onResume
      }
      @Loggable
      def onItemClick(n: Int) = positiveView.get.foreach {
        positiveButton =>
          positiveButton.setEnabled(!authInitialValue.exists(_ == n))
          currentValue.set(n)
      }
      @Loggable
      def onPositiveClick() {
        val context = getSherlockActivity
        val authType = SSHDPreferences.AuthentificationType(currentValue.get + 1)
        Futures.future { SSHDPreferences.AuthentificationMode.set(authType, context, true) }
        AuthentificationMode.view.get.foreach(view => {
          val text = view.findViewById(android.R.id.content).asInstanceOf[TextView]
          XResource.getString(getSherlockActivity, "option_auth_" + authType.toString.replaceAll(""" """, """_""")) match {
            case Some(string) =>
              text.setText(string)
            case None =>
              text.setText(authType.toString.toLowerCase.replaceAll(""" """, "\n"))
          }
        })
        // update DefaultUser enable/disable flag
        DefaultUser.view.get.foreach(v => DefaultUser.updateCheckbox(v.findViewById(_root_.android.R.id.checkbox).asInstanceOf[CheckBox]))
      }
      private def defaultSelection: Int =
        Futures.future { SSHDPreferences.AuthentificationMode.get(getSherlockActivity).id - 1 }()
    }
  }
}
