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

import java.util.Locale

import scala.actors.Actor
import scala.collection.immutable.HashMap
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.DActivity
import org.digimead.digi.ctrl.lib.androidext.SafeDialog
import org.digimead.digi.ctrl.lib.androidext.XAPI
import org.digimead.digi.ctrl.lib.androidext.XAndroid
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.log.AndroidLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.SyncVar
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.info.TabContent
import org.digimead.digi.ctrl.sshd.service.FilterAddFragment
import org.digimead.digi.ctrl.sshd.service.TabContent
import org.digimead.digi.ctrl.sshd.session.TabContent
import org.digimead.digi.ctrl.sshd.user.UserFragment

import com.actionbarsherlock.app.ActionBar
import com.actionbarsherlock.app.SherlockFragmentActivity

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

abstract class SSHDActivityBase extends SherlockFragmentActivity with DActivity {
  /** original registerReceiver */
  val origRegisterReceiver: (BroadcastReceiver, IntentFilter, String, Handler) => Intent = super.registerReceiver
  /** original unregisterReceiver */
  val origUnregisterReceiver: (BroadcastReceiver) => Unit = super.unregisterReceiver
}

/**
 * SSHDActivity = SSHDActivityBase + SSHDActivityState + SSHDActivityMenu
 */
class SSHDActivity extends SSHDActivityMenu {
  /** profiling support */
  private val ppLoading = SSHDActivity.ppGroup.start("SSHDActivity")
  /** org.digimead.digi.ctrl.lib.message dispatcher */
  implicit val dispatcher = org.digimead.digi.ctrl.sshd.Message.dispatcher
  lazy val resources = new SSHDResource(new WeakReference(this))
  ppLoading.stop()
  /**
   * @see android.support.v4.app.FragmentActivity#onCreate(android.os.Bundle)
   */
  override def onCreate(savedInstanceState: Bundle) = SSHDActivity.ppGroup("SSHDActivity.onCreate") {
    SSHDActivity.appActivity = Some(this)
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)
    val bar = getSupportActionBar()
    val barState = getLayoutInflater().inflate(R.layout.menubar_state, null)
    bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS)
    bar.setCustomView(barState, new ActionBar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
      ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    bar.setDisplayShowCustomEnabled(true)
    bar.setDisplayShowHomeEnabled(true)
    val display = getWindowManager.getDefaultDisplay()
    val variant = getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK match {
      case Configuration.SCREENLAYOUT_SIZE_XLARGE =>
        if (XAndroid.getScreenOrientation(display) == Configuration.ORIENTATION_LANDSCAPE) {
          log.debug("adjust to SIZE_XLARGE, layout Largest, landscape")
          SSHDActivity.appLayoutVariant = SSHDActivity.Layout.Largest
          bar.setDisplayShowTitleEnabled(true)
        } else {
          log.debug("adjust to SIZE_XLARGE, layout Normal")
          SSHDActivity.appLayoutVariant = SSHDActivity.Layout.Normal
          bar.setDisplayShowTitleEnabled(false)
        }
      case Configuration.SCREENLAYOUT_SIZE_LARGE =>
        if (XAndroid.getScreenOrientation(display) == Configuration.ORIENTATION_LANDSCAPE) {
          log.debug("adjust to SIZE_LARGE, layout Large, landscape")
          SSHDActivity.appLayoutVariant = SSHDActivity.Layout.Large
          bar.setDisplayShowTitleEnabled(true)
        } else {
          log.debug("adjust to SIZE_LARGE, layout Normal")
          SSHDActivity.appLayoutVariant = SSHDActivity.Layout.Normal
          bar.setDisplayShowTitleEnabled(false)
        }
      case _ =>
        if (XAndroid.getScreenOrientation(display) == Configuration.ORIENTATION_LANDSCAPE) {
          log.debug("adjust to SIZE_NORMAL and bellow, layout Small, landscape"); 1
          SSHDActivity.appLayoutVariant = SSHDActivity.Layout.Small
          bar.setDisplayShowTitleEnabled(true)
        } else {
          log.debug("adjust to SIZE_NORMAL and bellow, layout Small")
          SSHDActivity.appLayoutVariant = SSHDActivity.Layout.Small
          bar.setDisplayShowTitleEnabled(false)
        }
    }
    SSHDActivity.layoutAdjusted = false
    SSHDActivity.adjustHiddenLayout(this)
  }
  /**
   * @see android.support.v4.app.FragmentActivity#onResume()
   */
  @Loggable
  override def onResume() = SSHDActivity.ppGroup("SSHDActivity.onResume") {
    super.onResume()
    if (SSHDActivity.focused)
      SafeDialog.enable
  }
  /**
   * @see android.support.v4.app.FragmentActivity#onRestoreInstanceState()
   */
  @Loggable
  override def onRestoreInstanceState(outState: Bundle) = SSHDActivity.ppGroup("onRestoreInstanceState") {
    super.onRestoreInstanceState(outState)
    SSHDActivity.appInitialTab = outState.getInt("current_tab", 0)
  }
  /**
   * @see android.support.v4.app.FragmentActivity#onSaveInstanceState()
   */
  @Loggable
  override def onSaveInstanceState(outState: Bundle) = SSHDActivity.ppGroup("onSaveInstanceState") {
    super.onSaveInstanceState(outState)
    outState.putInt("current_tab", SSHDTabAdapter.getSelected)
  }
  /**
   * @see android.support.v4.app.FragmentActivity#onPause()
   */
  @Loggable
  override def onPause() = SSHDActivity.ppGroup("SSHDActivity.onPause") {
    SafeDialog.disable
    super.onPause()
  }
  /**
   * @see android.support.v4.app.FragmentActivity#onDestroy()
   */
  @Loggable
  override def onDestroy() = SSHDActivity.ppGroup("SSHDActivity.onDestroy") {
    SSHDActivity.appActivity = None
    super.onDestroy()
  }
  @Loggable
  override def onWindowFocusChanged(hasFocus: Boolean) = {
    super.onWindowFocusChanged(hasFocus)
    SSHDActivity.focused = hasFocus
    if (!SSHDActivity.layoutAdjusted && hasFocus)
      SSHDActivity.adjustVisibleLayout(this)
    if (AppComponent.Inner != null && SSHDActivityState.get == SSHDActivityState.State.Running)
      if (SSHDActivity.focused)
        SafeDialog.enable
      else
        SafeDialog.suspend
  }
  def onClickUsersGenerateNewUser(v: View) = UserFragment.onClickGenerateNewUser(v)
  def onClickUsersShowPassword(v: View) = UserFragment.onClickUsersShowPassword(v)
  def onClickUsersApply(v: View) = UserFragment.onClickApply(v)
  def onClickUsersToggleBlockAll(v: View) = UserFragment.onClickToggleBlockAll(v)
  def onClickUsersDeleteAll(v: View) = UserFragment.onClickDeleteAll(v)
  def onClickServiceFilterAddCustom(v: View) = FilterAddFragment.onClickCustom(v)
}

object SSHDActivity extends Logging {
  /** profiling support */
  val (ppGroup, ppLoading) = {
    val group = AnyBase.getStopWatchGroup("DigiSSHD")
    group.enabled = true
    group.enableOnDemand = true
    (group, group.start("SSHDActivity$"))
  }
  @volatile private var appActivity: Option[SSHDActivity] = None
  @volatile private var appInitialTab = 0
  @volatile private var appLayoutVariant = Layout.Normal
  @volatile private var focused = false
  @volatile private var layoutAdjusted = false
  lazy val locale = Locale.getDefault().getLanguage() + "_" + Locale.getDefault().getCountry()
  lazy val localeLanguage = Locale.getDefault().getLanguage()
  lazy val info = AppComponent.Inner.getCachedComponentInfo(locale, localeLanguage).get
  lazy val fragments: HashMap[String, () => Option[Fragment]] = AppComponent.Context.map {
    appContext =>
      val context = appContext.getApplicationContext
      HashMap[String, () => Option[Fragment]](
        classOf[org.digimead.digi.ctrl.sshd.service.TabContent].getName -> org.digimead.digi.ctrl.sshd.service.TabContent.accumulator,
        classOf[org.digimead.digi.ctrl.sshd.session.TabContent].getName -> org.digimead.digi.ctrl.sshd.session.TabContent.accumulator,
        classOf[org.digimead.digi.ctrl.sshd.info.TabContent].getName -> org.digimead.digi.ctrl.sshd.info.TabContent.accumulator)
  } getOrElse {
    log.fatal("lost application context")
    HashMap[String, () => Option[Fragment]]()
  }
  lazy val actor = {
    val actor = new Actor {
      def act = {
        loop {
          react {
            case _ =>
          }
        }
      }
    }
    actor.start
    actor
  }
  ppLoading.stop()
  // TODO REMOVE
  AnyBase.initializeDebug()
  Logging.addLogger(AndroidLogger)

  def activity = appActivity
  def initialTab = appInitialTab
  def layoutVariant = appLayoutVariant
  def adjustHiddenLayout(activity: SherlockFragmentActivity): Unit = Layout.synchronized {
    ppGroup("SSHDActivity.adjustHiddenLayout") {
      val display = activity.getWindowManager.getDefaultDisplay()
      val size = XAPI.getDisplaySize(display)
      layoutVariant match {
        case Layout.Large =>
          // adjust main_bottomPanel
          val main_bottomPanel = activity.findViewById(R.id.main_bottomPanel)
          val lp = main_bottomPanel.getLayoutParams
          lp.height = size.y / 3
          main_bottomPanel.setLayoutParams(lp)
          // adjust main_viewpager and main_extPanel
          val main_extPanel = activity.findViewById(R.id.main_extPanel)
          val main_extPanel_lp = main_extPanel.getLayoutParams.asInstanceOf[LinearLayout.LayoutParams]
          main_extPanel_lp.weight = 5
          main_extPanel.setLayoutParams(main_extPanel_lp)
          val main_viewpager = activity.findViewById(R.id.main_viewpager)
          val main_viewpager_lp = main_viewpager.getLayoutParams.asInstanceOf[LinearLayout.LayoutParams]
          main_viewpager_lp.weight = 4
          main_viewpager.setLayoutParams(main_viewpager_lp)
          // hide Grow/Shrink and jump to DigiControl
          activity.findViewById(R.id.main_buttonDigiControl).setVisibility(View.GONE)
          activity.findViewById(R.id.main_buttonGrowShrink).setVisibility(View.GONE)
          // hide small status
          activity.findViewById(R.id.main_textStatusSmallDevices).setVisibility(View.GONE)
        case Layout.Largest =>
          val main_bottomPanel = activity.findViewById(R.id.main_bottomPanel)
          val lp = main_bottomPanel.getLayoutParams
          lp.height = size.y / 4
          main_bottomPanel.setLayoutParams(lp)
          // hide Grow/Shrink and jump to DigiControl
          activity.findViewById(R.id.main_buttonDigiControl).setVisibility(View.GONE)
          activity.findViewById(R.id.main_buttonGrowShrink).setVisibility(View.GONE)
          // hide small status
          activity.findViewById(R.id.main_textStatusSmallDevices).setVisibility(View.GONE)
        case Layout.Normal =>
          activity.findViewById(R.id.main_extPanel).setVisibility(View.GONE)
          // hide Grow/Shrink and jump to DigiControl
          activity.findViewById(R.id.main_buttonDigiControl).setVisibility(View.GONE)
          activity.findViewById(R.id.main_buttonGrowShrink).setVisibility(View.GONE)
        case Layout.Small =>
          activity.findViewById(R.id.main_extPanel).setVisibility(View.GONE)
          if (activity.getSupportActionBar().isShowing) {
            activity.findViewById(R.id.main_buttonDigiControl).setVisibility(View.GONE)
            activity.findViewById(R.id.main_buttonGrowShrink).setVisibility(View.GONE)
          } else {
            activity.findViewById(R.id.main_buttonDigiControl).setVisibility(View.VISIBLE)
            activity.findViewById(R.id.main_buttonGrowShrink).setVisibility(View.VISIBLE)
          }
      }
    }
  }
  def adjustVisibleLayout(activity: SherlockFragmentActivity): Unit = Layout.synchronized {
    ppGroup("SSHDActivity.adjustVisibleLayout") {
      layoutAdjusted = true
      val alpha = 200
      val background = BitmapFactory.decodeResource(activity.getResources(), R.drawable.ic_background_logo_big)
      // set background for main_viewpager
      val main_viewpager = activity.findViewById(R.id.main_viewpager)
      val main_viewpager_bgsize = scala.math.min(main_viewpager.getWidth, main_viewpager.getHeight) - 10
      if (main_viewpager_bgsize > 0) {
        val bitmap = new BitmapDrawable(activity.getResources(), Bitmap.createScaledBitmap(background,
          main_viewpager_bgsize, main_viewpager_bgsize, true))
        bitmap.setAlpha(alpha)
        bitmap.setGravity(Gravity.CENTER)
        XAPI.setViewBackground(main_viewpager, bitmap)
      }
      if (layoutVariant == Layout.Normal || layoutVariant == Layout.Small) return
      // set background for main_topPanel
      val main_topPanel = activity.findViewById(R.id.main_topPanel)
      val main_topPanel_bgsize = scala.math.min(main_topPanel.getWidth, main_topPanel.getHeight) - 10
      if (main_topPanel_bgsize > 0) {
        val bitmap = new BitmapDrawable(activity.getResources(), Bitmap.createScaledBitmap(background,
          main_topPanel_bgsize, main_topPanel_bgsize, true))
        bitmap.setAlpha(alpha)
        bitmap.setGravity(Gravity.CENTER)
        XAPI.setViewBackground(main_topPanel, bitmap)
      }
      // set background for main_bottomPanel
      val main_bottomPanel = activity.findViewById(R.id.main_bottomPanel)
      val main_bottomPanel_bgsize = scala.math.min(main_bottomPanel.getWidth, main_bottomPanel.getHeight) - 10
      if (main_bottomPanel_bgsize > 0) {
        val bitmap = new BitmapDrawable(activity.getResources(), Bitmap.createScaledBitmap(background,
          main_bottomPanel_bgsize, main_bottomPanel_bgsize, true))
        bitmap.setAlpha(alpha)
        bitmap.setGravity(Gravity.CENTER)
        XAPI.setViewBackground(main_bottomPanel, bitmap)
      }
    }
  }
  @Loggable
  def stateInit(activity: SSHDActivity) = ppGroup("SSHDActivity.stateInit") {
    val bar = SyncVar[ActionBar]()
    AnyBase.runOnUiThread { bar.set(activity.getSupportActionBar()) }
    bar.get // initialize ActionBar
    SSHDTabAdapter.addTab(R.string.tab_name_service, classOf[org.digimead.digi.ctrl.sshd.service.TabContent], null)
    SSHDTabAdapter.addTab(R.string.tab_name_sessions, classOf[org.digimead.digi.ctrl.sshd.session.TabContent], null)
    SSHDTabAdapter.addTab(R.string.tab_name_information, classOf[org.digimead.digi.ctrl.sshd.info.TabContent], null)
  }
  @Loggable
  def stateCreate(activity: SSHDActivity) = ppGroup("SSHDActivity.stateCreate") {
    activity.onCreateExt(activity)
    if (SSHDActivityState.get == SSHDActivityState.State.Initializing)
      SSHDPreferences.initActivityPersistentOptions(activity)
    SSHDTabAdapter.onCreate(activity)
    AnyBase.runOnUiThread { activity.findViewById(R.id.loadingProgressBar).setVisibility(View.GONE) }
  }
  @Loggable
  def stateStart(activity: SSHDActivity) = ppGroup("SSHDActivity.stateStart") {
    activity.onStartExt(activity, activity.origRegisterReceiver)
  }
  @Loggable
  def stateResume(activity: SSHDActivity) = ppGroup("SSHDActivity.stateResume") {
    activity.onResumeExt(activity)
    if (SSHDActivity.focused)
      SafeDialog.enable
    else
      SafeDialog.suspend
  }
  @Loggable
  def statePause(activity: SSHDActivity) = ppGroup("SSHDActivity.statePause") {
    activity.onPauseExt(activity)
  }
  @Loggable
  def stateStop(activity: SSHDActivity) = ppGroup("SSHDActivity.stateStop") {
    activity.onStopExt(activity, false, activity.origUnregisterReceiver)
  }
  @Loggable
  def stateDestroy(activity: SSHDActivity) = ppGroup("SSHDActivity.stateDestroy") {
    AnyBase.runOnUiThread { activity.findViewById(R.id.loadingProgressBar).setVisibility(View.VISIBLE) }
    activity.onDestroyExt(activity)
  }
  object Layout extends Enumeration {
    val Small, Normal, Large, Largest = Value
  }
}
