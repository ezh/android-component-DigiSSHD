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

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.DActivity
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.log.AndroidLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.info.TabActivity

import com.actionbarsherlock.app.ActionBar
import com.actionbarsherlock.app.SherlockFragmentActivity

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.support.v4.view.ViewPager

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
    bar.setDisplayShowTitleEnabled(false)
    bar.setDisplayShowHomeEnabled(false)
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
  ppLoading.stop()
  Logging.addLogger(AndroidLogger)

  /**
   * called only once, at Initializing state
   */
  @Loggable
  def onInit(activity: SSHDActivity) = ppGroup("SSHDActivity.onInit") {
    val bar = activity.getSupportActionBar()
    SSHDTabAdapter.addTab(R.string.tab_name_service, classOf[org.digimead.digi.ctrl.sshd.service.TabActivity], null)
    SSHDTabAdapter.addTab(R.string.tab_name_sessions, classOf[org.digimead.digi.ctrl.sshd.sessions.TabActivity], null)
    SSHDTabAdapter.addTab(R.string.tab_name_information, classOf[org.digimead.digi.ctrl.sshd.info.TabActivity], null)
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
          onInit(activity)
          onCreate(activity)
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
        case e: Event.OnSaveInstanceState =>
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
        Event.OnDestroyID -> State.Suspended)
      override def event(e: Event) = e match {
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
          activity.onDestroyExt(activity)
          super.event(e)
        case event =>
          log.fatal("illegal event " + event)
          None
      }
    }
  }
}
