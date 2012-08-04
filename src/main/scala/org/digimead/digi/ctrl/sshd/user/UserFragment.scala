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
import java.util.concurrent.atomic.AtomicReference

import scala.Option.option2Iterable
import scala.actors.Futures
import scala.ref.WeakReference
import scala.util.Random

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.androidext.SafeDialog
import org.digimead.digi.ctrl.lib.androidext.XAPI
import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.base.AppControl
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.info.UserInfo
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.lib.message.IAmWarn
import org.digimead.digi.ctrl.lib.message.IAmYell
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDActivity
import org.digimead.digi.ctrl.sshd.SSHDPreferences
import org.digimead.digi.ctrl.sshd.SSHDTabAdapter
import org.digimead.digi.ctrl.sshd.ext.SSHDDialog
import org.digimead.digi.ctrl.sshd.ext.SSHDDialog.dialog2string
import org.digimead.digi.ctrl.sshd.ext.SherlockDynamicFragment
import org.digimead.digi.ctrl.sshd.service.option.AuthentificationMode

import com.actionbarsherlock.app.SherlockListFragment

import android.app.Activity
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
import android.text.Editable
import android.text.Html
import android.text.InputType
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class UserFragment extends SherlockListFragment with SherlockDynamicFragment with Logging {
  UserFragment.fragment = Some(this)
  private lazy val dynamicHeader = new WeakReference({
    getSherlockActivity.findViewById(R.id.element_users_header).asInstanceOf[LinearLayout]
  })
  private lazy val dynamicFooter = new WeakReference({
    getSherlockActivity.findViewById(R.id.element_users_footer).asInstanceOf[LinearLayout].
      findViewById(R.id.users_footer_dynamic).asInstanceOf[LinearLayout]
  })
  private lazy val apply = new WeakReference(getSherlockActivity.findViewById(R.id.element_users_footer).
    findViewById(R.id.users_apply).asInstanceOf[TextView])
  private lazy val blockAll = new WeakReference(getSherlockActivity.findViewById(R.id.element_users_footer).
    findViewById(R.id.users_footer_toggle_all).asInstanceOf[TextView])
  private lazy val deleteAll = new WeakReference(getSherlockActivity.findViewById(R.id.element_users_footer).
    findViewById(R.id.users_footer_delete_all).asInstanceOf[TextView])
  private lazy val userName = new WeakReference(dynamicFooter.get.map(_.findViewById(R.id.users_name).
    asInstanceOf[TextView]).getOrElse(null))
  private lazy val userGenerateButton = new WeakReference(dynamicFooter.get.map(_.findViewById(R.id.users_add).
    asInstanceOf[ImageButton]).getOrElse(null))
  private lazy val userPassword = new WeakReference(dynamicFooter.get.map(_.findViewById(R.id.users_password).
    asInstanceOf[TextView]).getOrElse(null))
  private lazy val userPasswordShowButton = new WeakReference(dynamicFooter.get.map(_.findViewById(R.id.users_show_password).
    asInstanceOf[ImageButton]).getOrElse(null))
  private lazy val userPasswordEnabledCheckbox = new WeakReference(dynamicFooter.get.map(_.findViewById(android.R.id.checkbox).
    asInstanceOf[CheckBox]).getOrElse(null))
  private[user] val lastActiveUserInfo = new AtomicReference[Option[UserInfo]](None)
  private var savedTitle: CharSequence = ""
  log.debug("alive")

  override def toString = "fragment_users"
  @Loggable
  override def onAttach(activity: Activity) {
    super.onAttach(activity)
    UserFragment.fragment = Some(this)
  }
  @Loggable
  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    SSHDActivity.ppGroup("UserFragment.onCreateView") {
      inflater.inflate(R.layout.fragment_users, container, false)
    }
  @Loggable
  override def onActivityCreated(savedInstanceState: Bundle) = SSHDActivity.ppGroup("info.TabContent.onActivityCreated") {
    super.onActivityCreated(savedInstanceState)
    UserAdapter.adapter.foreach(setListAdapter)
    setHasOptionsMenu(false)
    registerForContextMenu(getListView)
    for {
      userName <- userName.get
      userPassword <- userPassword.get
      userPasswordEnabledCheckbox <- userPasswordEnabledCheckbox.get
    } {
      userName.addTextChangedListener(new TextWatcher() {
        def afterTextChanged(s: Editable) { updateFieldsState }
        def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
      })
      userName.setFilters(Array(UserDialog.userNameFilter))
      userPassword.addTextChangedListener(new TextWatcher() {
        def afterTextChanged(s: Editable) { updateFieldsState }
        def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
      })
      userPassword.setFilters(Array(UserDialog.userPasswordFilter))
      userPasswordEnabledCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) = updateFieldsState
      })
    }
  }
  @Loggable
  override def onResume() = {
    super.onResume
    val activity = getSherlockActivity
    savedTitle = activity.getTitle
    for { userGenerateButton <- userGenerateButton.get } {
      AuthentificationMode.getStateExt(activity) match {
        case SSHDPreferences.AuthentificationType.SingleUser =>
          activity.setTitle(XResource.getString(activity, "app_name_singleuser").getOrElse("DigiSSHD: Single User Mode"))
          userGenerateButton.setEnabled(false)
        case SSHDPreferences.AuthentificationType.MultiUser =>
          activity.setTitle(XResource.getString(activity, "app_name_multiuser").getOrElse("DigiSSHD: Multi User Mode"))
          userGenerateButton.setEnabled(true)
        case invalid =>
          log.fatal("invalid authenticatin type \"" + invalid + "\"")
          None
      }
    }
    for {
      dynamicHeader <- dynamicHeader.get
      dynamicFooter <- dynamicFooter.get
    } {
      /*      if (SSHDActivity.collapsed.get) {
        dynamicHeader.setVisibility(View.GONE)
        dynamicFooter.setVisibility(View.GONE)
      } else {
        dynamicHeader.setVisibility(View.VISIBLE)
        dynamicFooter.setVisibility(View.VISIBLE)
      }*/
    }
    if (lastActiveUserInfo.get.isEmpty)
      onListItemClick(getListView, getListView, 0, 0)
    updateFieldsState
    showDynamicFragment
  }
  @Loggable
  override def onPause() = {
    hideDynamicFragment
    getSherlockActivity.setTitle(savedTitle)
    super.onPause
  }
  @Loggable
  override def onDetach() {
    UserFragment.fragment = None
    super.onDetach()
  }
  @Loggable
  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) = for {
    adapter <- UserAdapter.adapter
    userName <- userName.get
    userPassword <- userPassword.get
    userPasswordEnabledCheckbox <- userPasswordEnabledCheckbox.get
    context <- Option(getSherlockActivity)
  } adapter.getItem(position) match {
    case user: UserInfo =>
      if (UserAdapter.isMultiUser(context) || user.name == "android") {
        val isPasswordEnabled = Futures.future { UserAdapter.isPasswordEnabled(context, user) }
        lastActiveUserInfo.set(Some(user))
        userName.setText(user.name)
        userPassword.setText(user.password)
        userPasswordEnabledCheckbox.setChecked(isPasswordEnabled())
        updateFieldsState()
      } else
        Toast.makeText(context, XResource.getString(context, "users_in_single_user_mode").getOrElse("only android user available in single user mode"), Toast.LENGTH_SHORT).show()
    case item =>
      log.fatal("unknown item " + item)
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) = UserAdapter.adapter.foreach {
    adapter =>
      super.onCreateContextMenu(menu, v, menuInfo)
      menuInfo match {
        case info: AdapterContextMenuInfo =>
          adapter.getItem(info.position) match {
            case item: UserInfo =>
              menu.setHeaderTitle(Html.fromHtml(XResource.getString(v.getContext, "users_user").
                getOrElse("user <b>%s</b>").format(item.name)))
              menu.setHeaderIcon(XResource.getId(v.getContext, "ic_users", "drawable"))
              if (item.enabled)
                menu.add(Menu.NONE, XResource.getId(v.getContext, "users_disable"), 1,
                  XResource.getString(v.getContext, "users_disable").getOrElse("Disable"))
              else
                menu.add(Menu.NONE, XResource.getId(v.getContext, "users_enable"), 1,
                  XResource.getString(v.getContext, "users_enable").getOrElse("Enable"))
              if (item.name != "android") {
                menu.add(Menu.NONE, XResource.getId(v.getContext, "users_set_gid_uid"), 2,
                  XResource.getString(v.getContext, "users_set_gid_uid").getOrElse("Set GID and UID"))
                menu.add(Menu.NONE, XResource.getId(v.getContext, "users_set_home"), 3,
                  XResource.getString(v.getContext, "users_set_home").getOrElse("Set home directory"))
              }
              menu.add(Menu.NONE, XResource.getId(v.getContext, "users_copy_details"), 4,
                XResource.getString(v.getContext, "users_copy_details").getOrElse("Copy details"))
              menu.add(Menu.NONE, XResource.getId(v.getContext, "users_show_details"), 5,
                XResource.getString(v.getContext, "users_show_details").getOrElse("Details"))
              if (item.name != "android") {
                menu.add(Menu.NONE, XResource.getId(v.getContext, "users_delete"), 6,
                  XResource.getString(v.getContext, "users_delete").getOrElse("Delete"))
              }
            case item =>
              log.fatal("unknown item " + item)
          }
        case info =>
          log.fatal("unsupported menu info " + info)
      }
  }
  @Loggable
  override def onContextItemSelected(menuItem: MenuItem): Boolean = {
    UserAdapter.adapter.map {
      adapter =>
        menuItem.getMenuInfo match {
          case info: AdapterContextMenuInfo =>
            if (getListView.getPositionForView(info.targetView) == -1)
              return false
            val context = getSherlockActivity
            adapter.getItem(info.position) match {
              case item: UserInfo =>
                menuItem.getItemId match {
                  case id if id == XResource.getId(context, "users_disable") =>
                    UserDialog.disable.foreach(dialog =>
                      SafeDialog(context, dialog, () => dialog).transaction((ft, fragment, target) => {
                        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                        ft.addToBackStack(dialog)
                      }).before(dialog => {
                        dialog.user = Some(item)
                        dialog.onOkCallback = Some((user) => onDialogChangeState(false, user))
                      }).show())
                    true
                  case id if id == XResource.getId(context, "users_enable") =>
                    UserDialog.enable.foreach(dialog =>
                      SafeDialog(context, dialog, () => dialog).transaction((ft, fragment, target) => {
                        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                        ft.addToBackStack(dialog)
                      }).before(dialog => {
                        dialog.user = Some(item)
                        dialog.onOkCallback = Some((user) => onDialogChangeState(true, user))
                      }).show())
                    true
                  case id if id == XResource.getId(context, "users_set_gid_uid") =>
                    UserDialog.setGUID.foreach(dialog =>
                      SafeDialog(context, dialog, () => dialog).transaction((ft, fragment, target) => {
                        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                        ft.addToBackStack(dialog)
                      }).before(dialog => {
                        dialog.user = Some(item)
                        dialog.userExt = UserInfoExt.get(dialog.getSherlockActivity, item)
                        dialog.onOkCallback = Some((user, uid, gid) => onDialogSetGUID(user, uid, gid))
                      }).show())
                    true
                  case id if id == XResource.getId(context, "users_set_home") =>
                    true
                  case id if id == XResource.getId(context, "users_delete") =>
                    UserDialog.delete.foreach(dialog =>
                      SafeDialog(context, dialog, () => dialog).transaction((ft, fragment, target) => {
                        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                        ft.addToBackStack(dialog)
                      }).before(dialog => {
                        dialog.user = Some(item)
                        dialog.onOkCallback = Some((user) => onDialogDelete(user))
                      }).show())
                    true
                  case id if id == XResource.getId(context, "users_copy_details") =>
                    Futures.future {
                      try {
                        val message = XResource.getString(context, "users_copy_details").
                          getOrElse("Copy details about <b>%s</b> to clipboard").format(item.name)
                        val content = UserAdapter.getDetails(context, item)
                        AnyBase.runOnUiThread {
                          try {
                            XAPI.clipboardManager(context).setText(content)
                            Toast.makeText(context, Html.fromHtml(message), Toast.LENGTH_SHORT).show()
                          } catch {
                            case e =>
                              IAmYell("Unable to copy to clipboard information about \"" + item.name + "\"", e)
                          }
                        }
                      } catch {
                        case e =>
                          IAmYell("Unable to copy to clipboard details about \"" + item.name + "\"", e)
                      }
                    }
                    true
                  case id if id == XResource.getId(getSherlockActivity, "users_show_details") =>
                    UserDialog.showDetails.foreach(dialog =>
                      SafeDialog(context, dialog, () => dialog).transaction((ft, fragment, target) => {
                        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                        ft.addToBackStack(dialog)
                      }).before(dialog => dialog.user = Some(item)).show())
                    true
                  case id =>
                    log.fatal("unknown action " + id)
                    false
                }
              case item =>
                log.fatal("unknown item " + item)
                false
            }
          case info =>
            log.fatal("unsupported menu info " + info)
            false
        }
    }
  } getOrElse false
  @Loggable
  def onClickApply(v: View) = synchronized {
    for {
      userName <- userName.get
      userPassword <- userPassword.get
    } {
      val context = getSherlockActivity
      val name = userName.getText.toString.trim
      val password = userPassword.getText.toString.trim
      assert(name.nonEmpty && password.nonEmpty, "one of user fields is empty")
      lastActiveUserInfo.get match {
        case Some(user) if UserAdapter.list.exists(_.name == name) =>
          UserFragment.Dialog.updateUser.foreach(dialog =>
            SafeDialog(context, dialog, () => dialog).transaction((ft, fragment, target) => {
              ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
              ft.addToBackStack(dialog)
            }).before(dialog => dialog.user = Some(user)).show())
        case _ =>
          UserFragment.Dialog.createUser.foreach(dialog =>
            SafeDialog(context, dialog, () => dialog).transaction((ft, fragment, target) => {
              ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
              ft.addToBackStack(dialog)
            }).before(dialog => dialog.userName = Some(name)).show())
      }
    }
  }
  @Loggable
  def onClickToggleBlockAll(v: View) =
    UserFragment.Dialog.disableAllUsers.foreach(dialog =>
      SafeDialog(getSherlockActivity, dialog, () => dialog).transaction((ft, fragment, target) => {
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        ft.addToBackStack(dialog)
      }).show())
  @Loggable
  def onClickDeleteAll(v: View) = {
    /*    new AlertDialog.Builder(this).
      setTitle(XResource.getString(v.getContext, "users_delete_all_title").getOrElse("Delete all users")).
      setMessage(XResource.getString(v.getContext, "users_delete_all_message").getOrElse("Are you sure you want to delete all users except \"android\"?")).
      setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, whichButton: Int) = SSHDUsers.this.synchronized {
          for {
            adapter <- SSHDUsers.adapter
          } {
            adapter.setNotifyOnChange(false)
            SSHDUsers.list.foreach(user => if (user.name != "android") {
              IAmMumble("remove \"%s\" user account".format(user.name))
              SSHDUsers.remove(v.getContext, user)
              adapter.remove(user)
              if (lastActiveUserInfo.get.exists(_ == user))
                lastActiveUserInfo.set(None)
            })
            adapter.setNotifyOnChange(true)
            adapter.notifyDataSetChanged
          }
          val message = XResource.getString(v.getContext, "users_all_deleted").getOrElse("all users except \"android\" are deleted")
          IAmWarn(message)
          Toast.makeText(v.getContext, message, Toast.LENGTH_SHORT).show()
          updateFieldsState
        }
      }).
      setNegativeButton(android.R.string.cancel, null).
      setIcon(android.R.drawable.ic_dialog_alert).
      create().show()*/
  }
  @Loggable
  def onClickGenerateNewUser(v: View): Unit = Futures.future {
    try {
      lastActiveUserInfo.set(None)
      // name
      val names = getResources.getStringArray(R.array.names)
      val rand = new Random(System.currentTimeMillis())
      val random_index = rand.nextInt(names.length)
      val name = {
        var name = ""
        while (name.isEmpty || UserAdapter.list.exists(_.name == name)) {
          val rawName = names(random_index)
          name = (UserAdapter.nameMaximumLength - rawName.length) match {
            case len if len >= 4 =>
              rawName + UserAdapter.randomInt(0, 9999)
            case len if len > 3 =>
              rawName + UserAdapter.randomInt(0, 999)
            case len if len > 2 =>
              rawName + UserAdapter.randomInt(0, 99)
            case len if len > 1 =>
              rawName + UserAdapter.randomInt(0, 9)
            case _ =>
              rawName
          }
        }
        name
      }
      // password
      val password = UserAdapter.generate()
      for {
        userName <- userName.get
        userPasswrod <- userPassword.get
      } AnyBase.runOnUiThread {
        userName.setText(name)
        userPasswrod.setText(password)
        updateFieldsState()
      }
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  /*@Loggable
  def onClickChangeHomeDirectory(v: View): Unit = userHome.get.foreach {
    userHome =>
      if (lastActiveUserInfo.get.exists(_.name == "android")) {
        Toast.makeText(v.getContext, XResource.getString(v.getContext, "users_home_android_warning").
          getOrElse("unable to change home directory of system user"), Toast.LENGTH_SHORT).show()
        return
      }
      val filter = new FileFilter { override def accept(file: File) = file.isDirectory }
      val userHomeString = userHome.getText.toString.trim
      if (userHomeString.isEmpty) {
        Toast.makeText(v.getContext, XResource.getString(v.getContext, "users_home_directory_empty").
          getOrElse("home directory is empty"), Toast.LENGTH_SHORT).show()
        return
      }
      val userHomeFile = new File(userHomeString)
      if (!userHomeFile.exists) {
        new AlertDialog.Builder(this).
          setTitle(R.string.users_home_directory_not_exists_title).
          setMessage(R.string.users_home_directory_not_exists_message).
          setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            def onClick(dialog: DialogInterface, whichButton: Int) =
              if (userHomeFile.mkdirs) {
                dialog.dismiss()
                FileChooser.createDialog(SSHDUsers.this, XResource.getString(SSHDUsers.this, "dialog_select_folder").
                  getOrElse("Select Folder"), userHomeFile, onResultChangeHomeDirectory, filter).show()
              } else {
                Toast.makeText(v.getContext, XResource.getString(v.getContext, "filechooser_create_directory_failed").
                  getOrElse("unable to create directory %s").format(userHomeFile), Toast.LENGTH_SHORT).show()
              }
          }).
          setNegativeButton(android.R.string.cancel, null).
          setIcon(android.R.drawable.ic_dialog_alert).
          create().show()
      } else {
        FileChooser.createDialog(this, XResource.getString(this, "dialog_select_folder").getOrElse("Select Folder"),
          userHomeFile, onResultChangeHomeDirectory, filter).show()
      }
  }*/
  /*  @Loggable
  def onResultChangeHomeDirectory(context: Context, path: File, files: Seq[File], stash: AnyRef) = userHome.get.foreach {
    userHome =>
      userHome.setText(path.toString)
  }*/
  @Loggable
  def onClickShowPassword(v: View): Unit = for {
    userPasswordShowButton <- userPasswordShowButton.get
    userPassword <- userPassword.get
  } if (UserFragment.showPassword) {
    UserFragment.showPassword = false
    userPasswordShowButton.setSelected(false)
    userPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
    userPassword.setTransformationMethod(PasswordTransformationMethod.getInstance())
  } else {
    UserFragment.showPassword = true
    userPasswordShowButton.setSelected(true)
    userPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
  }
  @Loggable
  def onDialogChangeState(state: Boolean, user: UserInfo) = {
    val context = getSherlockActivity
    val notification = if (state)
      XResource.getString(getSherlockActivity, "users_enabled_message").getOrElse("enabled user \"%s\"").format(user.name)
    else
      XResource.getString(getSherlockActivity, "users_disabled_message").getOrElse("disabled user \"%s\"").format(user.name)
    IAmWarn(notification)
    Toast.makeText(context, notification, Toast.LENGTH_SHORT).show()
    val newUser = user.copy(enabled = state)
    Futures.future { UserAdapter.save(context, newUser) }
    UserAdapter.adapter.foreach {
      adapter =>
        val position = adapter.getPosition(user)
        if (position >= 0) {
          adapter.remove(user)
          adapter.insert(newUser, position)
        }
    }
    UserFragment.fragment.foreach {
      fragment =>
        fragment.updateFieldsState()
        if (fragment.lastActiveUserInfo.get.exists(_ == user))
          fragment.lastActiveUserInfo.set(Some(newUser))
    }
  }
  @Loggable
  def onDialogDelete(user: UserInfo) = Futures.future {
    this.synchronized {
      try {
        val context = getSherlockActivity
        val message = XResource.getString(context, "users_deleted_message").
          getOrElse("deleted user <b>%s</b>").format(user.name)
        IAmWarn(message)
        UserAdapter.remove(context, user)
        AnyBase.runOnUiThread {
          Toast.makeText(context, Html.fromHtml(message), Toast.LENGTH_SHORT).show()
          UserAdapter.adapter.foreach(_.remove(user))
        }
        updateFieldsState()
        if (lastActiveUserInfo.get.exists(_ == user))
          lastActiveUserInfo.set(UserAdapter.find(context, "android"))
      } catch {
        case e =>
          log.error(e.getMessage, e)
      }
    }
  }
  @Loggable
  def onDialogSetGUID(user: UserInfo, uid: Option[Int], gid: Option[Int]) = Futures.future {
    this.synchronized {
      try {
        val context = getSherlockActivity
        UserAdapter.setUserUID(context, user, uid match {
          case r @ Some(uid) if uid >= 0 => r
          case _ => None
        })
        UserAdapter.setUserGID(context, user, gid match {
          case r @ Some(gid) if gid >= 0 => r
          case _ => None
        })
      } catch {
        case e =>
          log.error(e.getMessage, e)
      }
    }
  }
  def onDialogUserUpdate() = Futures.future {
    this.synchronized {
      for {
        adapter <- UserAdapter.adapter
        userName <- userName.get
        userPassword <- userPassword.get
        userPasswordEnabledCheckbox <- userPasswordEnabledCheckbox.get
        user <- lastActiveUserInfo.get
      } {
        val context = getSherlockActivity
        val name = userName.getText.toString.trim
        val password = userPassword.getText.toString.trim
        (if (name == "android")
          Some(user.copy(password = user.password))
        else if (name != "android")
          Some(user.copy(name = name, password = password))
        else {
          None
        }) match {
          case Some(newUser) =>
            val message = XResource.getString(context, "users_update_message").
              getOrElse("update user \"%s\"").format(user.name)
            IAmWarn(message)
            UserAdapter.save(context, newUser)
            UserAdapter.setPasswordEnabled(userPasswordEnabledCheckbox.isChecked, context, newUser)
            lastActiveUserInfo.set(Some(newUser))
            AnyBase.runOnUiThread {
              Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
              val position = adapter.getPosition(user)
              adapter.remove(user)
              adapter.insert(newUser, position)
              UserFragment.this.getListView.setSelectionFromTop(position, 5)
            }
            updateFieldsState()
          case None =>
            val message = XResource.getString(context, "users_update_fail_message").
              getOrElse("update user \"%s\" failed").format(user.name)
            IAmYell(message)
            AnyBase.runOnUiThread { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
        }
      }
    }
  }
  @Loggable
  def onDialogUserCreate() = Futures.future {
    this.synchronized {
      for {
        adapter <- UserAdapter.adapter
        userName <- userName.get
        userPassword <- userPassword.get
        userPasswordEnabledCheckbox <- userPasswordEnabledCheckbox.get
      } {
        val context = getSherlockActivity
        val name = userName.getText.toString.trim
        val password = userPassword.getText.toString.trim
        val passwordEnabled = userPasswordEnabledCheckbox.isChecked
        val message = XResource.getString(context, "users_create_message").
          getOrElse("created user \"%s\"").format(name)
        IAmWarn(message)
        // home
        val home = AppControl.Inner.getExternalDirectory(DTimeout.normal).flatMap(d => Option(d)).getOrElse({
          val sdcard = new File("/sdcard")
          if (!sdcard.exists)
            new File("/")
          else
            sdcard
        }).getAbsolutePath
        val newUser = UserInfo(name, password, home, true)
        UserAdapter.save(context, newUser)
        UserAdapter.setPasswordEnabled(passwordEnabled, context, newUser)
        lastActiveUserInfo.set(Some(newUser))
        AnyBase.runOnUiThread {
          try {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            val position = (UserAdapter.list :+ newUser).sortBy(_.name).indexOf(newUser)
            adapter.insert(newUser, position)
            UserFragment.this.getListView.setSelectionFromTop(position, 5)
          } catch {
            case e =>
              log.error(e.getMessage, e)
          }
        }
        updateFieldsState()
      }
    }
  }
  @Loggable
  def onDialogDisableAllUsers(): Unit = Futures.future {
    this.synchronized {
      try {
        val context = getSherlockActivity
        val updateSeq = UserAdapter.list.map(user => {
          IAmMumble("disable user \"%s\"".format(user.name))
          val newUser = user.copy(enabled = false)
          UserAdapter.save(context, newUser)
          (adapter: ArrayAdapter[UserInfo]) => {
            val position = adapter.getPosition(user)
            adapter.remove(user)
            adapter.insert(newUser, position)
            if (lastActiveUserInfo.get.exists(_ == user))
              lastActiveUserInfo.set(Some(newUser))
          }
        })
        AnyBase.runOnUiThread {
          UserAdapter.adapter.foreach(adapter => {
            adapter.setNotifyOnChange(false)
            updateSeq.foreach(_(adapter))
            adapter.setNotifyOnChange(true)
            adapter.notifyDataSetChanged
          })
          val message = XResource.getString(context, "users_all_disabled").getOrElse("all users are disabled")
          IAmWarn(message)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        updateFieldsState
      } catch {
        case e =>
          log.error(e.getMessage, e)
      }
    }
  }
  @Loggable
  private[user] def updateFieldsState(): Unit = for {
    userName <- userName.get
    userPassword <- userPassword.get
    apply <- apply.get
    blockAll <- blockAll.get
    deleteAll <- deleteAll.get
    userPasswordEnabledCheckbox <- userPasswordEnabledCheckbox.get
  } Futures.future {
    var applyState = if (lastActiveUserInfo.get.nonEmpty) {
      if (lastActiveUserInfo.get.exists(u => u.name == userName.getText.toString.trim &&
        u.password == userPassword.getText.toString.trim && userPasswordEnabledCheckbox.isChecked ==
        UserAdapter.isPasswordEnabled(userPasswordEnabledCheckbox.getContext, u)))
        false
      else
        userName.getText.toString.trim.nonEmpty && userPassword.getText.toString.trim.nonEmpty
    } else {
      userName.getText.toString.trim.nonEmpty && userPassword.getText.toString.trim.nonEmpty
    }
    var userNameState = !lastActiveUserInfo.get.exists(_.name == "android")
    var userPasswordState = userPasswordEnabledCheckbox.isChecked
    if (!UserAdapter.isMultiUser(getSherlockActivity)) {
      AnyBase.runOnUiThread {
        apply.setEnabled(applyState)
        userName.setEnabled(false)
        userPassword.setEnabled(userPasswordState)
        blockAll.setEnabled(false)
        deleteAll.setEnabled(false)
      }
      return
    }
    var blockAllState = UserAdapter.list.exists(_.enabled)
    var deleteAllState = UserAdapter.list.exists(_.name != "android")
    AnyBase.runOnUiThread {
      apply.setEnabled(applyState)
      userName.setEnabled(userNameState)
      userPassword.setEnabled(userPasswordState)
      blockAll.setEnabled(blockAllState)
      deleteAll.setEnabled(deleteAllState)
    }
  }
}

object UserFragment extends Logging {
  /** profiling support */
  private val ppLoading = SSHDActivity.ppGroup.start("UserFragment$")
  /** TabContent fragment instance */
  @volatile private[user] var fragment: Option[UserFragment] = None
  @volatile private var showPassword = false
  log.debug("alive")
  ppLoading.stop

  @Loggable
  def show() = SSHDTabAdapter.getSelectedFragment match {
    case Some(currentTabFragment) =>
      SherlockDynamicFragment.show(classOf[UserFragment], currentTabFragment)
    case None =>
      log.fatal("current tab fragment not found")
  }
  @Loggable
  def onClickGenerateNewUser(v: View) = fragment.foreach(_.onClickGenerateNewUser(v))
  @Loggable
  def onClickUsersShowPassword(v: View) = fragment.foreach(_.onClickShowPassword(v))
  @Loggable
  def onClickApply(v: View) = fragment.foreach(_.onClickApply(v))
  @Loggable
  def onClickToggleBlockAll(v: View) = fragment.foreach(_.onClickToggleBlockAll(v))
  @Loggable
  def onClickDeleteAll(v: View) = fragment.foreach(_.onClickDeleteAll(v))
  object Dialog {
    lazy val updateUser = AppComponent.Context.map(context =>
      Fragment.instantiate(context.getApplicationContext, classOf[UpdateUser].getName, null).asInstanceOf[UpdateUser])
    lazy val createUser = AppComponent.Context.map(context =>
      Fragment.instantiate(context.getApplicationContext, classOf[CreateUser].getName, null).asInstanceOf[CreateUser])
    lazy val disableAllUsers = AppComponent.Context.map(context =>
      Fragment.instantiate(context.getApplicationContext, classOf[DisableAllUsers].getName, null).asInstanceOf[DisableAllUsers])

    class UpdateUser
      extends SSHDDialog.Alert(AppComponent.Context.map(c => (XResource.getId(c, "ic_users", "drawable")))) {
      @volatile var user: Option[UserInfo] = None
      override protected lazy val positive = Some((android.R.string.ok,
        new SSHDDialog.ButtonListener(new WeakReference(UpdateUser.this),
          Some((dialog: UpdateUser) => fragment.foreach(_.onDialogUserUpdate())))))
      override protected lazy val negative = Some((android.R.string.cancel,
        new SSHDDialog.ButtonListener(new WeakReference(UpdateUser.this),
          Some(defaultNegativeButtonCallback))))

      def tag = "dialog_user_update"
      def title = Html.fromHtml(XResource.getString(getSherlockActivity, "users_update_user_title").
        getOrElse("Update user <b>%s</b>").format(user.map(_.name).getOrElse("unknown")))
      def message = Some(Html.fromHtml(XResource.getString(getSherlockActivity,
        "users_update_user_message").getOrElse("Do you want to save the changes?")))
    }
    class CreateUser
      extends SSHDDialog.Alert(AppComponent.Context.map(c => (XResource.getId(c, "ic_users", "drawable")))) {
      @volatile var userName: Option[String] = None
      override protected lazy val positive = Some((android.R.string.ok,
        new SSHDDialog.ButtonListener(new WeakReference(CreateUser.this),
          Some((dialog: CreateUser) => fragment.foreach(_.onDialogUserCreate)))))
      override protected lazy val negative = Some((android.R.string.cancel,
        new SSHDDialog.ButtonListener(new WeakReference(CreateUser.this),
          Some(defaultNegativeButtonCallback))))

      def tag = "dialog_user_create"
      def title = Html.fromHtml(XResource.getString(getSherlockActivity, "users_create_user_title").
        getOrElse("Create user <b>%s</b>").format(userName.getOrElse("unknown")))
      def message = Some(Html.fromHtml(XResource.getString(getSherlockActivity,
        "users_create_user_message").getOrElse("Do you want to add new user?")))
    }
    class DisableAllUsers
      extends SSHDDialog.Alert(AppComponent.Context.map(c => (XResource.getId(c, "ic_users", "drawable")))) {
      override protected lazy val positive = Some((android.R.string.ok,
        new SSHDDialog.ButtonListener(new WeakReference(DisableAllUsers.this),
          Some((dialog: DisableAllUsers) => fragment.foreach(_.onDialogDisableAllUsers)))))
      override protected lazy val negative = Some((android.R.string.cancel,
        new SSHDDialog.ButtonListener(new WeakReference(DisableAllUsers.this),
          Some(defaultNegativeButtonCallback))))

      def tag = "dialog_user_create"
      def title = XResource.getString(getSherlockActivity, "users_disable_all_title").
        getOrElse("Disable all users")
      def message = Some(Html.fromHtml(XResource.getString(getSherlockActivity,
        "users_disable_all_message").getOrElse("Are you sure you want to disable all users?")))
    }
  }
}
