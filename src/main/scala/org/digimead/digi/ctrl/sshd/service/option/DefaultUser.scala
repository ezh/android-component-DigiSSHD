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

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.base.AppControl
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.IAmBusy
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.lib.message.IAmReady
import org.digimead.digi.ctrl.lib.message.Origin.anyRefToOrigin
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.SSHDPreferences
import org.digimead.digi.ctrl.sshd.SSHDUsers
import org.digimead.digi.ctrl.sshd.service.OptionBlock
import org.digimead.digi.ctrl.sshd.service.TabActivity

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.ListView
import android.widget.Toast

object DefaultUser extends CheckBoxItem with Logging {
  val option: DOption.OptVal = DOption.Value("android_user", classOf[Boolean], true: java.lang.Boolean)
  @volatile var android = AppComponent.Context.flatMap(c => SSHDUsers.find(c, "android")) getOrElse { log.fatal("unable to find 'android' user"); null }

  @Loggable
  def onCheckboxClick(view: CheckBox, lastState: Boolean) = TabActivity.activity.foreach {
    activity =>
      if (SSHDPreferences.AuthentificationMode.get(activity) == AuthentificationMode.AuthType.SingleUser)
        AnyBase.runOnUiThread {
          updateCheckbox(view)
          Toast.makeText(view.getContext, "\"android\" is always enabled in single user mode", Toast.LENGTH_SHORT).show()
        }
      else
        AppComponent.Inner.showDialogSafe(activity, "android_user_state", () => {
          val dialog = if (android.enabled)
            SSHDUsers.Dialog.createDialogUserDisable(activity, android, (user) => {
              android = user
              AnyBase.runOnUiThread { view.setChecked(user.enabled) }
            })
          else
            SSHDUsers.Dialog.createDialogUserEnable(activity, android, (user) => {
              android = user
              AnyBase.runOnUiThread { view.setChecked(user.enabled) }
            })
          dialog.show
          dialog
        })
  }
  // fucking android 2.x :-/, shitty puzzles
  def updateCheckbox(view: CheckBox) = {
    view.setFocusable(true)
    view.setFocusableInTouchMode(true)
    view.requestFocus
    view.setChecked(getState[Boolean](view.getContext))
    view.setFocusable(false)
    view.setFocusableInTouchMode(false)
  }
  @Loggable
  override def onListItemClick(l: ListView, v: View) = Futures.future { // leave UI thread
    TabActivity.activity.foreach {
      activity =>
        AppComponent.Inner.showDialogSafe[AlertDialog](activity, "dialog_password", () => {
          SSHDUsers.Dialog.createDialogUserChangePassword(activity, android, (user) => {
            android = user
            if (SSHDPreferences.AuthentificationMode.get(activity) == AuthentificationMode.AuthType.SingleUser &&
              AppComponent.Inner.state.get.value == DState.Active)
              Futures.future {
                AppComponent.Inner.showDialogSafe[AlertDialog](activity, "dialog_restart", () => {
                  val dialog = new AlertDialog.Builder(activity).
                    setIcon(_root_.android.R.drawable.ic_dialog_alert).
                    setTitle(Android.getString(activity, "warning_singleusermode_restart_title").getOrElse("Apply new settings")).
                    setMessage(Android.getString(activity, "session_singleusermode_restart_content").
                      getOrElse("Single user/basic mode provide only restricted abilities of session control.\n\n" +
                        "You must restart SSH before the new settings will take effect. Do you want to restart service now?")).
                    setPositiveButton(_root_.android.R.string.ok, new DialogInterface.OnClickListener() {
                      def onClick(dialog: DialogInterface, whichButton: Int) = {
                        val context = dialog.asInstanceOf[AlertDialog].getContext
                        IAmBusy(DefaultUser, Android.getString(context, "state_stopping_service").getOrElse("stopping service"))
                        Futures.future {
                          AppControl.Inner.callStop(context.getPackageName)() match {
                            case true =>
                              IAmMumble(Android.getString(context, "state_stopped_service").getOrElse("stopped service"))
                              IAmMumble(Android.getString(context, "state_starting_service").getOrElse("starting service"))
                              Futures.future {
                                AppControl.Inner.callStart(context.getPackageName)() match {
                                  case true =>
                                    log.info(context.getPackageName + " started")
                                    IAmReady(DefaultUser, Android.getString(context, "state_started_service").getOrElse("started service"))
                                  case false =>
                                    log.warn(context.getPackageName + " start failed")
                                    IAmReady(DefaultUser, Android.getString(context, "state_started_service").getOrElse("started service"))
                                }
                              } case false =>
                              IAmReady(DefaultUser, Android.getString(context, "state_stopped_service").getOrElse("stopped service"))
                          }
                        }
                      }
                    }).
                    setNegativeButton(_root_.android.R.string.cancel, null).
                    create()
                  dialog.show()
                  dialog
                })
              }
          })
        })
    }
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
    val context = v.getContext
    log.debug("create context menu for " + option.tag)
    menu.setHeaderTitle(Android.getString(context, "android_user_context_menu_title").getOrElse("Manage user \"android\""))
    /*if (item.icon.nonEmpty)
      Android.getId(context, item.icon, "drawable") match {
        case i if i != 0 =>
          menu.setHeaderIcon(i)
        case _ =>
      }*/
    menu.add(Menu.NONE, Android.getId(context, "generate_user_key"), 1,
      Android.getString(context, "generate_user_key").getOrElse("Generate user key"))
    menu.add(Menu.NONE, Android.getId(context, "import_user_key"), 2,
      Android.getString(context, "import_user_key").getOrElse("Import public key"))
    Futures.future { SSHDUsers.Key.getDropbearKeyFile(context, android) }() match {
      case Some(file) if file.exists =>
        menu.add(Menu.NONE, Android.getId(context, "export_user_key_dropbear"), 2,
          Android.getString(context, "export_user_key_dropbear").getOrElse("Export private key (Dropbear)"))
      case _ =>
    }
    Futures.future { SSHDUsers.Key.getOpenSSHKeyFile(context, android) }() match {
      case Some(file) if file.exists =>
        menu.add(Menu.NONE, Android.getId(context, "export_user_key_openssh"), 2,
          Android.getString(context, "export_user_key_openssh").getOrElse("Export private key (OpenSSH)"))
      case _ =>
    }
  }
  override def onContextItemSelected(menuItem: MenuItem): Boolean = TabActivity.activity.map {
    activity =>
      menuItem.getItemId match {
        case id if id == Android.getId(activity, "generate_user_key") =>
          Futures.future {
            SSHDUsers.Key.getDropbearKeyFile(activity, android).foreach(file =>
              OptionBlock.checkKeyAlreadyExists(activity, "User", file,
                (activity) => generateUserKey(activity)))
          }
          true
        case id if id == Android.getId(activity, "import_user_key") =>
          Futures.future { SSHDUsers.Key.importKey(activity, android) }
          true
        case id if id == Android.getId(activity, "export_user_key_dropbear") =>
          Futures.future { SSHDUsers.Key.exportDropbearKey(activity, android) }
          true
        case id if id == Android.getId(activity, "export_user_key_openssh") =>
          Futures.future { SSHDUsers.Key.exportOpenSSHKey(activity, android) }
          true
        case item =>
          log.fatal("skip unknown menu! item " + item)
          false
      }
  } getOrElse false
  override def getState[T](context: Context)(implicit m: Manifest[T]): T = {
    assert(m.erasure == option.kind)
    if (SSHDPreferences.AuthentificationMode.get(context) == AuthentificationMode.AuthType.SingleUser)
      true.asInstanceOf[T] // android user always enable in single user mode
    else
      android.enabled.asInstanceOf[T]
  }
  private def generateUserKey(activity: Activity) {
    AppComponent.Inner.showDialogSafe(activity, "android_user_gen_key", () => {
      val dialog = SSHDUsers.Dialog.createDialogGenerateUserKey(activity, android)
      dialog.show
      dialog
    })
  }
  override def getView(context: Context, inflater: LayoutInflater): View = {
    val view = super.getView(context, inflater)
    val checkbox = view.findViewById(_root_.android.R.id.checkbox).asInstanceOf[CheckBox]
    checkbox.setClickable(true)
    checkbox.setOnTouchListener(new View.OnTouchListener {
      override def onTouch(v: View, event: MotionEvent): Boolean = {
        if (event.getAction() == MotionEvent.ACTION_DOWN)
          Futures.future { onCheckboxClick(v.asInstanceOf[CheckBox], getState[Boolean](v.getContext)) }
        true
      }
    })
    view
  }
}
