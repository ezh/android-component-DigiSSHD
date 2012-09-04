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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import scala.Option.option2Iterable
import scala.actors.Futures
import scala.collection.JavaConversions._
import scala.ref.WeakReference

import org.digimead.digi.lib.ctrl.AnyBase
import org.digimead.digi.lib.ctrl.ext.SafeDialog
import org.digimead.digi.lib.ctrl.ext.XDialog
import org.digimead.digi.lib.ctrl.ext.XDialog.dialog2string
import org.digimead.digi.lib.ctrl.ext.XResource
import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.ctrl.base.AppComponent
import org.digimead.digi.lib.ctrl.dialog.{ FileChooser => LibFileChooser }
import org.digimead.digi.lib.ctrl.info.UserInfo
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.ctrl.message.IAmWarn
import org.digimead.digi.lib.ctrl.message.IAmYell
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDPreferences
import org.digimead.digi.ctrl.sshd.SSHDResource
import org.digimead.digi.ctrl.sshd.ext.SSHDAlertDialog
import org.digimead.digi.ctrl.sshd.ext.SSHDListDialog
import org.digimead.digi.ctrl.sshd.service.option.DefaultUser

import android.content.Context
import android.support.v4.app.FragmentActivity
import android.text.Editable
import android.text.Html
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

object UserDialog extends Logging {
  lazy val userNameFilter = new InputFilter {
    /*
     * man 5 passwd:
     * The login name may be up to 31 characters long. For compatibility with
     * legacy software, a login name should start with a letter and consist
     * solely of letters, numbers, dashes and underscores. The login name must
     * never begin with a hyphen (`-'); also, it is strongly suggested that nei-
     * ther uppercase characters nor dots (`.') be part of the name, as this
     * tends to confuse mailers. 
     */
    def filter(source: CharSequence, start: Int, end: Int,
      dest: Spanned, dstart: Int, dend: Int): CharSequence = {
      for (i <- start until end)
        if (!(UserAdapter.numbers ++ UserAdapter.alphabet ++ """_-""").exists(_ == source.charAt(i)))
          return ""
      if (source.toString.nonEmpty && dest.toString.isEmpty &&
        """_-""".exists(_ == source.toString.head))
        return ""
      return null
    }
  }
  lazy val userHomeFilter = new InputFilter {
    def filter(source: CharSequence, start: Int, end: Int,
      dest: Spanned, dstart: Int, dend: Int): CharSequence = {
      for (i <- start until end)
        if ("""|\?*<":>+[]'""".toList.exists(_ == source.charAt(i)))
          return ""
      return null
    }
  }
  lazy val userPasswordFilter = new InputFilter {
    def filter(source: CharSequence, start: Int, end: Int,
      dest: Spanned, dstart: Int, dend: Int): CharSequence = {
      if (dest.length > 15)
        return ""
      for (i <- start until end)
        if (!UserAdapter.defaultPasswordCharacters.exists(_ == source.charAt(i)))
          return ""
      return null
    }
  }

  @Loggable
  def changePassword(activity: FragmentActivity, user: UserInfo, onOkCallback: Option[UserInfo => Unit]) =
    SSHDResource.userChangePassword.foreach(dialog =>
      SafeDialog(activity, dialog, () => dialog).target(R.id.main_topPanel).
        transaction(SSHDPreferences.defaultTransaction(dialog)).before {
          (dialog) =>
            dialog.user = Some(user)
            dialog.onOkCallback = onOkCallback
        }.show())
  @Loggable
  def enable(activity: FragmentActivity, user: UserInfo, callback: Option[UserInfo => Unit] = None) =
    SSHDResource.userEnable.foreach(dialog =>
      SafeDialog(activity, dialog, () => dialog).
        transaction(SSHDPreferences.defaultTransaction(dialog)).before {
          dialog =>
            dialog.user = Some(user)
            dialog.onOkCallback = callback
        }.show())
  @Loggable
  def disable(activity: FragmentActivity, user: UserInfo, callback: Option[UserInfo => Unit] = None) =
    SSHDResource.userDisable.foreach(dialog =>
      SafeDialog(activity, dialog, () => dialog).
        transaction(SSHDPreferences.defaultTransaction(dialog)).before {
          dialog =>
            dialog.user = Some(user)
            dialog.onOkCallback = callback
        }.show())
  @Loggable
  def delete(activity: FragmentActivity, user: UserInfo) =
    SSHDResource.userDelete.foreach(dialog =>
      SafeDialog(activity, dialog, () => dialog).target(R.id.main_topPanel).
        transaction(SSHDPreferences.defaultTransaction(dialog)).before(dialog => dialog.user = Some(user)).show())
  @Loggable
  def setGUID(activity: FragmentActivity, user: UserInfo) =
    SSHDResource.userSetGUID.foreach(dialog =>
      SafeDialog(activity, dialog, () => dialog).target(R.id.main_topPanel).
        transaction(SSHDPreferences.defaultTransaction(dialog)).before(dialog => {
          dialog.user = Some(user)
          dialog.userExt = UserInfoExt.get(dialog.getSherlockActivity, user)
        }).show())
  @Loggable
  def showDetails(activity: FragmentActivity, user: UserInfo) =
    SSHDResource.userShowDetails.foreach(dialog =>
      SafeDialog(activity, dialog, () => dialog).target(R.id.main_topPanel).
        transaction(SSHDPreferences.defaultTransaction(dialog)).before(dialog => dialog.user = Some(user)).show())
  @Loggable
  def setHome(activity: FragmentActivity, user: UserInfo) =
    SSHDResource.userSetHome.foreach(dialog =>
      SafeDialog(activity, dialog, () => dialog).target(R.id.main_topPanel).
        transaction(SSHDPreferences.defaultTransaction(dialog)).before(dialog => {
          dialog.user = Some(user)
          dialog.setCallbackOnResult(setHomeCallback(activity, user, _, _))
        }).show())
  @Loggable
  private def setHomeCallback(context: FragmentActivity, oldUser: UserInfo, selectedDirectory: File, selectedFiles: Seq[File]) = {
    log.debug(oldUser.name + " new home is " + selectedDirectory)
    val newUser = oldUser.copy(home = selectedDirectory.getAbsolutePath)
    val message = Html.fromHtml(XResource.getString(context, "user_update_message").
      getOrElse("update user <b>%s</b>").format(oldUser.name))
    IAmWarn(message.toString)
    Futures.future {
      UserAdapter.updateUser(Some(newUser), Some(oldUser))
      UserFragment.updateUser(Some(newUser), Some(oldUser))
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
  }
  @Loggable
  def generateUserKey(activity: FragmentActivity, user: UserInfo, file: Option[File]) = file match {
    case Some(file) =>
      checkKeyAlreadyExists(activity, user, "User", file, () => selectKeyType(activity, user))
    case None =>
      val message = Html.fromHtml(XResource.getString(activity, "user_unable_generate_key").
        getOrElse("unable generate key for user <b>%s</b>").format(user.name))
      IAmYell(message.toString)
      Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
  }
  @Loggable
  def checkKeyAlreadyExists(activity: FragmentActivity, user: UserInfo, keyName: String,
    key: File, callback: () => Unit) = if (!key.exists || key.length == 0)
    callback()
  else
    SSHDResource.userKeyReplace.foreach(dialog =>
      SafeDialog(activity, dialog, () => dialog).target(R.id.main_topPanel).
        transaction(SSHDPreferences.defaultTransaction(dialog)).before(dialog => {
          dialog.user = Some(user)
        }).show())
  @Loggable
  def selectKeyType(activity: FragmentActivity, user: UserInfo) =
    SSHDResource.userKeyType.foreach(dialog =>
      SafeDialog(activity, dialog, () => dialog).target(R.id.main_topPanel).
        transaction(SSHDPreferences.defaultTransaction(dialog)).before(dialog => {
          dialog.user = Some(user)
        }).show())
  //UserKeys.generateUserKey(fragment.getSherlockActivity)
  /*
   *   private def generateUserKey(activity: SherlockFragmentActivity) {
    log.g_a_s_e("!!!")
    SSHDResource.userGenerateKey.foreach(dialog =>
      SafeDialog(activity, dialog, () => dialog).transaction((ft, fragment, target) => {
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        ft.addToBackStack(dialog)
      }).before(dialog => dialog.user = Some(android)).show())
  }
   */
  @Loggable
  def importKey(activity: FragmentActivity, user: UserInfo) {
    /*AppComponent.Inner.showDialogSafe[Dialog](activity, "service_import_userkey_dialog", () => {
      val dialog = FileChooser.createDialog(activity,
        XResource.getString(activity, "dialog_import_key").getOrElse("Import public key"),
        new File("/"),
        importKeyOnResult,
        new FileFilter { override def accept(file: File) = true },
        importKeyOnClick,
        false,
        user)
      dialog.show()
      dialog
    })*/
  }

  class ChangePassword
    extends SSHDAlertDialog(AppComponent.Context.map(c => (XResource.getId(c, "ic_users", "drawable")))) {
    @volatile var user: Option[UserInfo] = None
    @volatile var onOkCallback: Option[UserInfo => Unit] = None
    private val (innerContent, enablePasswordButton, togglePasswordButton, passwordField) = {
      val customView = ChangePassword.customView()
      (customView.map(_._1), customView.map(_._2), customView.map(_._3), customView.map(_._4))
    }
    override lazy val extContent = innerContent
    override protected lazy val positive = Some((android.R.string.yes, new XDialog.ButtonListener(new WeakReference(ChangePassword.this),
      Some((dialog: ChangePassword) => {
        onPositiveButtonClick
        defaultButtonCallback(dialog)
      }))))
    override protected lazy val negative = Some((android.R.string.no, new XDialog.ButtonListener(new WeakReference(ChangePassword.this),
      Some(defaultButtonCallback))))
    for {
      enablePasswordButton <- enablePasswordButton
      togglePasswordButton <- togglePasswordButton
      passwordField <- passwordField
    } {
      passwordField.addTextChangedListener(new ChangePassword.PasswordFieldTextChangedListener(positiveView, new WeakReference(this)))
      togglePasswordButton.setOnClickListener(new ChangePassword.TogglePasswordButtonOnClickListener(passwordField))
      enablePasswordButton.setOnCheckedChangeListener(new ChangePassword.EnablePasswordButtonOnCheckedChangeListener(positiveView,
        togglePasswordButton, passwordField, new WeakReference(this)))
    }

    def tag = "dialog_user_changepassword"
    def title = Html.fromHtml(XResource.getString(getSherlockActivity, "dialog_password_title").
      getOrElse("<b>%s</b> user password").format(user.map(_.name).getOrElse("unknown")))
    def message = Some(Html.fromHtml(XResource.getString(getSherlockActivity, "dialog_password_message").
      getOrElse("Please select a password. User password must be at least 1 characters long." +
        "Password cannot be more than 16 characters. Only standard unix password characters are allowed.")))
    @Loggable
    override def onResume() {
      val context = getSherlockActivity
      // update user that already loaded
      user.foreach(existsUser => user = UserAdapter.find(context, existsUser.name))
      for {
        user <- user
        enablePasswordButton <- enablePasswordButton
        togglePasswordButton <- togglePasswordButton
        passwordField <- passwordField
        ok <- positiveView.get
      } {
        val isUserPasswordEnabled = Futures.future { UserAdapter.isPasswordEnabled(context, user) }
        ok.setEnabled(false)
        enablePasswordButton.setChecked(isUserPasswordEnabled())
        passwordField.setEnabled(isUserPasswordEnabled())
        passwordField.setText(user.password)
      }
      super.onResume
    }
    @Loggable
    override def onDestroyView() {
      super.onDestroyView
      user = None
      onOkCallback = None
    }
    @Loggable
    private def onPositiveButtonClick() = for {
      oldUser <- user
      callback <- onOkCallback
      passwordField <- passwordField
      enablePasswordButton <- enablePasswordButton
    } {
      val valuePassword = passwordField.getText.toString
      val valueIsPasswordEnabled = enablePasswordButton.isChecked
      onOkCallback = None
      Futures.future {
        try {
          val context = getSherlockActivity
          val notification = XResource.getString(context, "user_change_password").getOrElse("password changed for user %1$s").format(oldUser.name)
          IAmWarn(notification.format(oldUser.name))
          val newUser = oldUser.copy(password = valuePassword)
          UserAdapter.updateUser(Some(newUser), Some(oldUser))
          UserAdapter.setPasswordEnabled(valueIsPasswordEnabled, context, newUser)
          UserFragment.updateUser(Some(newUser), Some(oldUser))
          if (oldUser.name == "android")
            DefaultUser.updateUser(newUser)
          AnyBase.runOnUiThread {
            Toast.makeText(context, notification.format(oldUser.name), Toast.LENGTH_SHORT).show()
            callback(newUser)
          }
        } catch {
          case e =>
            log.error(e.getMessage, e)
        }
      }
    }
  }
  object ChangePassword {
    private val maxLengthFilter = new InputFilter.LengthFilter(5)

    def customView(): Option[(ViewGroup, CheckBox, ImageButton, EditText)] = AppComponent.AppContext.flatMap { context =>
      val view = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].
        inflate(R.layout.dialog_edittext, null).asInstanceOf[ViewGroup]
      val inner = view.findViewById(android.R.id.edit).getParent.asInstanceOf[LinearLayout]
      val enablePasswordButton = new CheckBox(context)
      enablePasswordButton.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
      inner.addView(enablePasswordButton, 0)
      val togglePasswordButton = new ImageButton(context)
      togglePasswordButton.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
      togglePasswordButton.setImageResource(R.drawable.btn_eye)
      togglePasswordButton.setBackgroundResource(_root_.android.R.color.transparent)
      inner.addView(togglePasswordButton)
      val passwordField = inner.findViewById(_root_.android.R.id.edit).asInstanceOf[EditText]
      passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
      passwordField.setFilters(Array(userPasswordFilter))
      Some(view, enablePasswordButton, togglePasswordButton, passwordField)
    }
    class PasswordFieldTextChangedListener(ok: AtomicReference[Option[View]], dialog: WeakReference[ChangePassword]) extends TextWatcher {
      override def afterTextChanged(s: Editable) = for {
        dialog <- dialog.get
        initialUser <- dialog.user
        ok <- ok.get
      } try {
        val newPassword = s.toString
        if (newPassword.nonEmpty) {
          ok.setEnabled(newPassword != initialUser.password)
        } else
          ok.setEnabled(false)
      } catch {
        case e =>
          log.warn(e.getMessage, e)
      }
      override def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
      override def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    }
    class TogglePasswordButtonOnClickListener(passwordField: EditText) extends View.OnClickListener {
      val showPassword = new AtomicBoolean(false)
      def onClick(v: View) {
        val togglePasswordButton = v.asInstanceOf[ImageButton]
        if (showPassword.compareAndSet(true, false)) {
          togglePasswordButton.setSelected(false)
          passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
          passwordField.setTransformationMethod(PasswordTransformationMethod.getInstance())
        } else {
          showPassword.set(true)
          togglePasswordButton.setSelected(true)
          passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        }
      }
    }
    class EnablePasswordButtonOnCheckedChangeListener(ok: AtomicReference[Option[View]], togglePasswordButton: ImageButton,
      passwordField: EditText, dialog: WeakReference[ChangePassword])
      extends CompoundButton.OnCheckedChangeListener() {
      def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) = for {
        dialog <- dialog.get
        initialUser <- dialog.user
        ok <- ok.get
      } {
        val context = buttonView.getContext
        val passwordEnabled = Futures.future { UserAdapter.isPasswordEnabled(context, initialUser) }
        // !(!ok.isEnabled && isChecked == passwordEnabled()) - skip initial toast when dialog appears 
        if (!(!ok.isEnabled && isChecked == passwordEnabled()))
          if (isChecked) {
            passwordField.setEnabled(true)
            Toast.makeText(context, XResource.getString(context, "enable_password_authentication").
              getOrElse("enable password authentication"), Toast.LENGTH_SHORT).show
          } else {
            passwordField.setEnabled(false)
            Toast.makeText(context, XResource.getString(context, "disable_password_authentication").
              getOrElse("disable password authentication"), Toast.LENGTH_SHORT).show
          }
        if (isChecked != passwordEnabled()) {
          if ((isChecked && passwordField.getText.toString.nonEmpty) || !isChecked)
            ok.setEnabled(true)
        } else {
          if (passwordField.getText.toString == initialUser.password)
            ok.setEnabled(false)
        }
      }
    }
  }
  class Enable extends ChangeState with Logging {
    protected val newState = true

    def tag = "dialog_user_enable"
    def title = Html.fromHtml(XResource.getString(getSherlockActivity, "user_enable_title").
      getOrElse("Enable user <b>%s</b>").format(user.map(_.name).getOrElse("unknown")))
    def message = Some(Html.fromHtml(XResource.getString(getSherlockActivity, "user_enable_message").
      getOrElse("Do you want to enable <b>%s</b> account?").format(user.map(_.name).getOrElse("unknown"))))
  }
  class Disable extends ChangeState with Logging {
    protected val newState = false

    def tag = "dialog_user_disable"
    def title = Html.fromHtml(XResource.getString(getSherlockActivity, "user_disable_title").
      getOrElse("Disable user <b>%s</b>").format(user.map(_.name).getOrElse("unknown")))
    def message = Some(Html.fromHtml(XResource.getString(getSherlockActivity, "user_disable_message").
      getOrElse("Do you want to disable <b>%s</b> account?").format(user.map(_.name).getOrElse("unknown"))))
  }
  abstract class ChangeState
    extends SSHDAlertDialog(AppComponent.Context.map(c => (XResource.getId(c, "ic_users", "drawable")))) {
    @volatile var user: Option[UserInfo] = None
    @volatile var onOkCallback: Option[UserInfo => Unit] = None
    protected val newState: Boolean
    override protected lazy val positive = Some((android.R.string.yes, new XDialog.ButtonListener(new WeakReference(ChangeState.this),
      Some((dialog: ChangeState) => user.foreach {
        oldUser =>
          val callback = dialog.onOkCallback
          val context = getSherlockActivity
          val newUser = oldUser.copy(enabled = newState)
          val notification = if (newUser.enabled)
            XResource.getString(context, "user_enabled_message").getOrElse("enabled user \"%s\"").format(newUser.name)
          else
            XResource.getString(context, "user_disabled_message").getOrElse("disabled user \"%s\"").format(newUser.name)
          Toast.makeText(context, notification, Toast.LENGTH_SHORT).show()
          defaultButtonCallback(dialog)
          Futures.future {
            IAmWarn(notification)
            UserAdapter.updateUser(Some(newUser), Some(oldUser))
            UserFragment.updateUser(Some(newUser), Some(oldUser))
            if (oldUser.name == "android")
              DefaultUser.updateUser(newUser)
            AnyBase.runOnUiThread { callback.foreach(_(newUser)) }
          }
      }))))
    override protected lazy val negative = Some((android.R.string.no, new XDialog.ButtonListener(new WeakReference(ChangeState.this),
      Some(defaultButtonCallback))))

    @Loggable
    override def onDestroyView() {
      super.onDestroyView
      user = None
      onOkCallback = None
    }
  }
  class Delete
    extends SSHDAlertDialog(AppComponent.Context.map(c => (XResource.getId(c, "ic_users", "drawable")))) {
    @volatile var user: Option[UserInfo] = None
    override protected lazy val positive = Some((android.R.string.ok, new XDialog.ButtonListener(new WeakReference(Delete.this),
      Some((dialog: Delete) => user.foreach {
        oldUser =>
          val context = getSherlockActivity
          Futures.future {
            UserAdapter.updateUser(None, Some(oldUser))
            UserFragment.updateUser(None, Some(oldUser))
          }
          val message = Html.fromHtml(XResource.getString(context, "user_deleted_message").
            getOrElse("deleted user <b>%s</b>").format(oldUser.name))
          IAmWarn(message.toString)
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
      }))))
    override protected lazy val negative = Some((android.R.string.cancel, new XDialog.ButtonListener(new WeakReference(Delete.this),
      Some(defaultButtonCallback))))

    def tag = "dialog_user_delete"
    def title = Html.fromHtml(XResource.getString(getSherlockActivity, "user_delete_title").
      getOrElse("Delete user <b>%s</b>").format(user.map(_.name).getOrElse("unknown")))
    def message = Some(Html.fromHtml(XResource.getString(getSherlockActivity, "user_delete_message").
      getOrElse("Do you want to delete <b>%s</b> account?").
      format(user.map(_.name).getOrElse("unknown"))))
    @Loggable
    override def onDestroyView() {
      super.onDestroyView
      user = None
    }
  }
  class SetGUID
    extends SSHDAlertDialog(AppComponent.Context.map(c => (XResource.getId(c, "ic_users", "drawable")))) {
    @volatile var user: Option[UserInfo] = None
    @volatile var userExt: Option[UserInfoExt] = None
    @volatile var onOkCallback: Option[(UserInfo, Option[Int], Option[Int]) => Any] = None
    override lazy val extContent = Option(getSherlockActivity.getLayoutInflater.inflate(R.layout.dialog_user_guid, null))
    override protected lazy val positive = Some((android.R.string.ok, new XDialog.ButtonListener(new WeakReference(SetGUID.this),
      Some((dialog: SetGUID) => {
        dialog.customView.get.foreach {
          customContent =>
            val uidSpinner = customContent.findViewById(R.id.dialog_user_guid_uid).asInstanceOf[Spinner]
            val uid = Option(uidSpinner.getSelectedItem.asInstanceOf[SetGUID.Item]).map(_.id)
            val gidSpinner = customContent.findViewById(R.id.dialog_user_guid_gid).asInstanceOf[Spinner]
            val gid = Option(gidSpinner.getSelectedItem.asInstanceOf[SetGUID.Item]).map(_.id)
            user.foreach {
              user =>
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
      }))))
    override protected lazy val negative = Some((android.R.string.cancel, new XDialog.ButtonListener(new WeakReference(SetGUID.this),
      Some(defaultButtonCallback))))

    def tag = "dialog_user_set_guid"
    def title = XResource.getString(getSherlockActivity, "user_set_guid_title").
      getOrElse("Set UID and GID").format(user.map(_.name).getOrElse("unknown"))
    def message = Some(Html.fromHtml(XResource.getString(getSherlockActivity, "user_set_guid_message").
      getOrElse("Please provide new UID (user id) and GID (group id) for <b>%s</b>. " +
        "Those values affect ssh session only in <b>SUPER USER</b> environment, <b>MULTIUSER</b> mode").
      format(user.map(_.name).getOrElse("unknown"))))
    @Loggable
    override def onResume() {
      for {
        customView <- customView.get
        userExt <- userExt
      } {
        val context = getSherlockActivity
        val pm = context.getPackageManager
        val packages = pm.getInstalledPackages(0)
        val ids = SetGUID.Item(-1, XResource.getString(context, "default_value").getOrElse("default")) +:
          packages.flatMap(p => Option(p.applicationInfo).map(ai => SetGUID.Item(ai.uid, p.packageName))).toList.sortBy(_.packageName)
        val uidSpinner = customView.findViewById(R.id.dialog_user_guid_uid).asInstanceOf[Spinner]
        val uidAdapter = new ArrayAdapter[SetGUID.Item](context, android.R.layout.simple_spinner_item, ids)
        uidAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        uidSpinner.setAdapter(uidAdapter)
        userExt.uid match {
          case Some(uid) => ids.indexWhere(_.id == uid) match {
            case index if index > 0 => uidSpinner.setSelection(index)
            case _ => uidSpinner.setSelection(0)
          }
          case None => uidSpinner.setSelection(0)
        }
        val gidSpinner = customView.findViewById(R.id.dialog_user_guid_gid).asInstanceOf[Spinner]
        val gidAdapter = new ArrayAdapter[SetGUID.Item](context, android.R.layout.simple_spinner_item, ids)
        gidAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        gidSpinner.setAdapter(gidAdapter)
        userExt.gid match {
          case Some(gid) => ids.indexWhere(_.id == gid) match {
            case index if index > 0 => gidSpinner.setSelection(index)
            case _ => gidSpinner.setSelection(0)
          }
          case None => gidSpinner.setSelection(0)
        }
      }
      super.onResume
    }
    @Loggable
    override def onDestroyView() {
      super.onDestroyView
      user = None
      userExt = None
      onOkCallback = None
    }
  }
  object SetGUID {
    private case class Item(id: Int, packageName: String) {
      override def toString = if (id >= 0) packageName + " - " + id else packageName
    }
  }
  class ShowDetails
    extends SSHDAlertDialog(Some(android.R.drawable.ic_menu_info_details)) {
    @volatile var user: Option[UserInfo] = None
    override lazy val extContent = ShowDetails.customContent
    override protected lazy val positive = Some((android.R.string.ok, new XDialog.ButtonListener(new WeakReference(ShowDetails.this),
      Some(defaultButtonCallback))))

    def tag = "dialog_user_details"
    def title = Html.fromHtml(XResource.getString(getSherlockActivity, "user_details_title").
      getOrElse("<b>%s</b>").format(user.map(_.name).getOrElse("unknown")))
    def message = None
    @Loggable
    override def onResume() {
      super.onResume
      for {
        customView <- customView.get
        user <- user
      } {
        val content = customView.asInstanceOf[ViewGroup].getChildAt(0).asInstanceOf[TextView]
        content.setText(Html.fromHtml(UserAdapter.getHtmlDetails(getSherlockActivity, user)))
      }
    }
    @Loggable
    override def onDestroyView() {
      super.onDestroyView
      user = None
    }
  }
  object ShowDetails {
    private lazy val customContent = AppComponent.Context.map {
      context =>
        val view = new ScrollView(context)
        val message = new TextView(context)
        view.addView(message, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        message.setMovementMethod(LinkMovementMethod.getInstance())
        message.setId(Int.MaxValue)
        view
    }
  }
  class SetHome
    extends SSHDAlertDialog(AppComponent.Context.map(c => (XResource.getId(c, "ic_users", "drawable")))) with LibFileChooser {
    @volatile var user: Option[UserInfo] = None
    def tag = "dialog_user_choosehome"
    def title = Html.fromHtml(XResource.getString(getDialogActivity, "user_choosehome_title").
      getOrElse("Select home directory for <b>%s</b>").format(user.map(_.name).getOrElse("unknown")))
    def message = Some(Html.fromHtml(XResource.getString(getDialogActivity,
      "user_choosehome_message").getOrElse("Choose home")))
    def initialPath() = user.map(user => UserAdapter.homeDirectory(getSherlockActivity, user))
    def setCallbackOnResult(arg: (File, Seq[File]) => Any) = callbackOnResult = arg
  }
  class KeyReplace
    extends SSHDAlertDialog(AppComponent.Context.map(c => (XResource.getId(c, "ic_users", "drawable")))) {
    @volatile var user: Option[UserInfo] = None
    @volatile var keyType: Option[String] = None
    override protected lazy val positive = Some((android.R.string.ok, new XDialog.ButtonListener(new WeakReference(KeyReplace.this),
      Some((dialog: KeyReplace) => { log.___gaze("AAAA") }))))
    override protected lazy val negative = Some((android.R.string.cancel, new XDialog.ButtonListener(new WeakReference(KeyReplace.this),
      Some(defaultButtonCallback))))

    def tag = "dialog_user_key_replace"
    def title = Html.fromHtml(XResource.getString(getSherlockActivity, "user_key_replace_title").
      getOrElse("Key already exists for user <b>%s</b>").format(user.map(_.name).getOrElse("unknown")))
    def message = Some(Html.fromHtml(XResource.getString(getSherlockActivity, "user_key_replace_message").
      getOrElse("%s key already exists. Do you want to replace it?").
      format(keyType.getOrElse("unknown").capitalize)))
    @Loggable
    override def onDestroyView() {
      super.onDestroyView
      user = None
      keyType = None
    }
  }
  class KeyType
    extends SSHDListDialog(AppComponent.Context.map(c => (XResource.getId(c, "ic_users", "drawable")))) {
    @volatile var user: Option[UserInfo] = None
    override protected lazy val positive = Some((R.string.RSA, new XDialog.ButtonListener(new WeakReference(KeyType.this),
      Some((dialog: KeyType) => {
        customView.get.map {
          case list: ListView =>
            val context = getSherlockActivity
            val length = list.getCheckedItemPosition match {
              case 0 => 4096
              case 1 => 2048
              case 2 => 1024
            }
            user.foreach(user => Futures.future { UserKeys.generateRSAKey(context, user, length) })
          case view =>
            log.fatal("unexpected view " + view)
        }
      }))))
    override protected lazy val neutral = Some((R.string.DSA, new XDialog.ButtonListener(new WeakReference(KeyType.this),
      Some((dialog: KeyType) => {
        val context = getSherlockActivity
        user.foreach(user => Futures.future { UserKeys.generateDSAKey(context, user) })
      }))))
    override protected lazy val negative = Some((android.R.string.cancel, new XDialog.ButtonListener(new WeakReference(KeyType.this),
      Some(defaultButtonCallback))))
    val defaultLengthIndex = 2
    val keyLength = Array[CharSequence]("4096 bits (only RSA)", "2048 bits (only RSA)", "1024 bits (RSA and DSA)")
    protected lazy val adapter = new ArrayAdapter[CharSequence](getSherlockActivity, android.R.layout.simple_list_item_single_choice, keyLength.toList)
    lazy val onClickListener = new AdapterView.OnItemClickListener {
      def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long) = KeyType.this.onItemClick(position)
    }

    def tag = "dialog_user_key_type"
    def title = user match {
      case Some(user) =>
        Html.fromHtml(XResource.getString(getSherlockActivity, "user_user_key_type_title").
          getOrElse("Select key type for <b>%s</b>").format(user.name))
      case None =>
        Html.fromHtml(XResource.getString(getSherlockActivity, "user_host_key_type_title").
          getOrElse("Select <b>host</b> key type"))
    }
    def message = None
    @Loggable
    override def onResume() {
      customView.get.map {
        case list: ListView =>
          log.debug("update list attributes")
          list.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE)
          list.setItemChecked(defaultLengthIndex, true)
          list.setOnItemClickListener(onClickListener)
        case view =>
          log.fatal("unexpected view " + view)
      }
      super.onResume
    }
    @Loggable
    override def onDestroyView() {
      super.onDestroyView
      user = None
    }
    @Loggable
    def onItemClick(n: Int) = neutralView.get.foreach {
      neutralButton =>
        neutralButton.setEnabled(n == defaultLengthIndex)
    }
  }
}

