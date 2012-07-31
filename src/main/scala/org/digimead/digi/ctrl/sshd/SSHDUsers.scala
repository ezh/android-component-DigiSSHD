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

import java.io.BufferedReader
import java.io.File
import java.io.FileFilter
import java.io.FileWriter
import java.io.FilenameFilter
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import scala.Array.canBuildFrom
import scala.Option.option2Iterable
import scala.actors.Futures
import scala.actors.Futures.future
import scala.collection.JavaConversions._
import scala.ref.WeakReference
import scala.util.Random

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.base.AppControl
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.dialog.FileChooser
import org.digimead.digi.ctrl.lib.dialog.InstallControl
import org.digimead.digi.ctrl.lib.info.UserInfo
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.IAmBusy
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.lib.message.IAmReady
import org.digimead.digi.ctrl.lib.message.IAmWarn
import org.digimead.digi.ctrl.lib.message.IAmYell
import org.digimead.digi.ctrl.lib.message.Origin.anyRefToOrigin
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.util.Passwords
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.service.option.AuthentificationMode

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.ListActivity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.text.ClipboardManager
import android.text.Editable
import android.text.Html
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.util.Base64
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.CheckedTextView
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

object SSHDUsers extends Logging with Passwords {
//  @volatile private var activity: Option[SSHDUsers] = None
  @volatile private var multiUser: Boolean = false
  private val nameMaximumLength = 16


  log.debug("alive")
  object Key {
  }
  /*  object Dialog {
    @Loggable
    def createDialogUserDetails(context: Context, user: UserInfo): AlertDialog = {
      val title = XResource.getString(context, "users_details_title").getOrElse("user \"%s\"").format(user.name)
      val message =
        XResource.getString(context, "users_details_enabled").getOrElse("account: %s").
          format(if (user.enabled)
            XResource.getString(context, "user_enabled").getOrElse("<font color='green'>enabled</font>") + "<br/>"
          else
            XResource.getString(context, "user_disabled").getOrElse("<font color='red'>disabled</font>") + "<br/>") +
          XResource.getString(context, "users_details_uid").getOrElse("UID: %s").
          format((getUserUID(context, user) match {
            case Some(uid) => XResource.getString(context, "user_uid_custom").getOrElse("<font color='yellow'>%s</font>").format(uid) + "<br/>"
            case None => XResource.getString(context, "user_default").getOrElse("default")
          }) + "<br/>") +
          XResource.getString(context, "users_details_gid").getOrElse("GID: %s").
          format((getUserGID(context, user) match {
            case Some(gid) => XResource.getString(context, "user_gid_custom").getOrElse("<font color='yellow'>%s</font>").format(gid) + "<br/>"
            case None => XResource.getString(context, "user_default").getOrElse("default")
          }) + "<br/>") +
          XResource.getString(context, "users_details_password_enabled").getOrElse("password authentication: %s").
          format(if (isPasswordEnabled(context, user))
            XResource.getString(context, "user_enabled").getOrElse("<font color='green'>enabled</font>") + "<br/>"
          else
            XResource.getString(context, "user_disabled").getOrElse("<font color='red'>disabled</font>") + "<br/>") +
          XResource.getString(context, "users_details_authorized_keys").getOrElse("authorized_keys: %s").
          format(if (isPasswordEnabled(context, user))
            XResource.getString(context, "user_enabled").getOrElse("<font color='green'>enabled</font>") + "<br/>"
          else
            XResource.getString(context, "user_disabled").getOrElse("<font color='red'>disabled</font>") + "<br/>") +
          XResource.getString(context, "users_details_message_enabled").getOrElse("public key: %s").
          format(if (isPasswordEnabled(context, user))
            XResource.getString(context, "user_enabled").getOrElse("<font color='green'>enabled</font>") + "<br/>"
          else
            XResource.getString(context, "user_disabled").getOrElse("<font color='red'>disabled</font>") + "<br/>") +
          XResource.getString(context, "users_details_message_enabled").getOrElse("dropbear private key: %s").
          format(if (isPasswordEnabled(context, user))
            XResource.getString(context, "user_enabled").getOrElse("<font color='green'>enabled</font>") + "<br/>"
          else
            XResource.getString(context, "user_disabled").getOrElse("<font color='red'>disabled</font>") + "<br/>") +
          XResource.getString(context, "users_details_message_enabled").getOrElse("openssh private key: %s").
          format(if (isPasswordEnabled(context, user))
            XResource.getString(context, "user_enabled").getOrElse("<font color='green'>enabled</font>") + "<br/>"
          else
            XResource.getString(context, "user_disabled").getOrElse("<font color='red'>disabled</font>") + "<br/>") +
          XResource.getString(context, "users_details_message_enabled").getOrElse("home: %s").
          format(if (isPasswordEnabled(context, user))
            XResource.getString(context, "user_enabled").getOrElse("<font color='green'>enabled</font>") + "<br/>"
          else
            XResource.getString(context, "user_disabled").getOrElse("<font color='red'>disabled</font>") + "<br/>")
      new AlertDialog.Builder(context).
        setTitle(title).
        setMessage(Html.fromHtml(message)).
        setPositiveButton(android.R.string.ok, null).
        setNegativeButton(_root_.android.R.string.cancel, null).
        setIcon(_root_.android.R.drawable.ic_dialog_alert).
        create()
    }
    @Loggable
    def createDialogUserEnable(context: Context, user: UserInfo, callback: (UserInfo) => Any): AlertDialog = {
      val title = XResource.getString(context, "users_enable_title").getOrElse("Enable user \"%s\"").format(user.name)
      val message = XResource.getString(context, "users_enable_message").getOrElse("Do you want to enable \"%s\" account?").format(user.name)
      val notification = XResource.getString(context, "users_enabled_message").getOrElse("enabled user \"%s\"").format(user.name)
      createDialogUserChangeState(context, title, message, notification, true, user, callback)
    }
    @Loggable
    def createDialogUserDisable(context: Context, user: UserInfo, callback: (UserInfo) => Any): AlertDialog = {
      val title = XResource.getString(context, "users_disable_title").getOrElse("Disable user \"%s\"").format(user.name)
      val message = XResource.getString(context, "users_disable_message").getOrElse("Do you want to disable \"%s\" account?").format(user.name)
      val notification = XResource.getString(context, "users_disabled_message").getOrElse("disabled user \"%s\"").format(user.name)
      createDialogUserChangeState(context, title, message, notification, false, user, callback)
    }
    def createDialogGenerateUserKey(context: Context, user: UserInfo): AlertDialog = {
      val defaultLengthIndex = 2
      val keyLength = Array[CharSequence]("4096 bits (only RSA)", "2048 bits (only RSA)", "1024 bits (RSA and DSA)")
      val title = XResource.getString(context, "user_generate_key_title").getOrElse("Generate key for \"%s\"").format("android")
      new AlertDialog.Builder(context).
        setTitle(title).
        setSingleChoiceItems(keyLength, defaultLengthIndex, new DialogInterface.OnClickListener() {
          def onClick(dialog: DialogInterface, item: Int) =
            dialog.asInstanceOf[AlertDialog].getButton(DialogInterface.BUTTON_NEUTRAL).
              setEnabled(item == defaultLengthIndex)
        }).
        setNeutralButton("DSA", new DialogInterface.OnClickListener() {
          def onClick(dialog: DialogInterface, whichButton: Int) = {
            val lv = dialog.asInstanceOf[AlertDialog].getListView
            // leave UI thread
            Futures.future { Key.generateDSAKey(lv.getContext, user) }
          }
        }).
        setPositiveButton("RSA", new DialogInterface.OnClickListener() {
          def onClick(dialog: DialogInterface, whichButton: Int) {
            val lv = dialog.asInstanceOf[AlertDialog].getListView
            val length = lv.getCheckedItemPosition match {
              case 0 => 4096
              case 1 => 2048
              case 2 => 1024
            }
            // leave UI thread
            Futures.future { Key.generateRSAKey(lv.getContext, user, length) }
          }
        }).
        setNegativeButton(_root_.android.R.string.cancel, null).
        setIcon(_root_.android.R.drawable.ic_dialog_alert).
        create()
    }
    @Loggable
    private def createDialogUserChangeState(context: Context, title: String, message: String, notification: String, newState: Boolean, user: UserInfo, callback: (UserInfo) => Any): AlertDialog =
      new AlertDialog.Builder(context).
        setTitle(title).
        setMessage(message).
        setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
          def onClick(dialog: DialogInterface, whichButton: Int) = {
            IAmWarn(notification)
            Toast.makeText(context, notification, Toast.LENGTH_SHORT).show()
            val newUser = user.copy(enabled = newState)
            save(context, newUser)
            adapter.foreach {
              adapter =>
                val position = adapter.getPosition(user)
                if (position >= 0) {
                  adapter.remove(user)
                  adapter.insert(newUser, position)
                }
            }
/*            activity.foreach {
              activity =>
                activity.updateFieldsState()
                if (activity.lastActiveUserInfo.get.exists(_ == user))
                  activity.lastActiveUserInfo.set(Some(newUser))
            }*/
            callback(newUser)
          }
        }).
        setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
          def onClick(dialog: DialogInterface, whichButton: Int) =
            callback(user)
        }).
        setIcon(android.R.drawable.ic_dialog_alert).
        create()
  }*/
}
