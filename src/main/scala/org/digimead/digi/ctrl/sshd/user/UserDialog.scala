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

import scala.Option.option2Iterable
import scala.actors.Futures
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.androidext.Util
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.info.UserInfo
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.IAmWarn
import org.digimead.digi.ctrl.lib.util.Passwords
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.ext.SherlockSafeDialogFragment

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.Fragment
import android.text.Editable
import android.text.Html
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast

object UserDialog extends Logging with Passwords {
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
        if (!(numbers ++ alphabet ++ """_-""").exists(_ == source.charAt(i)))
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
        if (!defaultPasswordCharacters.exists(_ == source.charAt(i)))
          return ""
      return null
    }
  }
  lazy val changePassword = AppComponent.Context.map(context =>
    Fragment.instantiate(context.getApplicationContext, classOf[ChangePassword].getName, null).asInstanceOf[ChangePassword])

  class ChangePassword extends SherlockSafeDialogFragment with Logging {
    @volatile private var dirtyHackForDirtyFramework = false
    @volatile private var onUserUpdateCallback: Option[(ChangePassword, UserInfo) => Any] = None
    @volatile private var cachedDialog: Option[AlertDialog] = None
    @volatile private var initialUser: Option[UserInfo] = None

    override def toString = "dialog_user_changepassword"
    @Loggable
    override def onCreate(savedInstanceState: Bundle) = {
      super.onCreate(savedInstanceState)
    }
    @Loggable
    override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
      if (dirtyHackForDirtyFramework && inflater != null) {
        log.warn("workaround for \"requestFeature() must be called before adding content\"")
        dirtyHackForDirtyFramework = false
        return super.onCreateView(inflater, container, savedInstanceState)
      } else if (inflater == null)
        dirtyHackForDirtyFramework = true
      if (cachedDialog.nonEmpty)
        return null
      val context = getSherlockActivity
      initialUser = UserAdapter.find(context, getArguments.getString("username"))
      val view = for { user <- initialUser } yield ChangePassword.customView match {
        case Some((view, enablePasswordButton, togglePasswordButton, passwordField)) =>
          val isUserPasswordEnabled = UserAdapter.isPasswordEnabled(context, user)
          enablePasswordButton.setChecked(isUserPasswordEnabled)
          togglePasswordButton.setEnabled(isUserPasswordEnabled)
          passwordField.setEnabled(isUserPasswordEnabled)
          passwordField.setText(user.password)
          view
        case _ =>
          log.fatal("unable to get ChangePassword content view")
          super.onCreateView(inflater, container, savedInstanceState)
      }
      view getOrElse {
        log.fatal("unable to get ChangePassword content view")
        super.onCreateView(inflater, container, savedInstanceState)
      }
    }
    @Loggable
    override def onCreateDialog(savedInstanceState: Bundle): Dialog = cachedDialog match {
      case Some(dialog) =>
        val context = getSherlockActivity
        initialUser = UserAdapter.find(context, getArguments.getString("username"))
        initialUser match {
          case Some(user) =>
            dialog.setTitle(Util.getString(context, "dialog_password_title").getOrElse("'%s' user password").format(user.name))
            dialog.show
            dialog
          case None =>
            log.fatal("unable to get ChangePassword custom dialog, user %s not found".format(getArguments.getString("username")))
            super.onCreateDialog(savedInstanceState)
        }
      case None =>
        val context = getSherlockActivity
        initialUser = UserAdapter.find(context, getArguments.getString("username"))
        val dialog = for { user <- initialUser } yield ChangePassword.customView match {
          case Some((view, enablePasswordButton, togglePasswordButton, passwordField)) =>
            val dialog = new AlertDialog.Builder(context).
              setTitle(Util.getString(context, "dialog_password_title").getOrElse("'%s' user password").format(user.name)).
              setMessage(Html.fromHtml(Util.getString(context, "dialog_password_message").
                getOrElse("Please select a password. User password must be at least 1 characters long. Password cannot be more than 16 characters. Only standard unix password characters are allowed."))).
              setView(onCreateView(null, null, null)).
              setPositiveButton(android.R.string.ok, ChangePassword.PositiveButtonListener).
              setNegativeButton(android.R.string.cancel, ChangePassword.NegativeButtonListener).
              setIcon(android.R.drawable.ic_dialog_info).
              create()
            /*
             * ABSOLUTELY CRAZY BEHAVIOR, emulator, API 10
             * without dialog.show most of the time (sometimes, rarely not)
             * 
             * android.util.AndroidRuntimeException: requestFeature() must be called before adding content
             * at com.android.internal.policy.impl.PhoneWindow.requestFeature(PhoneWindow.java:181)
             * at com.android.internal.app.AlertController.installContent(AlertController.java:199)
             * at android.app.AlertDialog.onCreate(AlertDialog.java:251)
             * at android.app.Dialog.dispatchOnCreate(Dialog.java:307)
             * at android.app.Dialog.show(Dialog.java:225)
             * at android.support.v4.app.DialogFragment.onStart(DialogFragment.java:385)
             */
            dialog.show
            val ok = dialog.findViewById(android.R.id.button1)
            ok.setEnabled(false)
            passwordField.addTextChangedListener(new ChangePassword.PasswordFieldTextChangedListener(ok, new WeakReference(this)))
            togglePasswordButton.setOnClickListener(new ChangePassword.TogglePasswordButtonOnClickListener(passwordField))
            enablePasswordButton.setOnCheckedChangeListener(new ChangePassword.EnablePasswordButtonOnCheckedChangeListener(ok,
              togglePasswordButton, passwordField, new WeakReference(this)))
            cachedDialog = Some(dialog)
            dialog
          case None =>
            log.fatal("unable to get ChangePassword custom dialog")
            super.onCreateDialog(savedInstanceState)
        }
        dialog getOrElse {
          log.fatal("unable to get ChangePassword custom dialog")
          super.onCreateDialog(savedInstanceState)
        }
    }
    def setOnUserUpdateListener(callback: Option[(ChangePassword, UserInfo) => Any]) = {
      onUserUpdateCallback = callback
    }
    override def onDismiss(dialog: DialogInterface) {
      super.onDismiss(dialog)
      initialUser = None
    }
  }
  object ChangePassword {
    private lazy val customView = AppComponent.Context.flatMap { context =>
      val view = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].
        inflate(R.layout.dialog_edittext, null).asInstanceOf[LinearLayout]
      val enablePasswordButton = new CheckBox(context)
      enablePasswordButton.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
      view.addView(enablePasswordButton, 0)
      val togglePasswordButton = new ImageButton(context)
      togglePasswordButton.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
      togglePasswordButton.setImageResource(R.drawable.btn_eye)
      togglePasswordButton.setBackgroundResource(_root_.android.R.color.transparent)
      view.addView(togglePasswordButton)
      val passwordField = view.findViewById(_root_.android.R.id.edit).asInstanceOf[EditText]
      passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
      passwordField.setFilters(Array(userPasswordFilter))
      Some(view, enablePasswordButton, togglePasswordButton, passwordField)
    }
    private val maxLengthFilter = new InputFilter.LengthFilter(5)
    object PositiveButtonListener extends DialogInterface.OnClickListener {
      def onClick(dialog: DialogInterface, whichButton: Int) = Futures.future {
        dialog match {
          case alertDialog: AlertDialog =>
            val context = alertDialog.getContext
            val positiveButtonListenerResult = for {
              dialog <- changePassword
              user <- UserAdapter.find(context, dialog.getArguments.getString("username"))
              (view, enablePasswordButton, togglePasswordButton, passwordField) <- customView
            } yield try {
              val notification = Util.getString(context, "users_change_password").getOrElse("password changed for user %1$s").format(user.name)
              IAmWarn(notification.format(user.name))
              AnyBase.runOnUiThread { Toast.makeText(context, notification.format(user.name), Toast.LENGTH_SHORT).show() }
              val newUser = user.copy(password = passwordField.getText.toString)
              UserAdapter.save(context, newUser)
              UserAdapter.setPasswordEnabled(enablePasswordButton.isChecked, context, newUser)
              UserAdapter.adapter.foreach {
                adapter =>
                  val position = adapter.getPosition(user)
                  if (position >= 0) {
                    adapter.remove(user)
                    adapter.insert(newUser, position)
                  }
              }
              dialog.onUserUpdateCallback.foreach(_(dialog, newUser))
            } catch {
              case e =>
                log.error(e.getMessage, e)
            } finally {
              dialog.onUserUpdateCallback = None
            }
            positiveButtonListenerResult getOrElse {
              log.fatal("unable to process positive button callback - invalid environment " + customView)
            }
          case dialog =>
            log.fatal("unable to process positive button callback for invalid dialog " + dialog)
        }
      }
    }
    object NegativeButtonListener extends DialogInterface.OnClickListener {
      def onClick(dialog: DialogInterface, whichButton: Int) =
        changePassword.foreach(_.onUserUpdateCallback = None)
    }
    class PasswordFieldTextChangedListener(ok: View, dialog: WeakReference[ChangePassword]) extends TextWatcher {
      override def afterTextChanged(s: Editable) = for {
        dialog <- dialog.get
        initialUser <- dialog.initialUser
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
    class EnablePasswordButtonOnCheckedChangeListener(ok: View, togglePasswordButton: ImageButton,
      passwordField: EditText, dialog: WeakReference[ChangePassword])
      extends CompoundButton.OnCheckedChangeListener() {
      def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) = for {
        dialog <- dialog.get
        initialUser <- dialog.initialUser
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
          togglePasswordButton.setEnabled(true)
          passwordField.setEnabled(true)
          Toast.makeText(context, Util.getString(context, "enable_password_authentication").
            getOrElse("enable password authentication"), Toast.LENGTH_SHORT).show
        } else {
          togglePasswordButton.setEnabled(false)
          passwordField.setEnabled(false)
          Toast.makeText(context, Util.getString(context, "disable_password_authentication").
            getOrElse("disable password authentication"), Toast.LENGTH_SHORT).show
        }
      }
    }
  }
}
