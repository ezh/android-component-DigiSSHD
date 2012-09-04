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

import java.io.File

import scala.actors.Futures
import scala.ref.WeakReference

import org.digimead.digi.lib.ctrl.AnyBase
import org.digimead.digi.lib.ctrl.ext.SafeDialog
import org.digimead.digi.lib.ctrl.ext.XDialog
import org.digimead.digi.lib.ctrl.ext.XDialog.dialog2string
import org.digimead.digi.lib.ctrl.ext.XResource
import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.ctrl.base.AppComponent
import org.digimead.digi.lib.ctrl.base.AppControl
import org.digimead.digi.lib.ctrl.declaration.DOption
import org.digimead.digi.lib.ctrl.declaration.DState
import org.digimead.digi.lib.ctrl.declaration.DTimeout
import org.digimead.digi.lib.ctrl.info.UserInfo
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.ctrl.message.IAmBusy
import org.digimead.digi.lib.ctrl.message.IAmMumble
import org.digimead.digi.lib.ctrl.message.IAmReady
import org.digimead.digi.lib.ctrl.message.Origin.anyRefToOrigin
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDResource
import org.digimead.digi.ctrl.sshd.ext.SSHDAlertDialog
import org.digimead.digi.ctrl.sshd.service.TabContent
import org.digimead.digi.ctrl.sshd.user.UserAdapter
import org.digimead.digi.ctrl.sshd.user.UserDialog
import org.digimead.digi.ctrl.sshd.user.UserKeys

import android.content.Context
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentTransaction
import android.text.Html
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.ListView
import android.widget.Toast

object DefaultUser extends CheckBoxItem with Logging {
  val option: DOption.OptVal = DOption.Value("android_user", classOf[Boolean], true: java.lang.Boolean)
  @volatile private var android: UserInfo = AppComponent.Context.flatMap(c => UserAdapter.find(c, "android")) getOrElse { log.fatal("unable to find 'android' user"); null }

  @Loggable
  def onCheckboxClick(view: CheckBox, lastState: Boolean): Unit = TabContent.fragment.map {
    fragment =>
      val context = fragment.getSherlockActivity
      if (UserAdapter.isSingleUser(context))
        AnyBase.runOnUiThread {
          updateCheckbox(view)
          Toast.makeText(context, Html.fromHtml(XResource.getString(context, "user_android_enabled_singleuser").
            getOrElse("<b>android</b> is always enabled in single user mode")), Toast.LENGTH_SHORT).show()
        }
      else {
        val activity = fragment.getSherlockActivity
        if (android.enabled)
          UserDialog.disable(activity, android, Some((user) => {
            android = user
            view.setChecked(user.enabled)
          }))
        else
          UserDialog.enable(activity, android, Some((user) => {
            android = user
            view.setChecked(user.enabled)
          }))
      }
  }
  @Loggable
  def updateCheckbox(view: CheckBox) = {
    val newState = getState[Boolean](view.getContext)
    if (view.isChecked != newState)
      view.setChecked(newState)
  }
  @Loggable
  def updateUser(user: UserInfo) = for {
    view <- view.get
    checkbox <- Option(view.findViewById(_root_.android.R.id.checkbox))
  } {
    assert(user.name == "android")
    android = user
    AnyBase.runOnUiThread { updateCheckbox(checkbox.asInstanceOf[CheckBox]) }
  }
  @Loggable
  override def onListItemClick(l: ListView, v: View) = for {
    fragment <- TabContent.fragment
    dialog <- SSHDResource.userChangePassword
  } if (!dialog.isShowing) {
    val context = fragment.getSherlockActivity
    UserDialog.changePassword(context, android, Some(onDialogUserUpdate(context, _: UserInfo)))
  }
  @Loggable
  def onDialogUserUpdate(context: Context, newUser: UserInfo) = {
    if (UserAdapter.isSingleUser(context) && AppComponent.Inner.state.get.value == DState.Active) for {
      fragment <- TabContent.fragment
      dialog <- SSHDResource.serviceRestartRequired
    } if (!dialog.isShowing)
      Dialog.showRestartRequired(fragment.getSherlockActivity)
    android = newUser
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
    val context = v.getContext
    log.debug("create context menu for " + option.tag)
    menu.setHeaderTitle(Html.fromHtml(XResource.getString(context, "android_user_context_menu_title").
      getOrElse("user <b>android</b>")))
    menu.setHeaderIcon(XResource.getId(context, "ic_users", "drawable"))
    // keys
    val keysMenu = menu.addSubMenu(Menu.NONE, XResource.getId(context, "user_keys_menu"), 4,
      XResource.getString(context, "user_keys_menu").getOrElse("Key management"))
    keysMenu.setHeaderIcon(XResource.getId(context, "ic_users", "drawable"))
    val generateKeyItem = keysMenu.add(Menu.NONE, XResource.getId(context, "generate_user_key"), 1,
      XResource.getString(context, "generate_user_key").getOrElse("Generate user key"))
    val importKeyItem = keysMenu.add(Menu.NONE, XResource.getId(context, "import_user_key"), 2,
      XResource.getString(context, "import_user_key").getOrElse("Import public key"))
    val exportDropbearKeyItem = keysMenu.add(Menu.NONE, XResource.getId(context, "export_user_key_dropbear"), 3,
      XResource.getString(context, "export_user_key_dropbear").getOrElse("Export private key (Dropbear)"))
    val exportOpenSSHKeyItem = keysMenu.add(Menu.NONE, XResource.getId(context, "export_user_key_openssh"), 4,
      XResource.getString(context, "export_user_key_openssh").getOrElse("Export private key (OpenSSH)"))
    // details
    val detailsMenu = menu.addSubMenu(Menu.NONE, XResource.getId(context, "user_details_menu"), 5,
      XResource.getString(context, "user_details_menu").getOrElse("Details"))
    detailsMenu.setHeaderIcon(XResource.getId(context, "ic_users", "drawable"))
    detailsMenu.add(Menu.NONE, XResource.getId(context, "user_copy_details"), 1,
      XResource.getString(context, "user_copy_details").getOrElse("Copy"))
    detailsMenu.add(Menu.NONE, XResource.getId(context, "user_show_details"), 2,
      XResource.getString(context, "user_show_details").getOrElse("Show"))
    // disable unavailable items
    if (AppControl.isBound) {
      val publicKeyFileFuture = Futures.future { UserKeys.getPublicKeyFile(context, android) }
      val dropbearKeyFileFuture = Futures.future { UserKeys.getDropbearKeyFile(context, android) }
      val opensshKeyFileFuture = Futures.future { UserKeys.getOpenSSHKeyFile(context, android) }
      val result = Futures.awaitAll(DTimeout.shortest + 500, publicKeyFileFuture,
        dropbearKeyFileFuture, opensshKeyFileFuture).asInstanceOf[List[Option[Option[File]]]]
      if (result(0).flatMap(f => f).isEmpty) {
        generateKeyItem.setEnabled(false)
        importKeyItem.setEnabled(false)
      }
      if (result(1).flatMap(_.map(_.exists)) != Some(true)) exportDropbearKeyItem.setEnabled(false)
      if (result(2).flatMap(_.map(_.exists)) != Some(true)) exportOpenSSHKeyItem.setEnabled(false)
    } else {
      generateKeyItem.setEnabled(false)
      importKeyItem.setEnabled(false)
      exportDropbearKeyItem.setEnabled(false)
      exportOpenSSHKeyItem.setEnabled(false)
    }
  }
  override def onContextItemSelected(menuItem: MenuItem): Boolean = TabContent.fragment.map {
    fragment =>
      val context = fragment.getActivity
      menuItem.getItemId match {
        case id if id == XResource.getId(context, "generate_user_key") =>
          Futures.future { UserDialog.generateUserKey(context, android, UserKeys.getDropbearKeyFile(context, android)) }
          true
        case id if id == XResource.getId(context, "import_user_key") =>
          Futures.future { UserDialog.importKey(context, android) }
          true
        case id if id == XResource.getId(context, "export_user_key_dropbear") =>
          Futures.future { UserKeys.exportDropbearKey(context, android) }
          true
        case id if id == XResource.getId(context, "export_user_key_openssh") =>
          Futures.future { UserKeys.exportOpenSSHKey(context, android) }
          true
        case id if id == XResource.getId(context, "user_copy_details") =>
          Futures.future { UserAdapter.copyDetails(context, android) }
          true
        case id if id == XResource.getId(context, "user_show_details") =>
          Futures.future { UserDialog.showDetails(context, android) }
          true
        case item =>
          log.fatal("skip unknown menu! item " + item)
          false
      }
  } getOrElse false
  override def getState[T](context: Context)(implicit m: Manifest[T]): T = {
    assert(m.erasure == option.kind)
    if (UserAdapter.isSingleUser(context))
      true.asInstanceOf[T] // android user always enable in single user mode
    else
      android.enabled.asInstanceOf[T]
  }
  override def getView(context: Context, inflater: LayoutInflater): View = {
    val view = super.getView(context, inflater)
    val checkbox = view.findViewById(_root_.android.R.id.checkbox).asInstanceOf[CheckBox]
    checkbox.setClickable(true)
    checkbox.setOnTouchListener(new View.OnTouchListener {
      override def onTouch(v: View, event: MotionEvent): Boolean = {
        if (event.getAction() == MotionEvent.ACTION_DOWN)
          Futures.future { onCheckboxClick(v.asInstanceOf[CheckBox], getState[Boolean](v.getContext)) }
        true
      }
    })
    view
  }

  object Dialog {
    @Loggable
    def showRestartRequired(activity: FragmentActivity) =
      SSHDResource.serviceRestartRequired.foreach(dialog =>
        SafeDialog(activity, dialog, () => dialog).transaction((ft, fragment, target) => {
          ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
          ft.addToBackStack(dialog)
        }).show())

    class RestartRequired extends SSHDAlertDialog(Some(_root_.android.R.drawable.ic_dialog_alert)) with Logging {
      override protected lazy val positive = Some((_root_.android.R.string.ok,
        new XDialog.ButtonListener(new WeakReference(RestartRequired.this),
          Some((dialog: RestartRequired) => onRestartRequired))))
      override protected lazy val negative = Some((_root_.android.R.string.cancel,
        new XDialog.ButtonListener(new WeakReference(RestartRequired.this),
          Some(defaultButtonCallback))))

      def tag = "dialog_service_restartrequired"
      def title = XResource.getString(getSherlockActivity, "warning_singleusermode_restart_title").getOrElse("Apply new settings")
      def message = Some(XResource.getString(getSherlockActivity, "session_singleusermode_restart_content").
        getOrElse("Single user/basic mode provide only restricted abilities of session control.\n\n" +
          "You must restart SSH before the new settings will take effect. Do you want to restart service now?"))
      @Loggable
      private def onRestartRequired() {
        val context = getSherlockActivity
        IAmBusy(DefaultUser, XResource.getString(context, "state_stopping_service").getOrElse("stopping service"))
        Futures.future {
          AppControl.Inner.callStop(context.getPackageName)() match {
            case true =>
              IAmMumble(XResource.getString(context, "state_stopped_service").getOrElse("stopped service"))
              IAmMumble(XResource.getString(context, "state_starting_service").getOrElse("starting service"))
              Futures.future {
                AppControl.Inner.callStart(context.getPackageName)() match {
                  case true =>
                    log.info(context.getPackageName + " started")
                    IAmReady(DefaultUser, XResource.getString(context, "state_started_service").getOrElse("started service"))
                  case false =>
                    log.warn(context.getPackageName + " start failed")
                    IAmReady(DefaultUser, XResource.getString(context, "state_started_service").getOrElse("started service"))
                }
              } case false =>
              IAmReady(DefaultUser, XResource.getString(context, "state_stopped_service").getOrElse("stopped service"))
          }
        }
      }
    }
  }
}
