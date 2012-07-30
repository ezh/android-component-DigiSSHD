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
import scala.actors.threadpool.AtomicInteger
import scala.collection.immutable.HashMap

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.DActivity
import org.digimead.digi.ctrl.lib.androidext.SafeDialog
import org.digimead.digi.ctrl.lib.androidext.XAndroid
import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.log.AndroidLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.info.TabContent
import org.digimead.digi.ctrl.sshd.service.TabContent
import org.digimead.digi.ctrl.sshd.session.TabContent

import com.actionbarsherlock.app.ActionBar
import com.actionbarsherlock.app.SherlockFragmentActivity
import com.actionbarsherlock.view.Menu
import com.actionbarsherlock.view.MenuInflater

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.Point
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

class SSHDActivity extends SherlockFragmentActivity with DActivity {
  /** profiling support */
  private val ppLoading = SSHDActivity.ppGroup.start("SSHDActivity")
  /** org.digimead.digi.ctrl.lib.message dispatcher */
  implicit val dispatcher = org.digimead.digi.ctrl.sshd.Message.dispatcher
  /** original registerReceiver */
  val origRegisterReceiver: (BroadcastReceiver, IntentFilter, String, Handler) => Intent = super.registerReceiver
  /** original unregisterReceiver */
  val origUnregisterReceiver: (BroadcastReceiver) => Unit = super.unregisterReceiver
  ppLoading.stop()

  /**
   * @see android.support.v4.app.FragmentActivity#onCreate(android.os.Bundle)
   */
  @Loggable
  override def onCreate(savedInstanceState: Bundle) = SSHDActivity.ppGroup("SSHDActivity.onCreate") {
    SSHDActivity.activity = Some(this)
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)
    val bar = getSupportActionBar()
    bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS)
    val barState = getLayoutInflater().inflate(R.layout.menubar_state, null)
    getSupportActionBar().setCustomView(barState)
    getSupportActionBar().setDisplayShowCustomEnabled(true)
    //bar.setDisplayShowTitleEnabled(false)
    //bar.setDisplayShowHomeEnabled(false)
    val display = getWindowManager.getDefaultDisplay()
    val variant = getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK match {
      case Configuration.SCREENLAYOUT_SIZE_XLARGE =>
        if (XAndroid.getScreenOrientation(display) == Configuration.ORIENTATION_LANDSCAPE) {
          log.debug("adjust to SIZE_XLARGE, layout Largest, landscape")
          SSHDActivity.layoutVariant = SSHDActivity.Layout.Largest
        } else {
          log.debug("adjust to SIZE_XLARGE, layout Normal")
          SSHDActivity.layoutVariant = SSHDActivity.Layout.Normal
        }
      case Configuration.SCREENLAYOUT_SIZE_LARGE =>
        if (XAndroid.getScreenOrientation(display) == Configuration.ORIENTATION_LANDSCAPE) {
          log.debug("adjust to SIZE_LARGE, layout Large, landscape")
          SSHDActivity.layoutVariant = SSHDActivity.Layout.Large
        } else {
          log.debug("adjust to SIZE_LARGE, layout Normal")
          SSHDActivity.layoutVariant = SSHDActivity.Layout.Normal
        }
      case _ =>
        if (XAndroid.getScreenOrientation(display) == Configuration.ORIENTATION_LANDSCAPE) {
          log.debug("adjust to SIZE_NORMAL and bellow, layout Small, landscape"); 1
          SSHDActivity.layoutVariant = SSHDActivity.Layout.Small
        } else {
          log.debug("adjust to SIZE_NORMAL and bellow, layout Small")
          SSHDActivity.layoutVariant = SSHDActivity.Layout.Small
        }
    }
    SSHDActivity.layoutAdjusted = false
    SSHDActivity.adjustHiddenLayout(this)

    SSHDActivity.State.actor ! SSHDActivity.State.Event.OnCreate(this, savedInstanceState)
  }
  /**
   * @see android.support.v4.app.FragmentActivity#onStart()
   */
  @Loggable
  override def onStart() = SSHDActivity.ppGroup("SSHDActivity.onStart") {
    super.onStart()
    SSHDActivity.State.actor ! SSHDActivity.State.Event.OnStart(this)
  }
  /**
   * @see android.support.v4.app.FragmentActivity#onResume()
   */
  @Loggable
  override def onResume() = SSHDActivity.ppGroup("SSHDActivity.onResume") {
    super.onResume()
    if (SSHDActivity.focused)
      SafeDialog.enable
    SSHDActivity.State.actor ! SSHDActivity.State.Event.OnResume(this)
  }
  /**
   * @see android.support.v4.app.FragmentActivity#onSaveInstanceState()
   */
  @Loggable
  override def onSaveInstanceState(outState: Bundle) = SSHDActivity.ppGroup("onSaveInstanceState") {
    SSHDActivity.State.actor ! SSHDActivity.State.Event.OnSaveInstanceState(this, outState)
    super.onSaveInstanceState(outState)
  }
  /**
   * @see android.support.v4.app.FragmentActivity#onPause()
   */
  @Loggable
  override def onPause() = SSHDActivity.ppGroup("SSHDActivity.onPause") {
    SSHDActivity.State.actor ! SSHDActivity.State.Event.OnPause(this)
    super.onPause()
  }
  /**
   * @see android.support.v4.app.FragmentActivity#onStop()
   */
  @Loggable
  override def onStop() = SSHDActivity.ppGroup("SSHDActivity.onStop") {
    SSHDActivity.State.actor ! SSHDActivity.State.Event.OnStop(this)
    super.onStop()
  }
  /**
   * @see android.support.v4.app.FragmentActivity#onDestroy()
   */
  @Loggable
  override def onDestroy() = SSHDActivity.ppGroup("SSHDActivity.onDestroy") {
    SSHDActivity.State.actor ! SSHDActivity.State.Event.OnDestroy(this)
    super.onDestroy()
    SSHDActivity.activity = None
  }
  @Loggable
  override def onWindowFocusChanged(hasFocus: Boolean) = {
    super.onWindowFocusChanged(hasFocus)
    SSHDActivity.focused = hasFocus
    if (!SSHDActivity.layoutAdjusted && hasFocus)
      SSHDActivity.adjustVisibleLayout(this)
    if (AppComponent.Inner != null && SSHDActivity.State.get == SSHDActivity.State.Running)
      if (SSHDActivity.focused)
        SafeDialog.enable
      else
        SafeDialog.suspend
  }
  @Loggable
  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    super.onCreateOptionsMenu(menu)
    val inflater = getSupportMenuInflater()
    inflater.inflate(R.menu.menu, menu)
    true
    //import com.actionbarsherlock.view.MenuItem
    //
    //menu.add(0, 1, 1, android.R.string.cancel).setIcon(android.R.drawable.ic_menu_camera).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    //menu.add(0, 2, 2, android.R.string.cut).setIcon(android.R.drawable.ic_menu_agenda).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    //menu.add(0, 3, 3, android.R.string.paste).setIcon(android.R.drawable.ic_menu_compass).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    //menu.add(0, 4, 4, android.R.string.search_go).setIcon(android.R.drawable.ic_menu_upload).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
  }
}

object SSHDActivity extends Logging {
  /** profiling support */
  val (ppGroup, ppLoading) = {
    val group = AnyBase.getStopWatchGroup("DigiSSHD")
    group.enabled = true
    group.enableOnDemand = true
    (group, group.start("SSHDActivity$"))
  }
  @volatile private[sshd] var activity: Option[SSHDActivity] = None
  @volatile private var focused = false
  @volatile private var layoutVariant = Layout.Normal
  @volatile private var layoutAdjusted = false
  lazy val locale = Locale.getDefault().getLanguage() + "_" + Locale.getDefault().getCountry()
  lazy val localeLanguage = Locale.getDefault().getLanguage()
  lazy val info = AppComponent.Inner.getCachedComponentInfo(locale, localeLanguage).get
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
  ppLoading.stop()
  // TODO REMOVE
  AnyBase.initializeDebug()
  Logging.addLogger(AndroidLogger)

  def getLayoutVariant() = layoutVariant
  def adjustHiddenLayout(activity: SherlockFragmentActivity): Unit = Layout.synchronized {
    ppGroup("SSHDActivity.adjustHiddenLayout") {
      val size = new Point
      val display = activity.getWindowManager.getDefaultDisplay()
      try {
        val newGetSize = display.getClass.getMethod("getSize", Array[Class[_]](size.getClass): _*)
        newGetSize.invoke(display, size)
      } catch {
        case e: NoSuchMethodException =>
          size.x = display.getWidth
          size.y = display.getHeight
      }
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
        main_viewpager.setBackgroundDrawable(bitmap)
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
        main_topPanel.setBackgroundDrawable(bitmap)
      }
      // set background for main_bottomPanel
      val main_bottomPanel = activity.findViewById(R.id.main_bottomPanel)
      val main_bottomPanel_bgsize = scala.math.min(main_bottomPanel.getWidth, main_bottomPanel.getHeight) - 10
      if (main_bottomPanel_bgsize > 0) {
        val bitmap = new BitmapDrawable(activity.getResources(), Bitmap.createScaledBitmap(background,
          main_bottomPanel_bgsize, main_bottomPanel_bgsize, true))
        bitmap.setAlpha(alpha)
        bitmap.setGravity(Gravity.CENTER)
        main_bottomPanel.setBackgroundDrawable(bitmap)
      }
    }
  }
  /**
   * called only once, at Initializing state
   */
  @Loggable
  def onInit(activity: SSHDActivity) = ppGroup("SSHDActivity.onInit") {
    val bar = activity.getSupportActionBar()
    SSHDTabAdapter.addTab(R.string.tab_name_service, classOf[org.digimead.digi.ctrl.sshd.service.TabContent], null)
    SSHDTabAdapter.addTab(R.string.tab_name_sessions, classOf[org.digimead.digi.ctrl.sshd.session.TabContent], null)
    SSHDTabAdapter.addTab(R.string.tab_name_information, classOf[org.digimead.digi.ctrl.sshd.info.TabContent], null)
  }
  /**
   * called after every Suspended state
   */
  @Loggable
  def onCreate(activity: SSHDActivity) = ppGroup("SSHDActivity.onCreate") {
    activity.onCreateExt(activity)
    if (State.get == State.Initializing)
      SSHDPreferences.initActivityPersistentOptions(activity)
    SSHDTabAdapter.onCreate(activity)
  }

  /**
   * FSA android life cycle state trait
   */
  sealed abstract class State {
    def m: Map[Int, State]
    def event(e: State.Event): Option[State] = m.get(e.id)
  }
  object State {
    @volatile private var state: SSHDActivity.State = SSHDActivity.State.Initializing
    private[SSHDActivity] lazy val actor = {
      val actor = new Actor {
        /** FSA */
        def act = {
          loop {
            react {
              case event: Event =>
                log.debug("start transition %s(event %s)".format(state, event))
                ppGroup("SSHDActivity$." + state + "<-" + event) { state.event(event) } match {
                  case Some(newState) =>
                    log.debug("complete transition %s(event %s) -> %s".format(state, event, newState))
                    state = newState
                  case None =>
                    log.error("failed transition from %s(event %s)".format(state, event))
                }
            }
          }
        }
      }
      actor.start
      actor
    }

    def get() = State.state
    /**
     * FSA android life cycle event trait
     */
    sealed abstract class Event(val id: Int)
    /**
     * It is very important to pass an activity instance with event.
     * All communication are mostly independent, so activity may be invalidated while event processing
     */
    object Event {
      private val eventID = new AtomicInteger(0)

      val OnCreateID = eventID.incrementAndGet
      case class OnCreate(val activity: SSHDActivity, savedInstanceState: Bundle) extends Event(OnCreateID) {
        override def toString = "onCreate"
      }

      val OnStartID = eventID.incrementAndGet
      case class OnStart(val activity: SSHDActivity) extends Event(OnStartID) {
        override def toString = "onStart"
      }

      val OnResumeID = eventID.incrementAndGet
      case class OnResume(val activity: SSHDActivity) extends Event(OnResumeID) {
        override def toString = "onResume"
      }

      val OnSaveInstanceStateID = eventID.incrementAndGet
      case class OnSaveInstanceState(val activity: SSHDActivity, outState: Bundle) extends Event(OnSaveInstanceStateID) {
        override def toString = "onSaveInstanceState"
      }

      val OnPauseID = eventID.incrementAndGet
      case class OnPause(val activity: SSHDActivity) extends Event(OnPauseID) {
        override def toString = "onPause"
      }

      val OnStopID = eventID.incrementAndGet
      case class OnStop(val activity: SSHDActivity) extends Event(OnStopID) {
        override def toString = "onStop"
      }

      val OnDestroyID = eventID.incrementAndGet
      case class OnDestroy(val activity: SSHDActivity) extends Event(OnDestroyID) {
        override def toString = "onDestroy"
      }
    }
    object Initializing extends State {
      lazy val m = Map[Int, State](
        Event.OnCreateID -> State.OnCreate)
      override def toString = "State.Initializing"
      override def event(e: Event) = e match {
        case Event.OnCreate(activity, savedInstanceState) =>
          Thread.sleep(1000) // wait before load 
          onInit(activity)
          onCreate(activity)
          AnyBase.runOnUiThread { activity.findViewById(R.id.loadingProgressBar).setVisibility(View.GONE) }
          super.event(e)
        case event =>
          log.fatal("illegal event " + event)
          None
      }
    }
    object Suspended extends State {
      lazy val m = Map[Int, State](
        Event.OnCreateID -> State.OnCreate)
      override def toString = "State.Suspended"
      override def event(e: Event) = e match {
        case Event.OnCreate(activity, savedInstanceState) =>
          onCreate(activity)
          AnyBase.runOnUiThread { activity.findViewById(R.id.loadingProgressBar).setVisibility(View.GONE) }
          super.event(e)
        case event =>
          log.fatal("illegal event " + event)
          None
      }
    }
    object OnCreate extends State {
      override def toString = "State.OnCreate"
      lazy val m = Map[Int, State](
        Event.OnStartID -> State.OnStart)
      override def event(e: Event) = e match {
        case Event.OnStart(activity) =>
          activity.onStartExt(activity, activity.origRegisterReceiver)
          super.event(e)
        case event =>
          log.fatal("illegal event " + event)
          None
      }
    }
    object OnStart extends State {
      override def toString = "State.OnStart"
      lazy val m = Map[Int, State](
        Event.OnResumeID -> State.Running)
      override def event(e: Event) =
        e match {
          case Event.OnResume(activity) =>
            activity.onResumeExt(activity)
            if (SSHDActivity.focused)
              SafeDialog.enable
            else
              SafeDialog.suspend
            super.event(e)
          case event =>
            log.fatal("illegal event " + event)
            None
        }
    }
    object Running extends State {
      override def toString = "State.Running"
      lazy val m = Map[Int, State](
        Event.OnSaveInstanceStateID -> State.OnSaveInstanceState,
        Event.OnPauseID -> State.OnPause,
        Event.OnStopID -> State.OnStop,
        Event.OnDestroyID -> State.Suspended)
      override def event(e: Event) = e match {
        case Event.OnSaveInstanceState(activity, outState) =>
          super.event(e)
        case Event.OnPause(activity) =>
          activity.onPauseExt(activity)
          super.event(e)
        case Event.OnStop(activity) =>
          activity.onPauseExt(activity)
          activity.onStopExt(activity, false, activity.origUnregisterReceiver)
          super.event(e)
        case Event.OnDestroy(activity) =>
          activity.onPauseExt(activity)
          activity.onStopExt(activity, false, activity.origUnregisterReceiver)
          activity.onDestroyExt(activity)
          super.event(e)
        case event =>
          log.fatal("illegal event " + event)
          None
      }
    }
    object OnSaveInstanceState extends State {
      override def toString = "State.OnSaveInstanceState"
      lazy val m = Map[Int, State](
        Event.OnPauseID -> State.OnPause,
        Event.OnStopID -> State.OnStop,
        Event.OnDestroyID -> State.Suspended)
      override def event(e: Event) = e match {
        case Event.OnPause(activity) =>
          activity.onPauseExt(activity)
          super.event(e)
        case Event.OnStop(activity) =>
          activity.onPauseExt(activity)
          activity.onStopExt(activity, false, activity.origUnregisterReceiver)
          super.event(e)
        case Event.OnDestroy(activity) =>
          activity.onPauseExt(activity)
          activity.onStopExt(activity, false, activity.origUnregisterReceiver)
          activity.onDestroyExt(activity)
          super.event(e)
        case event =>
          log.fatal("illegal event " + event)
          None
      }
    }
    object OnPause extends State {
      override def toString = "State.OnPause"
      lazy val m = Map[Int, State](
        Event.OnStopID -> State.OnStop,
        Event.OnDestroyID -> State.Suspended,
        /*
         * fucking android, OnSaveInstanceState AFTER OnPause! LOL. WTF with documentation about lifecycle?
         */
        Event.OnSaveInstanceStateID -> State.OnPause)
      override def event(e: Event) = e match {
        case Event.OnSaveInstanceState(activity, outState) =>
          log.warn("another bug in 4.x, OnSaveInstanceState after OnPause")
          super.event(e)
        case Event.OnStop(activity) =>
          activity.onStopExt(activity, false, activity.origUnregisterReceiver)
          super.event(e)
        case Event.OnDestroy(activity) =>
          activity.onStopExt(activity, false, activity.origUnregisterReceiver)
          activity.onDestroyExt(activity)
          super.event(e)
        case event =>
          log.fatal("illegal event " + event)
          None
      }
    }
    object OnStop extends State {
      override def toString = "State.OnStop"
      lazy val m = Map[Int, State](
        Event.OnStartID -> State.OnStart,
        Event.OnDestroyID -> State.Suspended)
      override def event(e: Event) = e match {
        case Event.OnStart(activity) =>
          activity.onStartExt(activity, activity.origRegisterReceiver)
          super.event(e)
        case Event.OnDestroy(activity) =>
          AnyBase.runOnUiThread { activity.findViewById(R.id.loadingProgressBar).setVisibility(View.VISIBLE) }
          activity.onDestroyExt(activity)
          super.event(e)
        case event =>
          log.fatal("illegal event " + event)
          None
      }
    }
  }
  object Layout extends Enumeration {
    val Small, Normal, Large, Largest = Value
  }
}
