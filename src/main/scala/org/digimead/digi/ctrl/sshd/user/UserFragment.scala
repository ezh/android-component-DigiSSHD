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

import com.actionbarsherlock.app.SherlockFragment
import org.digimead.digi.ctrl.sshd.SSHDActivity
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.aop.Loggable
import android.os.Bundle
import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDTabAdapter
import android.support.v4.app.Fragment
import scala.ref.WeakReference
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageButton
import android.widget.CheckBox
import java.util.concurrent.atomic.AtomicReference
import org.digimead.digi.ctrl.lib.info.UserInfo
import com.actionbarsherlock.app.SherlockListFragment
import android.text.TextWatcher
import android.text.Editable
import android.widget.CompoundButton
import org.digimead.digi.ctrl.sshd.ext.SherlockDynamicFragment
import android.widget.ListView
import android.view.ContextMenu
import android.view.MenuItem
import android.widget.AdapterView.AdapterContextMenuInfo
import org.digimead.digi.ctrl.lib.androidext.XResource
import scala.actors.Futures
import scala.util.Random
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import org.digimead.digi.ctrl.sshd.SSHDPreferences
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.service.option.AuthentificationMode

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
  private lazy val userPasswordEnableCheckbox = new WeakReference(dynamicFooter.get.map(_.findViewById(android.R.id.checkbox).
    asInstanceOf[CheckBox]).getOrElse(null))
  private val lastActiveUserInfo = new AtomicReference[Option[UserInfo]](None)
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
      userPasswordEnableCheckbox <- userPasswordEnableCheckbox.get
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
      userPasswordEnableCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) = updateFieldsState
      })
    }
  }
  @Loggable
  override def onResume() = {
    super.onResume
    /*    for { userGenerateButton <- userGenerateButton.get } {
      AuthentificationMode.getStateExt(this) match {
        case AuthentificationMode.AuthType.SingleUser =>
          setTitle(XResource.getString(this, "app_name_singleuser").getOrElse("DigiSSHD: Single User Mode"))
          SSHDUsers.multiUser = false
          userGenerateButton.setEnabled(false)
        case AuthentificationMode.AuthType.MultiUser =>
          setTitle(XResource.getString(this, "app_name_multiuser").getOrElse("DigiSSHD: Multi User Mode"))
          SSHDUsers.multiUser = true
          userGenerateButton.setEnabled(true)
        case invalid =>
          log.fatal("invalid authenticatin type \"" + invalid + "\"")
          None
      }
    }*/
    /*    for {
      dynamicHeader <- dynamicHeader.get
      dynamicFooter <- dynamicFooter.get
    } {
      if (SSHDActivity.collapsed.get) {
        dynamicHeader.setVisibility(View.GONE)
        dynamicFooter.setVisibility(View.GONE)
      } else {
        dynamicHeader.setVisibility(View.VISIBLE)
        dynamicFooter.setVisibility(View.VISIBLE)
      }
    }*/
    updateFieldsState()
    showDynamicFragment
  }
  @Loggable
  override def onPause() = {
    hideDynamicFragment
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
    userPasswordEnableCheckbox <- userPasswordEnableCheckbox.get
  } {
    /*    adapter.getItem(position) match {
      case user: UserInfo =>
        if (SSHDUsers.multiUser || user.name == "android") {
          lastActiveUserInfo.set(Some(user))
          userName.setText(user.name)
          userPassword.setText(user.password)
          userPasswordEnableCheckbox.setChecked(SSHDUsers.isPasswordEnabled(this, user))
          updateFieldsState()
        } else
          Toast.makeText(this, XResource.getString(this, "users_in_single_user_mode").getOrElse("only android user available in single user mode"), Toast.LENGTH_SHORT).show()
      case item =>
        log.fatal("unknown item " + item)*/
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) = for {
    adapter <- UserAdapter.adapter
  } {
    super.onCreateContextMenu(menu, v, menuInfo)
    /*menuInfo match {
      case info: AdapterContextMenuInfo =>
        adapter.getItem(info.position) match {
          case item: UserInfo =>
            menu.setHeaderTitle(item.name)
            menu.setHeaderIcon(XResource.getId(v.getContext, "ic_users", "drawable"))
            if (item.enabled)
              menu.add(Menu.NONE, XResource.getId(v.getContext, "users_disable"), 1,
                XResource.getString(v.getContext, "users_disable").getOrElse("Disable"))
            else
              menu.add(Menu.NONE, XResource.getId(v.getContext, "users_enable"), 1,
                XResource.getString(v.getContext, "users_enable").getOrElse("Enable"))
            if (item.name != "android")
              menu.add(Menu.NONE, XResource.getId(v.getContext, "users_delete"), 1,
                XResource.getString(v.getContext, "users_delete").getOrElse("Delete"))
            menu.add(Menu.NONE, XResource.getId(v.getContext, "users_copy_details"), 3,
              XResource.getString(v.getContext, "users_copy_details").getOrElse("Copy details"))
            menu.add(Menu.NONE, XResource.getId(v.getContext, "users_show_details"), 3,
              XResource.getString(v.getContext, "users_show_details").getOrElse("Show details"))
          case item =>
            log.fatal("unknown item " + item)
        }
      case info =>
        log.fatal("unsupported menu info " + info)
    }*/
  }
  @Loggable
  override def onContextItemSelected(menuItem: MenuItem): Boolean = {
    UserAdapter.adapter.map {
      adapter =>
        val info = menuItem.getMenuInfo.asInstanceOf[AdapterContextMenuInfo]
        adapter.getItem(info.position) match {
          case item: UserInfo =>
            menuItem.getItemId match {
              case id if id == XResource.getId(getSherlockActivity, "users_disable") =>
                //                SSHDUsers.Dialog.createDialogUserDisable(this, item, (state) => {}).show
                true
              case id if id == XResource.getId(getSherlockActivity, "users_enable") =>
                //                SSHDUsers.Dialog.createDialogUserEnable(this, item, (state) => {}).show
                true
              case id if id == XResource.getId(getSherlockActivity, "users_delete") =>
                /*                new AlertDialog.Builder(this).
                  setTitle(XResource.getString(this, "users_delete_title").getOrElse("Delete user \"%s\"").format(item.name)).
                  setMessage(XResource.getString(this, "users_delete_message").getOrElse("Do you want to delete \"%s\" account?").format(item.name)).
                  setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    def onClick(dialog: DialogInterface, whichButton: Int) = {
                      val message = XResource.getString(SSHDUsers.this, "users_deleted_message").
                        getOrElse("deleted user \"%s\"").format(item.name)
                      IAmWarn(message)
                      Toast.makeText(SSHDUsers.this, message, Toast.LENGTH_SHORT).show()
                      adapter.remove(item)
                      SSHDUsers.remove(SSHDUsers.this, item)
                      updateFieldsState()
                      if (lastActiveUserInfo.get.exists(_ == item))
                        lastActiveUserInfo.set(None)
                    }
                  }).
                  setNegativeButton(android.R.string.cancel, null).
                  setIcon(android.R.drawable.ic_dialog_alert).
                  create().show()*/
                true
              case id if id == XResource.getId(getSherlockActivity, "users_copy_details") =>
                /*                try {
                  val message = XResource.getString(SSHDUsers.this, "users_copy_details").
                    getOrElse("Copy details about \"%s\" to clipboard").format(item.name)
                  runOnUiThread(new Runnable {
                    def run = try {
                      val clipboard = SSHDUsers.this.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
                      clipboard.setText("login: %s\nhome: %s\npassword: %s\nenabled: %s\n".format(item.name, item.home, item.password, item.enabled))
                      Toast.makeText(SSHDUsers.this, message, Toast.LENGTH_SHORT).show()
                    } catch {
                      case e =>
                        IAmYell("Unable to copy to clipboard information about \"" + item.name + "\"", e)
                    }
                  })
                } catch {
                  case e =>
                    IAmYell("Unable to copy to clipboard details about \"" + item.name + "\"", e)
                }*/
                true
              case id if id == XResource.getId(getSherlockActivity, "users_show_details") =>
                /*                try {
                  SSHDUsers.Dialog.createDialogUserDetails(this, item).show()
                } catch {
                  case e =>
                    IAmYell("Unable to show details about \"" + item.name + "\"", e)
                }*/
                true
              case id =>
                log.fatal("unknown action " + id)
                false
            }
          case item =>
            log.fatal("unknown item " + item)
            false
        }
    }
  } getOrElse false
  @Loggable
  def onClickApply(v: View) = synchronized {
    for {
      userName <- userName.get
      userPassword <- userPassword.get
      adapter <- UserAdapter.adapter
      userPasswordEnableCheckbox <- userPasswordEnableCheckbox.get
    } {
      val name = userName.getText.toString.trim
      val password = userPassword.getText.toString.trim
      assert(name.nonEmpty && password.nonEmpty, "one of user fields is empty")
      lastActiveUserInfo.get match {
        case Some(user) if name == "android" =>
        /*          new AlertDialog.Builder(this).
            setTitle(XResource.getString(v.getContext, "users_update_user_title").getOrElse("Update user \"%s\"").format(name)).
            setMessage(XResource.getString(v.getContext, "users_update_user_message").getOrElse("Do you want to save the changes?")).
            setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
              def onClick(dialog: DialogInterface, whichButton: Int) = {
                val message = XResource.getString(v.getContext, "users_update_message").
                  getOrElse("update user \"%s\"").format(name)
                IAmWarn(message)
                Toast.makeText(v.getContext, message, Toast.LENGTH_SHORT).show()
                val newUser = user.copy(password = password)
                lastActiveUserInfo.set(Some(newUser))
                SSHDUsers.save(v.getContext, newUser)
                SSHDUsers.setPasswordEnabled(userPasswordEnableCheckbox.isChecked, v.getContext, newUser)
                val position = adapter.getPosition(user)
                adapter.remove(user)
                adapter.insert(newUser, position)
                updateFieldsState()
                SSHDUsers.this.getListView.setSelectionFromTop(position, 5)
              }
            }).
            setNegativeButton(android.R.string.cancel, null).
            setIcon(android.R.drawable.ic_dialog_alert).
            create().show()*/
        case Some(user) if UserAdapter.list.exists(_.name == name) =>
        /*          new AlertDialog.Builder(this).
            setTitle(XResource.getString(v.getContext, "users_update_user_title").getOrElse("Update user \"%s\"").format(name)).
            setMessage(XResource.getString(v.getContext, "users_update_user_message").getOrElse("Do you want to save the changes?")).
            setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
              def onClick(dialog: DialogInterface, whichButton: Int) = {
                val message = XResource.getString(v.getContext, "users_update_message").
                  getOrElse("update user \"%s\"").format(name)
                IAmWarn(message)
                Toast.makeText(v.getContext, message, Toast.LENGTH_SHORT).show()
                val newUser = user.copy(name = name, password = password)
                lastActiveUserInfo.set(Some(newUser))
                SSHDUsers.save(v.getContext, newUser)
                SSHDUsers.setPasswordEnabled(userPasswordEnableCheckbox.isChecked, v.getContext, newUser)
                val position = adapter.getPosition(user)
                adapter.remove(user)
                adapter.insert(newUser, position)
                updateFieldsState()
                SSHDUsers.this.getListView.setSelectionFromTop(position, 5)
              }
            }).
            setNegativeButton(android.R.string.cancel, null).
            setIcon(android.R.drawable.ic_dialog_alert).
            create().show()*/
        case _ =>
        /*          new AlertDialog.Builder(this).
            setTitle(XResource.getString(v.getContext, "users_create_user_title").getOrElse("Create user \"%s\"").format(name)).
            setMessage(XResource.getString(v.getContext, "users_create_user_message").getOrElse("Do you want to save the changes?")).
            setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
              def onClick(dialog: DialogInterface, whichButton: Int) = {
                val message = XResource.getString(v.getContext, "users_create_message").
                  getOrElse("created user \"%s\"").format(name)
                IAmWarn(message)
                Toast.makeText(v.getContext, message, Toast.LENGTH_SHORT).show()
                // home
                val home = AppControl.Inner.getExternalDirectory(DTimeout.normal).flatMap(d => Option(d)).getOrElse({
                  val sdcard = new File("/sdcard")
                  if (!sdcard.exists)
                    new File("/")
                  else
                    sdcard
                }).getAbsolutePath
                val newUser = UserInfo(name, password, home, true)
                lastActiveUserInfo.set(Some(newUser))
                SSHDUsers.save(v.getContext, newUser)
                SSHDUsers.setPasswordEnabled(userPasswordEnableCheckbox.isChecked, v.getContext, newUser)
                val position = (SSHDUsers.list :+ newUser).sortBy(_.name).indexOf(newUser)
                adapter.insert(newUser, position)
                updateFieldsState()
                SSHDUsers.this.getListView.setSelectionFromTop(position, 5)
              }
            }).
            setNegativeButton(android.R.string.cancel, null).
            setIcon(android.R.drawable.ic_dialog_alert).
            create().show()*/
      }
    }
  }
  @Loggable
  def onClickToggleBlockAll(v: View) = {
    /*    new AlertDialog.Builder(this).
      setTitle(XResource.getString(v.getContext, "users_disable_all_title").getOrElse("Disable all users")).
      setMessage(XResource.getString(v.getContext, "users_disable_all_message").getOrElse("Are you sure you want to disable all users?")).
      setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, whichButton: Int) = SSHDUsers.this.synchronized {
          for {
            adapter <- SSHDUsers.adapter
          } {
            adapter.setNotifyOnChange(false)
            SSHDUsers.list.foreach(user => {
              IAmMumble("disable user \"%s\"".format(user.name))
              val newUser = user.copy(enabled = false)
              SSHDUsers.save(v.getContext, newUser)
              val position = adapter.getPosition(user)
              adapter.remove(user)
              adapter.insert(newUser, position)
              if (lastActiveUserInfo.get.exists(_ == user))
                lastActiveUserInfo.set(Some(newUser))
            })
            adapter.setNotifyOnChange(true)
            adapter.notifyDataSetChanged
          }
          val message = XResource.getString(v.getContext, "users_all_disabled").getOrElse("all users are disabled")
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
  def onClickGenerateNewUser(v: View) = Futures.future {
/*    try {
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
      val password = SSHDUsers.generate()
      for {
        userName <- userName.get
        userPasswrod <- userPassword.get
      } {
        runOnUiThread(new Runnable {
          def run {
            userName.setText(name)
            userPasswrod.setText(password)
            updateFieldsState()
          }
        })
      }
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }*/
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
  } {
    if (UserFragment.showPassword) {
      UserFragment.showPassword = false
      userPasswordShowButton.setSelected(false)
      userPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
      userPassword.setTransformationMethod(PasswordTransformationMethod.getInstance())
    } else {
      UserFragment.showPassword = true
      userPasswordShowButton.setSelected(true)
      userPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
    }
  }
  @Loggable
  private def updateFieldsState(): Unit = for {
    userName <- userName.get
    userPassword <- userPassword.get
    apply <- apply.get
    blockAll <- blockAll.get
    deleteAll <- deleteAll.get
    userPasswordEnableCheckbox <- userPasswordEnableCheckbox.get
  } {
    // set apply
    if (lastActiveUserInfo.get.nonEmpty) {
      if (lastActiveUserInfo.get.exists(u =>
        u.name == userName.getText.toString.trim &&
          u.password == userPassword.getText.toString.trim &&
          userPasswordEnableCheckbox.isChecked == UserAdapter.isPasswordEnabled(userPasswordEnableCheckbox.getContext, u)))
        apply.setEnabled(false)
      else if (userName.getText.toString.trim.nonEmpty &&
        userPassword.getText.toString.trim.nonEmpty)
        apply.setEnabled(true)
      else
        apply.setEnabled(false)
    } else {
      if (userName.getText.toString.trim.nonEmpty &&
        userPassword.getText.toString.trim.nonEmpty) {
        apply.setEnabled(true)
      } else
        apply.setEnabled(false)
    }
    // set userName
    if (lastActiveUserInfo.get.exists(_.name == "android"))
      userName.setEnabled(false)
    else
      userName.setEnabled(true)
    // set userPassword
    if (userPasswordEnableCheckbox.isChecked)
      userPassword.setEnabled(true)
    else
      userPassword.setEnabled(false)
    // single user mode
    if (SSHDPreferences.AuthentificationMode.get(getSherlockActivity) != AuthentificationMode.AuthType.MultiUser) {
      userName.setEnabled(false)
      blockAll.setEnabled(false)
      deleteAll.setEnabled(false)
      return
    }
    // set block all
    if (UserAdapter.list.exists(_.enabled))
      blockAll.setEnabled(true)
    else
      blockAll.setEnabled(false)
    // set delete all
    if (UserAdapter.list.exists(_.name != "android"))
      deleteAll.setEnabled(true)
    else
      deleteAll.setEnabled(false)
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
}
