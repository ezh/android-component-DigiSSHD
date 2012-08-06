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
import scala.actors.Futures
import scala.collection.JavaConversions._

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.androidext.XAPI
import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.info.UserInfo
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.IAmYell
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.util.Passwords
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDPreferences
import org.digimead.digi.ctrl.sshd.SSHDService

import android.content.Context
import android.text.Html
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.TextView
import android.widget.Toast

object UserAdapter extends Logging with Passwords {
  private[user] val nameMaximumLength = 16
  private[user] lazy val adapter: Option[ArrayAdapter[UserInfo]] = AppComponent.Context map {
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
          text2.setText(XResource.getString(view.getContext, "users_home_at").getOrElse("Home at '%s'").format(item.home))
          checkbox.setChecked(item.enabled)
          view
        }
      })
  } getOrElse { log.fatal("unable to create SSHDUsers adapter"); None }

  @Loggable
  def isMultiUser(context: Context): Boolean =
    SSHDPreferences.AuthentificationMode.get(context) == SSHDPreferences.AuthentificationType.MultiUser
  @Loggable
  def isSingleUser(context: Context): Boolean =
    SSHDPreferences.AuthentificationMode.get(context) == SSHDPreferences.AuthentificationType.SingleUser
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
  def updateUser(newUser: Option[UserInfo], oldUser: Option[UserInfo]) = for {
    adapter <- adapter
    context <- AppComponent.Context
  } (newUser, oldUser) match {
    case (Some(newUser), Some(oldUser)) =>
      // update
      val position = adapter.getPosition(oldUser)
      if (position >= 0)
        AnyBase.runOnUiThread {
          adapter.remove(oldUser)
          adapter.insert(newUser, position)
        }
      else
        log.fatal("previous user " + oldUser + " not found")
      if (newUser.name != oldUser.name)
        remove(context, oldUser)
      save(context, newUser)
    case (None, Some(oldUser)) =>
      // delete
      val position = adapter.getPosition(oldUser)
      if (position >= 0)
        AnyBase.runOnUiThread { adapter.remove(oldUser) }
      else
        log.fatal("previous user " + oldUser + " not found")
      remove(context, oldUser)
    case (Some(newUser), None) =>
      // create
      AnyBase.runOnUiThread { adapter.add(newUser) }
      save(context, newUser)
    case (None, None) =>
    // special action, 42
  }
  @Loggable
  def getUserUID(context: Context, user: UserInfo): Option[Int] = synchronized {
    UserInfoExt.get(context, user).flatMap(_.uid)
  }
  @Loggable
  def setUserUID(context: Context, user: UserInfo, newUID: Option[Int]): Unit = synchronized {
    assert(newUID == None || newUID.get >= 0, { "invalid UID " + newUID })
    UserInfoExt.set(context, user, UserInfoExt.get(context, user).
      getOrElse(UserInfoExt.default).copy(uid = newUID))
  }
  @Loggable
  def getUserGID(context: Context, user: UserInfo): Option[Int] = synchronized {
    UserInfoExt.get(context, user).flatMap(_.gid)
  }
  @Loggable
  def setUserGID(context: Context, user: UserInfo, newGID: Option[Int]): Unit = synchronized {
    assert(newGID == None || newGID.get >= 0, { "invalid GID " + newGID })
    UserInfoExt.set(context, user, UserInfoExt.get(context, user).
      getOrElse(UserInfoExt.default).copy(gid = newGID))
  }
  @Loggable
  def isPasswordEnabled(context: Context, user: UserInfo): Boolean = synchronized {
    UserInfoExt.get(context, user).map(_.passwordEnabled).getOrElse(true)
  }
  @Loggable
  def setPasswordEnabled(enabled: Boolean, context: Context, user: UserInfo) = synchronized {
    UserInfoExt.set(context, user, UserInfoExt.get(context, user).getOrElse(UserInfoExt.default).copy(passwordEnabled = enabled))
  }
  @Loggable
  def homeDirectory(context: Context, user: UserInfo): File = try {
    val home = new File(user.home)
    if (user.name != "android" && home.exists)
      home
    else
      SSHDService.getExternalDirectory() getOrElse new File("/")
  } catch {
    case e =>
      SSHDService.getExternalDirectory() getOrElse new File("/")
  }
  @Loggable
  def getDetails(context: Context, user: UserInfo): String = Html.fromHtml(getHtmlDetails(context, user)).toString
  @Loggable(result = false)
  def getHtmlDetails(context: Context, user: UserInfo): String = {
    val ext = UserInfoExt.get(context, user) getOrElse (UserInfoExt.default)
    val publicKeyFileFuture = Futures.future { UserKeys.getPublicKeyFile(context, user) }
    val authorizedKeysFileFuture = Futures.future { UserKeys.getAuthorizedKeysFile(context, user) }
    val dropbearKeyFileFuture = Futures.future { UserKeys.getDropbearKeyFile(context, user) }
    val opensshKeyFileFuture = Futures.future { UserKeys.getOpenSSHKeyFile(context, user) }
    val home = Futures.future { homeDirectory(context, user) }
    val keys = Futures.awaitAll(DTimeout.shortest + 500, publicKeyFileFuture, authorizedKeysFileFuture,
      dropbearKeyFileFuture, opensshKeyFileFuture).asInstanceOf[List[Option[Option[File]]]]
    val publicKey = keys(0).flatMap(file => file)
    val authorizedKeys = keys(1).flatMap(file => file)
    val dropbearKey = keys(2).flatMap(file => file)
    val opensshKey = keys(3).flatMap(file => file)
    val exists = XResource.getString(context, "str_exists").getOrElse("exists")
    val notexists = XResource.getString(context, "str_not_exists").getOrElse("not exists")
    XResource.getString(context, "users_html_details_message").getOrElse(
      """login: <font color='white'>%s</font><br/>""" +
        """enabled: %s<br/>""" +
        """password enabled: %s<br/>""" +
        """home: <font color='white'>%s</font><br/>""" +
        """UID: <font color='white'>%s</font><br/>""" +
        """GID: <font color='white'>%s</font><br/>""" +
        """Public key: <font color='white'>%s</font>%s<br/>""" +
        """Authorized keys: <font color='white'>%s</font>%s<br/>""" +
        """Dropbear private key: <font color='white'>%s</font>%s<br/>""" +
        """OpenSSH private key: <font color='white'>%s</font>%s<br/>""").format(user.name,
        if (user.enabled) "<font color='green'>yes</font>" else "<font color='red'>no</font>",
        if (ext.passwordEnabled) "<font color='green'>yes</font>" else "<font color='red'>no</font>",
        Futures.awaitAll(DTimeout.normal, home).head.asInstanceOf[Option[String]] getOrElse "/",
        ext.uid.getOrElse("default"),
        ext.gid.getOrElse("default"),
        publicKey.map(_.toString).getOrElse(notexists),
        if (publicKey.isEmpty)
          ""
        else if (publicKey.map(_.exists) == Some(true))
          " [<font color='green'>" + exists + "</font>]"
        else
          " [<font color='red'>" + notexists + "</font>]",
        authorizedKeys.map(_.toString).getOrElse(notexists),
        if (authorizedKeys.isEmpty)
          ""
        else if (authorizedKeys.map(_.exists) == Some(true))
          " [<font color='green'>" + exists + "</font>]"
        else
          " [<font color='red'>" + notexists + "</font>]",
        dropbearKey.map(_.toString).getOrElse(notexists),
        if (dropbearKey.isEmpty)
          ""
        else if (dropbearKey.map(_.exists) == Some(true))
          " [<font color='green'>" + exists + "</font>]"
        else " [<font color='red'>" + notexists + "</font>]",
        opensshKey.map(_.toString).getOrElse(notexists),
        if (opensshKey.isEmpty)
          ""
        else if (opensshKey.map(_.exists) == Some(true))
          " [<font color='green'>" + exists + "</font>]"
        else
          " [<font color='red'>" + notexists + "</font>]")
  }
  @Loggable
  def copyDetails(context: Context, user: UserInfo) = try {
    val message = XResource.getString(context, "users_copy_details").
      getOrElse("Copy details about <b>%s</b> to clipboard").format(user.name)
    val content = UserAdapter.getDetails(context, user)
    AnyBase.runOnUiThread {
      try {
        XAPI.clipboardManager(context).setText(content)
        Toast.makeText(context, Html.fromHtml(message), Toast.LENGTH_SHORT).show()
      } catch {
        case e =>
          IAmYell("Unable to copy to clipboard information about \"" + user.name + "\"", e)
      }
    }
  } catch {
    case e =>
      IAmYell("Unable to copy to clipboard details about \"" + user.name + "\"", e)
  }
  @Loggable
  private def checkAndroidUserInfo(in: List[UserInfo]): List[UserInfo] = if (!in.exists(_.name == "android")) {
    log.debug("add default system user \"android\"")
    in :+ UserInfo("android", "123", "variable location", true)
  } else
    in
}
