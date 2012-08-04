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

import scala.collection.mutable.HashMap
import scala.collection.mutable.SynchronizedMap

import org.digimead.digi.ctrl.lib.info.UserInfo
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Common

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.Base64

case class UserInfoExt(passwordEnabled: Boolean, uid: Option[Int], gid: Option[Int]) extends Parcelable {
  def this(in: Parcel) = this(passwordEnabled = (in.readByte == 1),
    uid = in.readInt match {
      case id if id >= 0 => Some(id)
      case _ => None
    },
    gid = in.readInt match {
      case id if id >= 0 => Some(id)
      case _ => None
    })
  def writeToParcel(out: Parcel, flags: Int) {
    if (UserInfoExt.log.isTraceExtraEnabled)
      UserInfoExt.log.trace("writeToParcel SSHDUsers.UserInfo with flags " + flags)
    out.writeByte(if (passwordEnabled) 1 else 0)
    out.writeInt(uid.getOrElse(-1))
    out.writeInt(gid.getOrElse(-1))
  }
  def describeContents() = 0
}

object UserInfoExt extends Logging {
  override val log = Logging.getRichLogger(this)
  val namespace = getClass.getPackage.getName + "@namespace.users.ext"
  final val CREATOR: Parcelable.Creator[UserInfoExt] = new Parcelable.Creator[UserInfoExt]() {
    def createFromParcel(in: Parcel): UserInfoExt = try {
      if (log.isTraceExtraEnabled)
        log.trace("createFromParcel new SSHDUsers.UserInfoExt")
      new UserInfoExt(in)
    } catch {
      case e =>
        log.error(e.getMessage, e)
        null
    }
    def newArray(size: Int): Array[UserInfoExt] = new Array[UserInfoExt](size)
  }
  private val cached = new HashMap[String, UserInfoExt] with SynchronizedMap[String, UserInfoExt]

  def default() = UserInfoExt(true, None, None)
  def get(context: Context, user: UserInfo): Option[UserInfoExt] = cached.get(user.name) orElse {
    val userPref = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    if (!userPref.contains(user.name))
      return None
    val data = userPref.getString(user.name, "") // "" return UserInfoExt(false,Some(0),Some(0))
    try {
      Common.unparcelFromArray[UserInfoExt](Base64.decode(data, Base64.DEFAULT),
        UserInfo.getClass.getClassLoader) match {
          case result @ Some(info) =>
            cached(user.name) = info
            result
          case None =>
            None
        }
    } catch {
      case e =>
        log.warn(e.getMessage, e)
        None
    }
  }
  def set(context: Context, user: UserInfo, ext: UserInfoExt) {
    log.debug("update UserInfoExt for " + user.name + ": " + ext)
    cached(user.name) = ext
    val userPref = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    val editor = userPref.edit
    editor.putString(user.name, Base64.encodeToString(Common.parcelToArray(ext), Base64.DEFAULT))
    editor.commit
  }
}
