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

import scala.collection.JavaConversions._

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.block.Level
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.dialog.Preferences
import org.digimead.digi.ctrl.lib.log.RichLogger
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.sshd.Message.dispatcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.preference.ListPreference
import android.preference.{ Preference => APreference }

class SSHDPreferences extends Preferences {
  implicit val logger = log
  @Loggable
  override protected def updatePrefSummary(p: APreference, key: String, notify: Boolean = false) {
    p match {
      case p: ListPreference if key == SSHDPreferences.DOption.ControlsHighlight.tag =>
        if (shared.contains(SSHDPreferences.DOption.ControlsHighlight.tag))
          // DOption.ControlsHighlight.tag exists
          SSHDPreferences.ControlsHighlight.set(p.getValue.toString, this, notify)(logger, dispatcher)
        else
          // DOption.ControlsHighlight.tag not exists
          SSHDPreferences.ControlsHighlight.set(SSHDPreferences.ControlsHighlight.get(this).toString, this, notify)(logger, dispatcher)
      case _ =>
        super.updatePrefSummary(p, key, notify)
    }
  }
}

object SSHDPreferences {
  @Loggable
  def initActivityPersistentOptions(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher) {
    Preferences.DebugLogLevel.set(context)(logger, dispatcher)
    Preferences.DebugAndroidLogger.set(context)(logger, dispatcher)
    Preferences.PreferredLayoutOrientation.set(context)(logger, dispatcher)
    Preferences.ShutdownTimeout.set(context)(logger, dispatcher)
    ControlsHighlight.set(context)(logger, dispatcher)
  }
  def initServicePersistentOptions(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher) {
    Preferences.DebugLogLevel.set(context)(logger, dispatcher)
    Preferences.DebugAndroidLogger.set(context)(logger, dispatcher)
  }
  object ControlsHighlight extends Preferences.StringPreference[String](DOption.ControlsHighlight, (s) => s,
    "set_experience_highlights_notify", "set experience highlights to \"%s\"") {
    override def set(value: String, context: Context, notify: Boolean = false)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = {
      value match {
        case "On" =>
          activate(context)
        case "Interactive" =>
        case "Off" =>
          deactivate(context)
      }
      super.set(value, context, notify)(logger, dispatcher)
    }
    def activate(context: Context) = {
      val publicEditor = Common.getPublicPreferences(context).edit
      publicEditor.putBoolean(DOption.ControlsHighlightActive.tag, true)
      publicEditor.commit
      Level.setEnable(true)
      Level.hlOn(context)
    }
    def deactivate(context: Context) {
      val publicEditor = Common.getPublicPreferences(context).edit
      publicEditor.putBoolean(DOption.ControlsHighlightActive.tag, false)
      publicEditor.commit
      Level.setEnable(false)
      Level.hlOff(context)
    }
  }
  object FilterConnection {
    protected def initialize(context: Context) {
      if (!context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE).contains(DOption.FilterConnectionInitialized.tag)) {
        val editor = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE).edit
        editor.putBoolean(DOption.FilterConnectionInitialized.tag, true)
        Allow.enable(context, "127.*.*.*", "192.168.*.*", "172.16.*.*", "172.17.*.*", "172.18.*.*",
          "172.18.*.*", "172.19.*.*", "172.20.*.*", "172.21.*.*", "172.22.*.*", "172.23.*.*",
          "172.24.*.*", "172.25.*.*", "172.26.*.*", "172.27.*.*", "172.28.*.*", "172.29.*.*",
          "172.30.*.*", "172.31.*.*", "172.32.*.*", "10.*.*.*", "169.254.*.*")
        editor.commit
      }
    }
    object Allow {
      val FilterConnectionAllow = getClass.getPackage.getName + "@namespace.filter.connection.allow"
      def get(context: Context): Seq[(String, Boolean)] = {
        initialize(context)
        context.getSharedPreferences(FilterConnectionAllow, Context.MODE_PRIVATE).getAll().toSeq.asInstanceOf[Seq[(String, Boolean)]]
      }
      def contains(context: Context, acl: String) =
        context.getSharedPreferences(FilterConnectionAllow, Context.MODE_PRIVATE).contains(acl)
      def enable(context: Context, acl: String*) {
        val editor = context.getSharedPreferences(FilterConnectionAllow, Context.MODE_PRIVATE).edit
        acl.foreach(acl => editor.putBoolean(acl, true))
        editor.commit
      }
      def disable(context: Context, acl: String*) {
        val editor = context.getSharedPreferences(FilterConnectionAllow, Context.MODE_PRIVATE).edit
        acl.foreach(acl => editor.putBoolean(acl, false))
        editor.commit
      }
      def remove(context: Context, acl: String*) {
        val editor = context.getSharedPreferences(FilterConnectionAllow, Context.MODE_PRIVATE).edit
        acl.foreach(acl => editor.remove(acl))
        editor.commit
      }
    }
    object Deny {
      val FilterConnectionDeny = getClass.getPackage.getName + "@namespace.filter.connection.deny"
      def get(context: Context): Seq[(String, Boolean)] = {
        initialize(context)
        context.getSharedPreferences(FilterConnectionDeny, Context.MODE_PRIVATE).getAll().toSeq.asInstanceOf[Seq[(String, Boolean)]]
      }
      def contains(context: Context, acl: String) =
        context.getSharedPreferences(FilterConnectionDeny, Context.MODE_PRIVATE).contains(acl)
      def enable(context: Context, acl: String*) {
        val editor = context.getSharedPreferences(FilterConnectionDeny, Context.MODE_PRIVATE).edit
        acl.foreach(acl => editor.putBoolean(acl, true))
        editor.commit
      }
      def disable(context: Context, acl: String*) {
        val editor = context.getSharedPreferences(FilterConnectionDeny, Context.MODE_PRIVATE).edit
        acl.foreach(acl => editor.putBoolean(acl, false))
        editor.commit
      }
      def remove(context: Context, acl: String*) {
        val editor = context.getSharedPreferences(FilterConnectionDeny, Context.MODE_PRIVATE).edit
        acl.foreach(acl => editor.remove(acl))
        editor.commit
      }
    }
  }
  object NetworkPort extends Preferences.Preference[Int, Int] {
    val option = DOption.NetworkPort
    def get(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Int =
      context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE).getInt(option.tag, default)
    def set(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit =
      set(get(context)(logger, dispatcher), context)(logger, dispatcher)
    def set(value: Int, context: Context, notify: Boolean = false)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = {
      assert(value >= 1 && value <= 65535)
      val editor = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE).edit
      editor.putInt(option.tag, value)
      editor.commit
      val message = Android.getString(context, "set_network_port_notify").getOrElse("set network port to %d").format(value)
      if (notify)
        SSHDCommon.optionChangedOnRestartNotify(context, option, value.toString)
      IAmMumble(message)(logger, dispatcher)
      context.sendBroadcast(new Intent(DIntent.UpdateOption, Uri.parse("code://" + context.getPackageName + "/" + option)))
    }
  }
  object AsRoot extends Preferences.Preference[Boolean, Boolean] {
    val option = org.digimead.digi.ctrl.lib.declaration.DOption.AsRoot
    def get(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Boolean =
      context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE).getBoolean(option.tag, default)
    def set(context: Context)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit =
      set(get(context)(logger, dispatcher), context)(logger, dispatcher)
    def set(value: Boolean, context: Context, notify: Boolean = false)(implicit logger: RichLogger, dispatcher: Dispatcher): Unit = {
      val editor = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE).edit
      editor.putBoolean(option.tag, value)
      editor.commit
      val message = if (value)
        Android.getString(context, "enable_as_root_notify").getOrElse("grant superuser permission").format(value)
      else
        Android.getString(context, "disable_as_root_notify").getOrElse("revoke superuser permission").format(value)
      if (notify)
        SSHDCommon.optionChangedOnRestartNotify(context, option, value.toString)
      IAmMumble(message)(logger, dispatcher)
      context.sendBroadcast(new Intent(DIntent.UpdateOption, Uri.parse("code://" + context.getPackageName + "/" + option)))
    }
  }
  object DOption extends DOption {
    val ControlsHighlight: OptVal = Value("experience_highlights", classOf[String], "On")
    val ControlsHighlightActive: OptVal = Value("experience_highlights_active", classOf[Boolean], true: java.lang.Boolean)
    val FilterConnectionInitialized: OptVal = Value("filter_connection_initialized", classOf[Boolean], false: java.lang.Boolean)
    val SelectedTab: OptVal = Value("selected_tab", classOf[Int], 0: java.lang.Integer)
    val NetworkPort: OptVal = Value("network_port", classOf[Int], 2222: java.lang.Integer)
  }
}
