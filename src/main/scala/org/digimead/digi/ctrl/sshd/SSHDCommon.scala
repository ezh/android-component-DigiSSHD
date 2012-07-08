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

package org.digimead.digi.ctrl.sshd

import scala.actors.Futures
import scala.collection.mutable.Subscriber

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.base.AppComponentEvent
import org.digimead.digi.ctrl.lib.base.AppControl
import org.digimead.digi.ctrl.lib.block.Level
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.log.RichLogger
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.message.IAmWarn
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.Message.dispatcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.text.Html
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

object SSHDCommon extends Logging {
  // AppComponent global state subscriber
  val globalStateSubscriber = new Subscriber[AppComponentEvent, AppComponent.type#Pub] {
    def notify(pub: AppComponent.type#Pub, event: AppComponentEvent) =
      if (AppControl.Inner != null && AppControl.Inner.isAvailable == Some(true)) {
        event match {
          case AppComponent.Event.Resume =>
            IAmWarn("DigiSSHD resume")
            // leave UI thread
            Futures.future { Option(AppControl.Inner).foreach(_.callUpdateShutdownTimer(getClass.getPackage.getName, -1)) }
          case AppComponent.Event.Suspend(timeout) =>
            IAmWarn("DigiSSHD suspend, shutdown timer is " + timeout + "s")
            // leave UI thread
            Futures.future {
              var remain = timeout
              val step = 5000
              while ((AppComponent.isSuspend || AppControl.isSuspend) && remain > 0) {
                Option(AppControl.Inner).foreach(_.callUpdateShutdownTimer(getClass.getPackage.getName, remain))
                Thread.sleep(step)
                remain -= step
              }
            }
          case AppComponent.Event.Shutdown =>
            IAmWarn("DigiSSHD shutdown")
            // leave UI thread
            Futures.future { Option(AppControl.Inner).foreach(_.callUpdateShutdownTimer(getClass.getPackage.getName, 0)) }
          case _ =>
        }
      } else
        log.debug("skip event " + event + ", ICtrlHost not binded")
  }
  AppComponent.subscribe(globalStateSubscriber)
  log.debug("alive")

  def optionChangedOnRestartNotify(context: Context, option: DOption#OptVal, state: String)(implicit logger: RichLogger, dispatcher: Dispatcher) {
    if (AppComponent.Inner.state.get.value == DState.Passive) {
      val message = Android.getString(context, "option_changed").getOrElse("%1$s set to %2$s").format(option.name(context), state)
      AnyBase.runOnUiThread { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    } else {
      val message = Android.getString(context, "option_changed_on_restart").getOrElse("%1$s set to %2$s, it will be applied on the next run").format(option.name(context), state)
      AnyBase.runOnUiThread { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
  }
  def optionChangedNotify(context: Activity, option: DOption.OptVal, state: String)(implicit logger: RichLogger, dispatcher: Dispatcher) {
    val message = Android.getString(context, "option_changed").getOrElse("%1$s set to %2$s").format(option.name(context), state)
    AnyBase.runOnUiThread { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
  }
  def showHelpDialog(context: Activity) = {
    AppComponent.Inner.showDialogSafe[AlertDialog](context, "dialog_help", () => {
      val container = new ScrollView(context)
      container.setPadding(10, 10, 10, 10)
      val subContainer = new LinearLayout(context)
      subContainer.setOrientation(LinearLayout.VERTICAL)
      container.addView(subContainer)
      // message
      val message = new TextView(context)
      message.setId(android.R.id.message)
      message.setText(Html.fromHtml(Android.getString(context, "dialog_help_message").getOrElse("All elemenst marked with level markers:")))
      subContainer.addView(message)
      // list
      val inflater = context.getLayoutInflater
      // row1
      val row1 = inflater.inflate(android.R.layout.simple_list_item_2, null)
      Level.novice(row1)
      row1.findViewById(android.R.id.text1).asInstanceOf[TextView].setText(
        Html.fromHtml(Android.getString(context, "dialog_help_novice_mark_title").getOrElse("Novice level")))
      row1.findViewById(android.R.id.text2).asInstanceOf[TextView].setText(
        Html.fromHtml(Android.getString(context, "dialog_help_novice_mark_text").
          getOrElse("<font color='green'>Novice level</font> - common group of elements, that satisfy requirements to base control of component like start/stop")))
      subContainer.addView(row1)
      // row2
      val row2 = inflater.inflate(android.R.layout.simple_list_item_2, null)
      Level.intermediate(row2)
      row2.findViewById(android.R.id.text1).asInstanceOf[TextView].setText(
        Html.fromHtml(Android.getString(context, "dialog_help_intermediate_mark_title").getOrElse("Intermediate level")))
      row2.findViewById(android.R.id.text2).asInstanceOf[TextView].setText(
        Html.fromHtml(Android.getString(context, "dialog_help_intermediate_mark_text").
          getOrElse("<font color='yellow'>Intermediate level</font> - group of elements, that allow tune component behaviour or provide additional actions but requires reasonable level of knowledge")))
      subContainer.addView(row2)
      // row3
      val row3 = inflater.inflate(android.R.layout.simple_list_item_2, null)
      Level.professional(row3)
      row3.findViewById(android.R.id.text1).asInstanceOf[TextView].setText(
        Html.fromHtml(Android.getString(context, "dialog_help_professional_mark_title").getOrElse("Professional level")))
      row3.findViewById(android.R.id.text2).asInstanceOf[TextView].setText(
        Html.fromHtml(Android.getString(context, "dialog_help_intermediate_mark_text").
          getOrElse("<font color='red'>Professional level</font> - group of elements, that specific to particular Digi component and provide advanced functions like ACL")))
      subContainer.addView(row3)
      // checkbox
      val checkBox = new CheckBox(context)
      checkBox.setText(Html.fromHtml(Android.getString(context, "dialog_help_show_tips").getOrElse("show tips")))
      //      subContainer.addView(checkBox)
      // dialog
      val dialog = new AlertDialog.Builder(context).
        setTitle(R.string.dialog_help_title).
        setView(container).
        setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
          def onClick(dialog: DialogInterface, whichButton: Int) {}
        }).
        setIcon(R.drawable.ic_menu_help).
        create()
      dialog.setOnShowListener(new DialogInterface.OnShowListener {
        def onShow(dialog: DialogInterface) {
          val text = dialog.asInstanceOf[AlertDialog].findViewById(android.R.id.message).asInstanceOf[TextView]
          val padding = 5
          text.setMinHeight(Resources.ic_assistant.getIntrinsicHeight + padding * 2)
          // origin.getIntrinsicWidth, origin.getIntrinsicHeight prevent bug in 2.x
          Android.addLeadingDrawable(text, Resources.ic_assistant, padding,
            Resources.ic_assistant.getIntrinsicWidth, Resources.ic_assistant.getIntrinsicHeight)
        }
      })
      dialog.show()
      dialog
    })
  }
  object Resources {
    lazy val ic_assistant = SSHDActivity.activity.get.getResources.getDrawable(R.drawable.ic_assistant)
  }
}
