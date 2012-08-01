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

import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDPreferences
import org.digimead.digi.ctrl.sshd.ext.SSHDDialog
import org.digimead.digi.ctrl.sshd.ext.SSHDDialog.dialog2string
import org.digimead.digi.ctrl.sshd.service.TabContent

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView

object AuthentificationMode extends TextViewItem with Logging {
  val option = SSHDPreferences.DOption.AuthentificationMode

  @Loggable
  override def onListItemClick(l: ListView, v: View) = for {
    fragment <- TabContent.fragment
    dialog <- AuthentificationMode.Dialog.selectAuth
  } if (dialog.isShowing)
    log.debug(dialog + " already shown")
  else
    dialog.show(fragment)
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
    lazy val selectAuth = AppComponent.Context.map(context =>
      Fragment.instantiate(context.getApplicationContext, classOf[SelectAuth].getName, null).asInstanceOf[SelectAuth])
    class SelectAuth extends SSHDDialog with Logging {
      private lazy val currentValue = new AtomicInteger(getState[Int](getSherlockActivity))
      @volatile private var initialValue: Option[Int] = None
      @volatile private var ok = new WeakReference[Button](null)
      private lazy val cachedDialog = {
        val dialog = new AlertDialog.Builder(getSherlockActivity).
          setIcon(android.R.drawable.ic_menu_preferences).
          setTitle(R.string.dialog_auth_title).
          setSingleChoiceItems(R.array.auth_type, defaultSelection, onChoiseListener).
          setPositiveButton(android.R.string.ok, positiveButtonListener).
          setNegativeButton(android.R.string.cancel, negativeButtonListener).
          create()
        dialog.show
        dialog
      }
      private lazy val cachedEmbedded = {
        val context = getSherlockActivity
        val view = LayoutInflater.from(context).inflate(R.layout.fragment_dialog_list, null)
        val list = view.findViewById(android.R.id.custom).asInstanceOf[ListView]
        list.setAdapter(ArrayAdapter.createFromResource(context, R.array.auth_type, android.R.layout.simple_list_item_single_choice))
        list.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE)
        list.setOnItemClickListener(new AdapterView.OnItemClickListener {
          def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long) =
            onChoiseListener.onClick(null, position)
        })
        val title = view.findViewById(android.R.id.title).asInstanceOf[TextView]
        title.setText(R.string.dialog_auth_title)
        val icon = view.findViewById(android.R.id.icon).asInstanceOf[ImageView]
        icon.setImageResource(android.R.drawable.ic_menu_preferences)
        icon.setVisibility(View.VISIBLE)
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
      private lazy val cachedEmbeddedAttr = XResource.getAttributeSet(getSherlockActivity, R.layout.fragment_dialog_list)
      private lazy val positiveButtonListener = new SelectAuth.PositiveButtonListener(new WeakReference(this))
      private lazy val negativeButtonListener = new SelectAuth.NegativeButtonListener(new WeakReference(this))
      private lazy val onChoiseListener = new SelectAuth.OnChoiceListener(new WeakReference(this))

      def tag = "dialog_authmode"
      @Loggable
      override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = if (getShowsDialog) {
        null
      } else {
        val context = getSherlockActivity
        Option(cachedEmbedded.getParent).foreach(_.asInstanceOf[ViewGroup].removeView(cachedEmbedded))
        cachedEmbeddedAttr.foreach(attr => cachedEmbedded.setLayoutParams(container.generateLayoutParams(attr)))
        val currentValue = SSHDPreferences.AuthentificationMode.get(getSherlockActivity).id
        initialValue = Some(currentValue)
        cachedEmbedded.findViewById(android.R.id.custom).asInstanceOf[ListView].
          setItemChecked(defaultSelection, true)
        ok = new WeakReference(cachedEmbedded.findViewById(android.R.id.button2).asInstanceOf[Button])
        ok.get.foreach(_.setEnabled(false))
        cachedEmbedded
      }
      @Loggable
      override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
        cachedDialog.show
        val currentValue = SSHDPreferences.AuthentificationMode.get(getSherlockActivity).id
        initialValue = Some(currentValue)
        cachedDialog.getListView.setItemChecked(defaultSelection, true)
        ok = new WeakReference(cachedDialog.findViewById(android.R.id.button1).asInstanceOf[Button])
        ok.get.foreach(_.setEnabled(false))
        cachedDialog
      }
      private def innerView(context: Context) = {
        val context = getSherlockActivity
        val view = new ListView(context)
        view.setId(android.R.id.list)
        view.setAdapter(ArrayAdapter.createFromResource(context, R.array.auth_type, android.R.layout.simple_list_item_single_choice))
        view.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE)
        view
      }
      private def defaultSelection =
        SSHDPreferences.AuthentificationMode.get(getSherlockActivity).id - 1
    }
    object SelectAuth {
      class PositiveButtonListener(dialog: WeakReference[SelectAuth]) extends DialogInterface.OnClickListener() {
        def onClick(dialogInterface: DialogInterface, whichButton: Int) = try {
          dialog.get.foreach {
            dialog =>
              log.debug("change authentification mode")
              val context = dialog.getSherlockActivity
              val authType = SSHDPreferences.AuthentificationType(dialog.currentValue.get)
              Futures.future { SSHDPreferences.AuthentificationMode.set(authType, context, true) }
              AuthentificationMode.view.get.foreach(view => {
                val text = view.findViewById(android.R.id.content).asInstanceOf[TextView]
                XResource.getString(dialog.getSherlockActivity, "option_auth_" + authType.toString.replaceAll(""" """, """_""")) match {
                  case Some(string) =>
                    text.setText(string)
                  case None =>
                    text.setText(authType.toString.toLowerCase.replaceAll(""" """, "\n"))
                }
              })
              // update DefaultUser enable/disable flag
              DefaultUser.view.get.foreach(v => DefaultUser.updateCheckbox(v.findViewById(_root_.android.R.id.checkbox).asInstanceOf[CheckBox]))
              if (!dialog.getShowsDialog)
                dialog.getSherlockActivity.getSupportFragmentManager.
                  popBackStackImmediate(dialog, FragmentManager.POP_BACK_STACK_INCLUSIVE)
          }
        } catch {
          case e =>
            log.error(e.getMessage, e)
        }
      }
      class NegativeButtonListener(dialog: WeakReference[SelectAuth]) extends DialogInterface.OnClickListener() {
        def onClick(dialogInterface: DialogInterface, whichButton: Int) = try {
          dialog.get.foreach(dialog => if (!dialog.getShowsDialog)
            dialog.getSherlockActivity.getSupportFragmentManager.
            popBackStackImmediate(dialog, FragmentManager.POP_BACK_STACK_INCLUSIVE))
        } catch {
          case e =>
            log.error(e.getMessage, e)
        }
      }
      class OnChoiceListener(dialog: WeakReference[SelectAuth]) extends DialogInterface.OnClickListener() {
        def onClick(dialogInterface: DialogInterface, which: Int) = try {
          dialog.get.foreach {
            dialog =>
              dialog.currentValue.set(which + 1)
              dialog.ok.get.foreach(_.setEnabled(!dialog.initialValue.exists(_ == dialog.currentValue.get)))
          }
        } catch {
          case e =>
            log.error(e.getMessage, e)
        }
      }
    }
  }
}
