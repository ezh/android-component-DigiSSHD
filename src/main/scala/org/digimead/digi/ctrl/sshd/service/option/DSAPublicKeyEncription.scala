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

import org.digimead.digi.lib.ctrl.ext.XResource
import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.ctrl.base.AppControl
import org.digimead.digi.lib.ctrl.declaration.DOption
import org.digimead.digi.lib.ctrl.declaration.DPreference
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.ctrl.sshd.service.TabContent

import android.content.Context
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
      false // prevent RSA and DSS a simultaneous shutdown
    else
      true
    if (allow) {
      Futures.future {
        val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
        val editor = pref.edit()
        editor.putBoolean(option.tag, !lastState)
        editor.commit()
      }
      view.setChecked(!lastState)
    } else {
      val message = XResource.getString(context, "option_rsa_dss_at_least_one").getOrElse("at least one of the encription type must be selected from either RSA or DSA")
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
    menu.setHeaderIcon(android.R.drawable.ic_menu_preferences)
    val generateKeyItem = menu.add(Menu.NONE, XResource.getId(context, "generate_host_key"), 1,
      XResource.getString(context, "generate_host_key").getOrElse("Generate host key"))
    val importKeyItem = menu.add(Menu.NONE, XResource.getId(context, "import_host_key"), 2,
      XResource.getString(context, "import_host_key").getOrElse("Import host key"))
    val exportKeyItem = menu.add(Menu.NONE, XResource.getId(context, "export_host_key"), 2,
      XResource.getString(context, "export_host_key").getOrElse("Export host key"))
    // disable unavailable items
    if (!AppControl.isBound) {
      generateKeyItem.setEnabled(false)
      importKeyItem.setEnabled(false)
      exportKeyItem.setEnabled(false)
    }
  }
  override def onContextItemSelected(menuItem: MenuItem): Boolean = TabContent.fragment.map {
    fragment =>
      val context = fragment.getActivity
      menuItem.getItemId match {
        case id if id == XResource.getId(context, "generate_host_key") =>
          /*          Futures.future { UserDialog.generateKey(context, android, UserKeys.getDropbearKeyFile(context, android)) }
          Futures.future {
            getSourceKeyFile().foreach(file =>
              UserDialog.checkKeyAlreadyExists(context, "DSA host", file,
                () => generateHostKey(activity)))
          }*/
          true
        case id if id == XResource.getId(context, "import_host_key") =>
          /*          Futures.future {
            getSourceKeyFile().foreach(file =>
              UserDialog.checkKeyAlreadyExists(context, "DSA host", file,
                (activity) => importHostKey(activity)))
          }*/
          true
        case id if id == XResource.getId(context, "export_host_key") =>
          Futures.future { exportHostKey(context) }
          true
        case item =>
          log.fatal("skip unknown menu! item " + item)
          false
      }
  } getOrElse false
}
