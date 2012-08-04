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
import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDPreferences
import org.digimead.digi.ctrl.sshd.ext.SSHDDialog
import org.digimead.digi.ctrl.sshd.service.TabContent

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.text.Editable
import android.text.Html
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView

object NetworkPort extends TextViewItem with Logging {
  val option = SSHDPreferences.NetworkPort.option

  @Loggable
  override def onListItemClick(l: ListView, v: View) = for {
    fragment <- TabContent.fragment
    dialog <- NetworkPort.Dialog.selectPort
  } if (dialog.isShowing)
    log.debug(dialog + " already shown")
  else
    dialog.show(fragment)
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
    lazy val selectPort = AppComponent.Context.map(context =>
      Fragment.instantiate(context.getApplicationContext, classOf[SelectPort].getName, null).asInstanceOf[SelectPort])
    class SelectPort extends SSHDDialog with Logging {
      @volatile private var initialValue: Option[Int] = None
      @volatile private var ok = new WeakReference[Button](null)
      @volatile private var port = new WeakReference[EditText](null)
      private lazy val cachedDialog = {
        val dialog = new AlertDialog.Builder(getSherlockActivity).
          setIcon(android.R.drawable.ic_menu_preferences).
          setTitle(R.string.dialog_port_title).
          setMessage(content(getSherlockActivity)).
          setPositiveButton(android.R.string.ok, positiveButtonListener).
          setNegativeButton(android.R.string.cancel, negativeButtonListener).
          setView(innerView(getSherlockActivity)).
          create()
        dialog.show
        dialog.findViewById(android.R.id.edit).asInstanceOf[EditText].
          addTextChangedListener(portFieldTextChangedListener)
        dialog
      }
      private lazy val cachedEmbedded = {
        val context = getSherlockActivity
        val view = LayoutInflater.from(context).inflate(R.layout.fragment_dialog, null)
        val content = view.findViewById(android.R.id.custom).asInstanceOf[TextView]
        content.getParent.asInstanceOf[ViewGroup].addView(innerView(context), 1)
        val title = view.findViewById(android.R.id.title).asInstanceOf[TextView]
        title.setText(R.string.dialog_port_title)
        val icon = view.findViewById(android.R.id.icon).asInstanceOf[ImageView]
        icon.setImageResource(android.R.drawable.ic_menu_preferences)
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
      private lazy val positiveButtonListener = new SelectPort.PositiveButtonListener(new WeakReference(this))
      private lazy val negativeButtonListener = new SelectPort.NegativeButtonListener(new WeakReference(this))
      private lazy val portFieldTextChangedListener = new SelectPort.PortFieldTextChangedListener(new WeakReference(this))

      def tag = "dialog_selectport"
      @Loggable
      override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = if (getShowsDialog) {
        null
      } else {
        val context = getSherlockActivity
        Option(cachedEmbedded.getParent).foreach(_.asInstanceOf[ViewGroup].removeView(cachedEmbedded))
        cachedEmbeddedAttr.foreach(attr => cachedEmbedded.setLayoutParams(container.generateLayoutParams(attr)))
        val currentValue = SSHDPreferences.NetworkPort.get(context)
        initialValue = Some(currentValue)
        ok = new WeakReference(cachedEmbedded.findViewById(android.R.id.button2).asInstanceOf[Button])
        ok.get.foreach(_.setEnabled(false))
        port = new WeakReference(cachedEmbedded.findViewById(android.R.id.edit).asInstanceOf[EditText])
        port.get.foreach(_.setText(currentValue.toString))
        cachedEmbedded
      }
      @Loggable
      override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
        cachedDialog.show
        val currentValue = SSHDPreferences.NetworkPort.get(getSherlockActivity)
        initialValue = Some(currentValue)
        ok = new WeakReference(cachedDialog.findViewById(android.R.id.button1).asInstanceOf[Button])
        ok.get.foreach(_.setEnabled(false))
        port = new WeakReference(cachedDialog.findViewById(android.R.id.edit).asInstanceOf[EditText])
        port.get.foreach(_.setText(currentValue.toString))
        cachedDialog
      }
      private def innerView(context: Context) = {
        val context = getSherlockActivity
        val maxLengthFilter = new InputFilter.LengthFilter(5)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_edittext, null)
        val portField = view.findViewById(android.R.id.edit).asInstanceOf[EditText]
        portField.setInputType(InputType.TYPE_CLASS_NUMBER)
        portField.setFilters(Array(maxLengthFilter))
        portField.addTextChangedListener(new SelectPort.PortFieldTextChangedListener(new WeakReference(this)))
        view
      }
      private def content(context: Context) = Html.fromHtml(XResource.getString(context, "dialog_port_message").
        getOrElse("Select new TCP port in range from 1 to 65535"))
    }
    object SelectPort {
      class PositiveButtonListener(dialog: WeakReference[SelectPort]) extends DialogInterface.OnClickListener() {
        def onClick(dialogInterface: DialogInterface, whichButton: Int) = try {
          for {
            dialog <- dialog.get
            port <- dialog.port.get
          } {
            val newValue = port.getText.toString.toInt
            NetworkPort.view.get.foreach(view => {
              val text = view.findViewById(android.R.id.content).asInstanceOf[TextView]
              text.setText(newValue.toString)
            })
            Futures.future { SSHDPreferences.NetworkPort.set(newValue, dialog.getSherlockActivity) }
            if (!dialog.getShowsDialog)
              dialog.getSherlockActivity.getSupportFragmentManager.
                popBackStackImmediate(dialog, FragmentManager.POP_BACK_STACK_INCLUSIVE)
          }
        } catch {
          case e =>
            log.error(e.getMessage, e)
        }
      }
      class NegativeButtonListener(dialog: WeakReference[SelectPort]) extends DialogInterface.OnClickListener() {
        def onClick(dialogInterface: DialogInterface, whichButton: Int) = try {
          dialog.get.foreach(dialog => if (!dialog.getShowsDialog)
            dialog.getSherlockActivity.getSupportFragmentManager.
            popBackStackImmediate(dialog, FragmentManager.POP_BACK_STACK_INCLUSIVE))
        } catch {
          case e =>
            log.error(e.getMessage, e)
        }
      }
      class PortFieldTextChangedListener(dialog: WeakReference[SelectPort]) extends TextWatcher {
        override def afterTextChanged(s: Editable) = try {
          for {
            dialog <- dialog.get
            ok <- dialog.ok.get
            initialValue <- dialog.initialValue
          } {
            val rawPort = s.toString
            if (rawPort.nonEmpty) {
              val port = rawPort.toInt
              if (port > 1 && port < 65536 && port != initialValue)
                ok.setEnabled(true)
              else
                ok.setEnabled(false)
            } else
              ok.setEnabled(false)
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
