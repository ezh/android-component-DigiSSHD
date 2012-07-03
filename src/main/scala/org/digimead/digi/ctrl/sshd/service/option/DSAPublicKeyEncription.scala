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
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.SSHDCommon
import org.digimead.digi.ctrl.sshd.service.OptionBlock
import org.digimead.digi.ctrl.sshd.service.TabActivity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.ListView
import android.widget.Toast

object DSAPublicKeyEncription extends CheckBoxItem with PublicKey with Logging {
  val option = DOption.Value("dsa", classOf[Boolean], false: java.lang.Boolean)

  @Loggable
  def onCheckboxClick(view: CheckBox, lastState: Boolean) = {
    val context = view.getContext
    val allow = if (!RSAPublicKeyEncription.getState[Boolean](context) && getState[Boolean](context))
      false // prevent RSA and DSS simultaneous shutdown
    else
      true
    if (allow) {
      val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
      val editor = pref.edit()
      editor.putBoolean(option.tag, !lastState)
      editor.commit()
      view.setChecked(!lastState)
      context.sendBroadcast(new Intent(DIntent.UpdateOption, Uri.parse("code://" + context.getPackageName + "/" + option)))
      SSHDCommon.optionChangedOnRestartNotify(context, option, getState[Boolean](context).toString)
    } else {
      val message = Android.getString(context, "option_rsa_dss_at_least_one").getOrElse("at least one of the encription type must be selected from either RSA or DSA")
      view.setChecked(getState[Boolean](context))
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
  }
  @Loggable
  def onListItemClick(l: ListView, v: View) =
    view.get.foreach {
      view =>
        onCheckboxClick(view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox], getState[Boolean](view.getContext))
    }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
    val context = v.getContext
    log.debug("create context menu for " + option.tag)
    menu.setHeaderTitle(option.tag.toUpperCase)
    /*if (item.icon.nonEmpty)
      Android.getId(context, item.icon, "drawable") match {
        case i if i != 0 =>
          menu.setHeaderIcon(i)
        case _ =>
      }*/
    menu.add(Menu.NONE, Android.getId(context, "generate_host_key"), 1,
      Android.getString(context, "generate_host_key").getOrElse("Generate host key"))
    menu.add(Menu.NONE, Android.getId(context, "import_host_key"), 2,
      Android.getString(context, "import_host_key").getOrElse("Import host key"))
    menu.add(Menu.NONE, Android.getId(context, "export_host_key"), 2,
      Android.getString(context, "export_host_key").getOrElse("Export host key"))
  }
  override def onContextItemSelected(menuItem: MenuItem): Boolean = TabActivity.activity.map {
    activity =>
      menuItem.getItemId match {
        case id if id == Android.getId(activity, "generate_host_key") =>
          Futures.future {
            getSourceKeyFile().foreach(file =>
              OptionBlock.checkKeyAlreadyExists(activity, "DSA host", file,
                (activity) => generateHostKey(activity)))
          }
          true
        case id if id == Android.getId(activity, "import_host_key") =>
          Futures.future {
            getSourceKeyFile().foreach(file =>
              OptionBlock.checkKeyAlreadyExists(activity, "DSA host", file,
                (activity) => importHostKey(activity)))
          }
          true
        case id if id == Android.getId(activity, "export_host_key") =>
          Futures.future { exportHostKey(activity) }
          true
        case item =>
          log.fatal("skip unknown menu! item " + item)
          false
      }
  } getOrElse false
}
