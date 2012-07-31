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

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.androidext.SafeDialog
import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDPreferences
import org.digimead.digi.ctrl.sshd.ext.SherlockSafeDialogFragment
import org.digimead.digi.ctrl.sshd.ext.SherlockSafeDialogFragment.dialog2string
import org.digimead.digi.ctrl.sshd.service.TabContent

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView

object GrantSuperuserPermission extends CheckBoxItem with Logging {
  val option: DOption.OptVal = SSHDPreferences.AsRoot.option

  @Loggable
  def onCheckboxClick(view: CheckBox, lastState: Boolean) =
    if (lastState) {
      Futures.future { SSHDPreferences.AsRoot.set(false, view.getContext, true) }
      if (view.isChecked)
        view.setChecked(false)
    } else for {
      fragment <- TabContent.fragment
      dialog <- GrantSuperuserPermission.Dialog.rootRequest
    } if (dialog.isShowing) {
      log.debug(dialog + " already shown")
    } else {
      val manager = fragment.getSherlockActivity.getSupportFragmentManager
      if (manager.findFragmentByTag(dialog) == null || !fragment.isTopPanelAvailable) {
        log.debug("show " + dialog)
        SafeDialog.transaction.prepend((ft, fragment, target) => {
          ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
          ft.addToBackStack(dialog)
        }).show(fragment.getSherlockActivity, Some(R.id.main_topPanel), dialog, () => dialog)
      } else {
        log.debug("restore " + dialog)
        manager.popBackStack(dialog, 0)
      }
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
      private lazy val cachedDialog = {
        val dialog = new AlertDialog.Builder(getSherlockActivity).
          setIcon(R.drawable.ic_danger).
          setTitle(R.string.dialog_root_title).
          setMessage(content(getSherlockActivity)).
          setPositiveButton(android.R.string.ok, positiveButtonListener).
          setNegativeButton(android.R.string.cancel, negativeButtonListener).
          create()
        dialog.show
        dialog
      }
      private lazy val cachedEmbedded = {
        val context = getSherlockActivity
        val view = LayoutInflater.from(context).inflate(R.layout.fragment_dialog, null)
        val content = view.findViewById(android.R.id.custom).asInstanceOf[TextView]
        val title = view.findViewById(android.R.id.title).asInstanceOf[TextView]
        title.setText(R.string.dialog_root_title)
        val icon = view.findViewById(android.R.id.icon).asInstanceOf[ImageView]
        icon.setImageResource(R.drawable.ic_danger)
        icon.setVisibility(View.VISIBLE)
        content.setText(this.content(context))
        view.findViewById(android.R.id.summary).setVisibility(View.VISIBLE)
        val cancel = view.findViewById(android.R.id.button1).asInstanceOf[Button]
        cancel.setVisibility(View.VISIBLE)
        cancel.setText(android.R.string.cancel)
        cancel.setOnClickListener(new View.OnClickListener {
          def onClick(v: View) = negativeButtonListener.onClick(null, 0)
        })
        val ok = view.findViewById(android.R.id.button2).asInstanceOf[Button]
        ok.setOnClickListener(new View.OnClickListener {
          def onClick(v: View) = positiveButtonListener.onClick(null, 0)
        })
        ok.setVisibility(View.VISIBLE)
        ok.setText(android.R.string.ok)
        view
      }
      private lazy val cachedEmbeddedAttr = XResource.getAttributeSet(getSherlockActivity, R.layout.fragment_dialog)
      private lazy val positiveButtonListener = new RootRequest.PositiveButtonListener(new WeakReference(this))
      private lazy val negativeButtonListener = new RootRequest.NegativeButtonListener(new WeakReference(this))

      override def toString = "dialog_rootrequest"
      @Loggable
      override def onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
      }
      @Loggable
      override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = if (getShowsDialog) {
        null
      } else {
        val context = getSherlockActivity
        Option(cachedEmbedded.getParent).foreach(_.asInstanceOf[ViewGroup].removeView(cachedEmbedded))
        cachedEmbeddedAttr.foreach(attr => cachedEmbedded.setLayoutParams(container.generateLayoutParams(attr)))
        cachedEmbedded
      }
      @Loggable
      override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
        cachedDialog.show
        cachedDialog
      }
      def content(context: Context) = Html.fromHtml(XResource.getString(context, "dialog_root_message").
        getOrElse("Are you sure you want to grant superuser permission to native components?\n\n" +
          "Please note that your software must support this function. This option is ignored " +
          "if you have a firmware with limited functionality."))
    }
    object RootRequest {
      class PositiveButtonListener(dialog: WeakReference[RootRequest]) extends DialogInterface.OnClickListener() {
        def onClick(dialogInterface: DialogInterface, whichButton: Int) = try {
          GrantSuperuserPermission.view.get.foreach(view => {
            Futures.future { SSHDPreferences.AsRoot.set(true, view.getContext, true) }
            val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
            if (!checkbox.isChecked)
              checkbox.setChecked(true)
          })
          dialog.get.foreach(dialog => if (!dialog.getShowsDialog)
            dialog.getSherlockActivity.getSupportFragmentManager.
            popBackStackImmediate(dialog, FragmentManager.POP_BACK_STACK_INCLUSIVE))
        } catch {
          case e =>
            log.error(e.getMessage, e)
        }
      }
      class NegativeButtonListener(dialog: WeakReference[RootRequest]) extends DialogInterface.OnClickListener() {
        def onClick(dialogInterface: DialogInterface, whichButton: Int) = try {
          GrantSuperuserPermission.view.get.foreach(view => {
            val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
            if (checkbox.isChecked)
              AnyBase.runOnUiThread { checkbox.setChecked(false) }
          })
          dialog.get.foreach(dialog => if (!dialog.getShowsDialog)
            dialog.getSherlockActivity.getSupportFragmentManager.
            popBackStackImmediate(dialog, FragmentManager.POP_BACK_STACK_INCLUSIVE))
        } catch {
          case e =>
            log.error(e.getMessage, e)
        }
      }
    }
  }
}
