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

package org.digimead.digi.ctrl.sshd.user

import java.io.File
import java.util.ArrayList

import scala.Option.option2Iterable
import scala.collection.JavaConversions._

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.base.AppControl
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.info.UserInfo
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.sshd.R

import android.content.Context
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.TextView

object UserAdapter extends Logging {
  private val nameMaximumLength = 16
  private lazy val adapter: Option[ArrayAdapter[UserInfo]] = AppComponent.Context map {
    context =>
      val userPref = context.getSharedPreferences(DPreference.Users, Context.MODE_PRIVATE)
      val users = userPref.getAll.map({
        case (name, data) => try {
          Common.unparcelFromArray[UserInfo](Base64.decode(data.asInstanceOf[String], Base64.DEFAULT),
            UserInfo.getClass.getClassLoader)
        } catch {
          case e =>
            log.warn(e.getMessage, e)
            None
        }
      }).flatten.toList
      Some(new ArrayAdapter[UserInfo](context, R.layout.element_users_row, android.R.id.text1,
        new ArrayList[UserInfo](checkAndroidUserInfo(users).sortBy(_.name))) {
        override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
          val item = getItem(position)
          val view = super.getView(position, convertView, parent)
          val text1 = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
          val text2 = view.findViewById(android.R.id.text2).asInstanceOf[TextView]
          val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckedTextView]
          text1.setText(item.name)
          text2.setText(Android.getString(view.getContext, "users_home_at").getOrElse("Home at '%s'").format(item.home))
          checkbox.setChecked(item.enabled)
          view
        }
      })
      None
  } getOrElse { log.fatal("unable to create SSHDUsers adapter"); None }
  def list(): List[UserInfo] = adapter.map(adapter =>
    (for (i <- 0 until adapter.getCount) yield adapter.getItem(i)).toList).getOrElse(List())
  @Loggable
  def find(context: Context, name: String): Option[UserInfo] = {
    context.getSharedPreferences(DPreference.Users, Context.MODE_PRIVATE).getString(name, null) match {
      case data: String =>
        Common.unparcelFromArray[UserInfo](Base64.decode(data.asInstanceOf[String], Base64.DEFAULT), UserInfo.getClass.getClassLoader)
      case null if name == "android" =>
        checkAndroidUserInfo(List()).find(_.name == "android")
      case null =>
        None
    }
  }
  @Loggable
  def save(context: Context, user: UserInfo) {
    val userPref = context.getSharedPreferences(DPreference.Users, Context.MODE_PRIVATE)
    val editor = userPref.edit
    editor.putString(user.name, Base64.encodeToString(Common.parcelToArray(user), Base64.DEFAULT))
    editor.commit
  }
  @Loggable
  def remove(context: Context, user: UserInfo) {
    val userPref = context.getSharedPreferences(DPreference.Users, Context.MODE_PRIVATE)
    val editor = userPref.edit
    editor.remove(user.name)
    editor.commit
  }
  @Loggable
  def getUserUID(context: Context, user: UserInfo): Option[Int] = synchronized {
    None
  }
  @Loggable
  def setUserUID(context: Context, user: UserInfo, uid: Option[Int]): Unit = synchronized {
    None
  }
  @Loggable
  def getUserGID(context: Context, user: UserInfo): Option[Int] = synchronized {
    None
  }
  @Loggable
  def setUserGID(context: Context, user: UserInfo, gid: Option[Int]): Unit = synchronized {
    None
  }
  @Loggable
  def setPasswordEnabled(enabled: Boolean, context: Context, user: UserInfo) = synchronized {
    val userPEnabledPref = context.getSharedPreferences(DPreference.Users + "@penabled", Context.MODE_PRIVATE)
    val editor = userPEnabledPref.edit
    if (enabled)
      editor.putBoolean(user.name, true)
    else
      editor.putBoolean(user.name, false)
    editor.commit
  }
  @Loggable
  def isPasswordEnabled(context: Context, user: UserInfo): Boolean = synchronized {
    context.getSharedPreferences(DPreference.Users + "@penabled", Context.MODE_PRIVATE).
      getBoolean(user.name, true)
  }
  @Loggable
  def homeDirectory(context: Context, user: UserInfo): File =
    AppControl.Inner.getExternalDirectory(DTimeout.long) getOrElse new File("/")
  @Loggable
  private def checkAndroidUserInfo(in: List[UserInfo]): List[UserInfo] = if (!in.exists(_.name == "android")) {
    log.debug("add default system user \"android\"")
    in :+ UserInfo("android", "123", "variable location", true)
  } else
    in
}
