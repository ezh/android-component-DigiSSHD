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

package org.digimead.digi.ctrl.sshd

import scala.ref.WeakReference

import org.digimead.digi.ctrl.sshd.user.UserDialog
import org.digimead.digi.ctrl.sshd.user.UserDialog.ChangePassword
import org.digimead.digi.ctrl.sshd.user.UserDialog.Delete
import org.digimead.digi.ctrl.sshd.user.UserDialog.Disable
import org.digimead.digi.ctrl.sshd.user.UserDialog.Enable
import org.digimead.digi.ctrl.sshd.user.UserDialog.KeyReplace
import org.digimead.digi.ctrl.sshd.user.UserDialog.KeyType
import org.digimead.digi.ctrl.sshd.user.UserDialog.SetGUID
import org.digimead.digi.ctrl.sshd.user.UserDialog.SetHome
import org.digimead.digi.ctrl.sshd.user.UserDialog.ShowDetails
import org.digimead.digi.ctrl.sshd.user.UserFragment
import org.digimead.digi.ctrl.sshd.user.UserFragment.Dialog.CreateUser
import org.digimead.digi.ctrl.sshd.user.UserFragment.Dialog.DeleteAllUsers
import org.digimead.digi.ctrl.sshd.user.UserFragment.Dialog.DisableAllUsers
import org.digimead.digi.ctrl.sshd.user.UserFragment.Dialog.UpdateUser

import android.app.Activity
import android.support.v4.app.Fragment

class SSHDResource(activity: WeakReference[Activity]) {
  // UserDialog
  lazy val userChangePassword = activity.get.map(a => Fragment.instantiate(a, classOf[UserDialog.ChangePassword].getName, null).asInstanceOf[UserDialog.ChangePassword])
  lazy val userEnable = activity.get.map(a => Fragment.instantiate(a, classOf[UserDialog.Enable].getName, null).asInstanceOf[UserDialog.Enable])
  lazy val userDisable = activity.get.map(a => Fragment.instantiate(a, classOf[UserDialog.Disable].getName, null).asInstanceOf[UserDialog.Disable])
  lazy val userDelete = activity.get.map(a => Fragment.instantiate(a, classOf[UserDialog.Delete].getName, null).asInstanceOf[UserDialog.Delete])
  lazy val userSetGUID = activity.get.map(a => Fragment.instantiate(a, classOf[UserDialog.SetGUID].getName, null).asInstanceOf[UserDialog.SetGUID])
  lazy val userShowDetails = activity.get.map(a => Fragment.instantiate(a, classOf[UserDialog.ShowDetails].getName, null).asInstanceOf[UserDialog.ShowDetails])
  lazy val userSetHome = activity.get.map(a => Fragment.instantiate(a, classOf[UserDialog.SetHome].getName, null).asInstanceOf[UserDialog.SetHome])
  lazy val userKeyReplace = activity.get.map(a => Fragment.instantiate(a, classOf[UserDialog.KeyReplace].getName, null).asInstanceOf[UserDialog.KeyReplace])
  lazy val userKeyType = activity.get.map(a => Fragment.instantiate(a, classOf[UserDialog.KeyType].getName, null).asInstanceOf[UserDialog.KeyType])
  // UserFragment
  lazy val userUpdate = activity.get.map(a => Fragment.instantiate(a, classOf[UserFragment.Dialog.UpdateUser].getName, null).asInstanceOf[UserFragment.Dialog.UpdateUser])
  lazy val userCreate = activity.get.map(a => Fragment.instantiate(a, classOf[UserFragment.Dialog.CreateUser].getName, null).asInstanceOf[UserFragment.Dialog.CreateUser])
  lazy val userDisableAll = activity.get.map(a => Fragment.instantiate(a, classOf[UserFragment.Dialog.DisableAllUsers].getName, null).asInstanceOf[UserFragment.Dialog.DisableAllUsers])
  lazy val userDeleteAll = activity.get.map(a => Fragment.instantiate(a, classOf[UserFragment.Dialog.DeleteAllUsers].getName, null).asInstanceOf[UserFragment.Dialog.DeleteAllUsers])
}

object SSHDResource {
  // implicit conversion broken in 2.8 :-/
  // UserDialog
  def userChangePassword = SSHDActivity.activity.flatMap(_.resources.userChangePassword)
  def userEnable = SSHDActivity.activity.flatMap(_.resources.userEnable)
  def userDisable = SSHDActivity.activity.flatMap(_.resources.userDisable)
  def userDelete = SSHDActivity.activity.flatMap(_.resources.userDelete)
  def userSetGUID = SSHDActivity.activity.flatMap(_.resources.userSetGUID)
  def userSetHome = SSHDActivity.activity.flatMap(_.resources.userSetHome)
  def userShowDetails = SSHDActivity.activity.flatMap(_.resources.userShowDetails)
  def userKeyReplace = SSHDActivity.activity.flatMap(_.resources.userKeyReplace)
  def userKeyType = SSHDActivity.activity.flatMap(_.resources.userKeyType)
  // UserFragment
  def userUpdate = SSHDActivity.activity.flatMap(_.resources.userUpdate)
  def userCreate = SSHDActivity.activity.flatMap(_.resources.userCreate)
  def userDisableAll = SSHDActivity.activity.flatMap(_.resources.userDisableAll)
  def userDeleteAll = SSHDActivity.activity.flatMap(_.resources.userDeleteAll)
}
