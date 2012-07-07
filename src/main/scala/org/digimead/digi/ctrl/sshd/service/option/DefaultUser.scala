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
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.SSHDUsers
import org.digimead.digi.ctrl.sshd.service.OptionBlock
import org.digimead.digi.ctrl.sshd.service.TabActivity

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.ListView

object DefaultUser extends CheckBoxItem with Logging {
  val option: DOption.OptVal = DOption.Value("android_user", classOf[Boolean], true: java.lang.Boolean)
  @volatile var android = AppComponent.Context.flatMap(c => SSHDUsers.find(c, "android")) getOrElse { log.fatal("unable to find 'android' user"); null }

  @Loggable
  def onCheckboxClick(view: CheckBox, lastState: Boolean) = TabActivity.activity.foreach {
    activity =>
      AppComponent.Inner.showDialogSafe(activity, "android_user_state", () => {
        val dialog = if (lastState)
          SSHDUsers.Dialog.createDialogUserDisable(activity, android, (user) => {
            android = user
            AnyBase.handler.post(new Runnable { def run = view.setChecked(user.enabled) })
          })
        else
          SSHDUsers.Dialog.createDialogUserEnable(activity, android, (user) => {
            android = user
            AnyBase.handler.post(new Runnable { def run = view.setChecked(user.enabled) })
          })
        dialog.show
        dialog
      })
  }
  @Loggable
  override def onListItemClick(l: ListView, v: View) = Futures.future { // leave UI thread
    TabActivity.activity.foreach {
      activity =>
        AppComponent.Inner.showDialogSafe[AlertDialog](activity, "dialog_password", () => {
          SSHDUsers.Dialog.createDialogUserChangePassword(activity, android, (user) => android = user)
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
    android.enabled.asInstanceOf[T]
  }
  private def generateUserKey(activity: Activity) {
    AppComponent.Inner.showDialogSafe(activity, "android_user_gen_key", () => {
      val dialog = SSHDUsers.Dialog.createDialogGenerateUserKey(activity, android)
      dialog.show
      dialog
    })
  }
}
