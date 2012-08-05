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

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import scala.Option.option2Iterable
import scala.actors.Futures
import scala.collection.JavaConversions._
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.androidext.XDialog
import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.info.UserInfo
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.IAmWarn
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.ext.SSHDAlertDialog

import android.content.Context
import android.support.v4.app.Fragment
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
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
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
  lazy val changePassword = AppComponent.Context.map(context =>
    Fragment.instantiate(context.getApplicationContext, classOf[ChangePassword].getName, null).asInstanceOf[ChangePassword])
  lazy val enable = AppComponent.Context.map(context =>
    Fragment.instantiate(context.getApplicationContext, classOf[Enable].getName, null).asInstanceOf[Enable])
  lazy val disable = AppComponent.Context.map(context =>
    Fragment.instantiate(context.getApplicationContext, classOf[Disable].getName, null).asInstanceOf[Disable])
  lazy val delete = AppComponent.Context.map(context =>
    Fragment.instantiate(context.getApplicationContext, classOf[Delete].getName, null).asInstanceOf[Delete])
  lazy val setGUID = AppComponent.Context.map(context =>
    Fragment.instantiate(context.getApplicationContext, classOf[SetGUID].getName, null).asInstanceOf[SetGUID])
  lazy val showDetails = AppComponent.Context.map(context =>
    Fragment.instantiate(context.getApplicationContext, classOf[ShowDetails].getName, null).asInstanceOf[ShowDetails])

  class ChangePassword
    extends SSHDAlertDialog(AppComponent.Context.map(c => (XResource.getId(c, "ic_users", "drawable")))) {
    @volatile var user: Option[UserInfo] = None
    @volatile var onOkCallback: Option[UserInfo => Unit] = None
    private val (innerContent, enablePasswordButton, togglePasswordButton, passwordField) = {
      val customView = ChangePassword.customView()
      (customView.map(_._1), customView.map(_._2), customView.map(_._3), customView.map(_._4))
    }
    override val extContent = innerContent
    override protected lazy val positive = Some((android.R.string.yes, new XDialog.ButtonListener(new WeakReference(ChangePassword.this),
      Some((dialog: ChangePassword) => onPositiveButtonClick))))
    override protected lazy val negative = Some((android.R.string.no, new XDialog.ButtonListener(new WeakReference(ChangePassword.this),
      Some(defaultNegativeButtonCallback))))
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
      for {
        user <- user
        enablePasswordButton <- enablePasswordButton
        togglePasswordButton <- togglePasswordButton
        passwordField <- passwordField
        ok <- positiveView.get
      } {
        val context = getSherlockActivity
        val isUserPasswordEnabled = UserAdapter.isPasswordEnabled(context, user)
        enablePasswordButton.setChecked(isUserPasswordEnabled)
        passwordField.setEnabled(isUserPasswordEnabled)
        passwordField.setText(user.password)
        ok.setEnabled(false)
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
      user <- user
      callback <- onOkCallback
      passwordField <- passwordField
      enablePasswordButton <- enablePasswordButton
    } Futures.future {
      try {
        val context = getSherlockActivity
        val notification = XResource.getString(context, "users_change_password").getOrElse("password changed for user %1$s").format(user.name)
        IAmWarn(notification.format(user.name))
        val newUser = user.copy(password = passwordField.getText.toString)
        UserAdapter.save(context, newUser)
        UserAdapter.setPasswordEnabled(enablePasswordButton.isChecked, context, newUser)
        UserFragment.fragment.foreach {
          fragment =>
            if (fragment.lastActiveUserInfo.get.exists(_ == user))
              fragment.lastActiveUserInfo.set(Some(newUser))
            fragment.updateFieldsState()
        }
        AnyBase.runOnUiThread {
          UserAdapter.adapter.foreach {
            adapter =>
              val position = adapter.getPosition(user)
              if (position >= 0) {
                adapter.remove(user)
                adapter.insert(newUser, position)
              }
          }
          Toast.makeText(context, notification.format(user.name), Toast.LENGTH_SHORT).show()
        }
        callback(newUser)
      } catch {
        case e =>
          log.error(e.getMessage, e)
      } finally {
        onOkCallback = None
      }
    }
  }
  object ChangePassword {
    private val maxLengthFilter = new InputFilter.LengthFilter(5)

    def customView(): Option[(LinearLayout, CheckBox, ImageButton, EditText)] = AppComponent.AppContext.flatMap { context =>
      val view = new LinearLayout(context)
      view.setOrientation(LinearLayout.VERTICAL)
      view.setId(android.R.id.custom)
      val inner = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].
        inflate(R.layout.dialog_edittext, null).asInstanceOf[LinearLayout]
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
      view.addView(inner, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
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
        if (isChecked != UserAdapter.isPasswordEnabled(context, initialUser)) {
          if ((isChecked && passwordField.getText.toString.nonEmpty) || !isChecked)
            ok.setEnabled(true)
        } else {
          if (passwordField.getText.toString == initialUser.password)
            ok.setEnabled(false)
        }
        if (isChecked) {
          passwordField.setEnabled(true)
          Toast.makeText(context, XResource.getString(context, "enable_password_authentication").
            getOrElse("enable password authentication"), Toast.LENGTH_SHORT).show
        } else {
          passwordField.setEnabled(false)
          Toast.makeText(context, XResource.getString(context, "disable_password_authentication").
            getOrElse("disable password authentication"), Toast.LENGTH_SHORT).show
        }
      }
    }
  }
  class Enable extends ChangeState with Logging {
    protected val newState = true

    def tag = "dialog_user_enable"
    def title = Html.fromHtml(XResource.getString(getSherlockActivity, "users_enable_title").
      getOrElse("Enable user <b>%s</b>").format(user.map(_.name).getOrElse("unknown")))
    def message = Some(Html.fromHtml(XResource.getString(getSherlockActivity, "users_enable_message").
      getOrElse("Do you want to enable <b>%s</b> account?").format(user.map(_.name).getOrElse("unknown"))))
  }
  class Disable extends ChangeState with Logging {
    protected val newState = false

    def tag = "dialog_user_disable"
    def title = Html.fromHtml(XResource.getString(getSherlockActivity, "users_disable_title").
      getOrElse("Disable user <b>%s</b>").format(user.map(_.name).getOrElse("unknown")))
    def message = Some(Html.fromHtml(XResource.getString(getSherlockActivity, "users_disable_message").
      getOrElse("Do you want to disable <b>%s</b> account?").format(user.map(_.name).getOrElse("unknown"))))
  }
  abstract class ChangeState
    extends SSHDAlertDialog(AppComponent.Context.map(c => (XResource.getId(c, "ic_users", "drawable")))) {
    @volatile var user: Option[UserInfo] = None
    @volatile var onOkCallback: Option[UserInfo => Unit] = None
    protected val newState: Boolean
    override protected lazy val positive = Some((android.R.string.yes, new XDialog.ButtonListener(new WeakReference(ChangeState.this),
      Some((dialog: ChangeState) => user.foreach {
        user =>
          val context = getSherlockActivity
          val newUser = user.copy(enabled = newState)
          Futures.future { UserAdapter.save(context, newUser) }
          // update UserAdapter if any
          UserAdapter.adapter.foreach {
            adapter =>
              val position = adapter.getPosition(user)
              if (position >= 0) {
                adapter.remove(user)
                adapter.insert(newUser, position)
              }
          }
          // update UserFragment if any
          UserFragment.fragment.foreach(_.onDialogChangeState(user, newUser))
          // return result
          dialog.onOkCallback.foreach(_(newUser))
      }))))
    override protected lazy val negative = Some((android.R.string.no, new XDialog.ButtonListener(new WeakReference(ChangeState.this),
      Some(defaultNegativeButtonCallback))))

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
    @volatile var onOkCallback: Option[UserInfo => Any] = None
    override protected lazy val positive = Some((android.R.string.ok, new XDialog.ButtonListener(new WeakReference(Delete.this),
      Some((dialog: Delete) => user.foreach(user => dialog.onOkCallback.foreach(_(user)))))))
    override protected lazy val negative = Some((android.R.string.cancel, new XDialog.ButtonListener(new WeakReference(Delete.this),
      Some(defaultNegativeButtonCallback))))

    def tag = "dialog_user_delete"
    def title = Html.fromHtml(XResource.getString(getSherlockActivity, "users_delete_title").
      getOrElse("Delete user <b>%s</b>").format(user.map(_.name).getOrElse("unknown")))
    def message = Some(Html.fromHtml(XResource.getString(getSherlockActivity, "users_delete_message").
      getOrElse("Do you want to delete <b>%s</b> account?").
      format(user.map(_.name).getOrElse("unknown"))))
    @Loggable
    override def onDestroyView() {
      super.onDestroyView
      user = None
      onOkCallback = None
    }
  }
  class SetGUID
    extends SSHDAlertDialog(AppComponent.Context.map(c => (XResource.getId(c, "ic_users", "drawable"))),
      R.layout.dialog_users_guid) {
    @volatile var user: Option[UserInfo] = None
    @volatile var userExt: Option[UserInfoExt] = None
    @volatile var onOkCallback: Option[(UserInfo, Option[Int], Option[Int]) => Any] = None
    override protected lazy val positive = Some((android.R.string.ok, new XDialog.ButtonListener(new WeakReference(SetGUID.this),
      Some((dialog: SetGUID) => {
        dialog.contentView.get.foreach {
          customContent =>
            val uidSpinner = customContent.findViewById(R.id.dialog_users_guid_uid).asInstanceOf[Spinner]
            val uid = Option(uidSpinner.getSelectedItem.asInstanceOf[SetGUID.Item]).map(_.id)
            val gidSpinner = customContent.findViewById(R.id.dialog_users_guid_gid).asInstanceOf[Spinner]
            val gid = Option(gidSpinner.getSelectedItem.asInstanceOf[SetGUID.Item]).map(_.id)
            user.foreach(user => dialog.onOkCallback.foreach(_(user, uid, gid)))
        }
      }))))
    override protected lazy val negative = Some((android.R.string.cancel, new XDialog.ButtonListener(new WeakReference(SetGUID.this),
      Some(defaultNegativeButtonCallback))))

    def tag = "dialog_user_set_guid"
    def title = XResource.getString(getSherlockActivity, "users_set_guid_title").
      getOrElse("Set UID and GID").format(user.map(_.name).getOrElse("unknown"))
    def message = Some(Html.fromHtml(XResource.getString(getSherlockActivity, "users_set_guid_message").
      getOrElse("Please provide new UID (user id) and GID (group id) for <b>%s</b>. " +
        "Those values affect ssh session only in <b>SUPER USER</b> environment, <b>MULTIUSER</b> mode").
      format(user.map(_.name).getOrElse("unknown"))))
    @Loggable
    override def onResume() {
      for {
        contentView <- contentView.get
        userExt <- userExt
      } {
        val context = getSherlockActivity
        val pm = context.getPackageManager
        val packages = pm.getInstalledPackages(0)
        val ids = SetGUID.Item(-1, XResource.getString(context, "default_value").getOrElse("default")) +:
          packages.flatMap(p => Option(p.applicationInfo).map(ai => SetGUID.Item(ai.uid, p.packageName))).toList.sortBy(_.packageName)
        val uidSpinner = contentView.findViewById(R.id.dialog_users_guid_uid).asInstanceOf[Spinner]
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
        val gidSpinner = contentView.findViewById(R.id.dialog_users_guid_gid).asInstanceOf[Spinner]
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
    extends SSHDAlertDialog(AppComponent.Context.map(c => (XResource.getId(c, "ic_users", "drawable"))),
      ShowDetails.customContent) {
    @volatile var user: Option[UserInfo] = None
    override protected lazy val positive = Some((android.R.string.ok, new XDialog.ButtonListener(new WeakReference(ShowDetails.this),
      Some(defaultNegativeButtonCallback))))

    def tag = "dialog_user_details"
    def title = Html.fromHtml(XResource.getString(getSherlockActivity, "users_details_title").
      getOrElse("<b>%s</b> details").format(user.map(_.name).getOrElse("unknown")))
    def message = None
    @Loggable
    override def onResume() {
      super.onResume
      for {
        contentView <- contentView.get
        user <- user
      } {
        val content = contentView.asInstanceOf[ViewGroup].getChildAt(0).asInstanceOf[TextView]
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
}





