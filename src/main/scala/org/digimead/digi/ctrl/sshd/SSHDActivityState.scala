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

import java.util.concurrent.atomic.AtomicInteger

import scala.actors.Actor

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging

import android.os.Bundle

abstract class SSHDActivityState extends SSHDActivityBase {
  this: SSHDActivity =>
  /**
   * @see android.support.v4.app.FragmentActivity#onCreate(android.os.Bundle)
   */
  @Loggable
  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    SSHDActivityState.State.actor ! SSHDActivityState.Event.OnCreate(this, savedInstanceState)
  }
  /**
   * @see android.support.v4.app.FragmentActivity#onStart()
   */
  @Loggable
  override def onStart() = SSHDActivity.ppGroup("SSHDActivity.onStart") {
    super.onStart()
    SSHDActivityState.State.actor ! SSHDActivityState.Event.OnStart(this)
  }
  /**
   * @see android.support.v4.app.FragmentActivity#onResume()
   */
  @Loggable
  override def onResume() = SSHDActivity.ppGroup("SSHDActivity.onResume") {
    super.onResume()
    SSHDActivityState.State.actor ! SSHDActivityState.Event.OnResume(this)
  }

  /**
   * @see android.support.v4.app.FragmentActivity#onSaveInstanceState()
   */
  @Loggable
  override def onSaveInstanceState(outState: Bundle) = SSHDActivity.ppGroup("onSaveInstanceState") {
    SSHDActivityState.State.actor ! SSHDActivityState.Event.OnSaveInstanceState(this, outState)
    super.onSaveInstanceState(outState)
  }
  /**
   * @see android.support.v4.app.FragmentActivity#onPause()
   */
  @Loggable
  override def onPause() = SSHDActivity.ppGroup("SSHDActivity.onPause") {
    SSHDActivityState.State.actor ! SSHDActivityState.Event.OnPause(this)
    super.onPause()
  }
  /**
   * @see android.support.v4.app.FragmentActivity#onStop()
   */
  @Loggable
  override def onStop() = SSHDActivity.ppGroup("SSHDActivity.onStop") {
    SSHDActivityState.State.actor ! SSHDActivityState.Event.OnStop(this)
    super.onStop()
  }
  /**
   * @see android.support.v4.app.FragmentActivity#onDestroy()
   */
  @Loggable
  override def onDestroy() = SSHDActivity.ppGroup("SSHDActivity.onDestroy") {
    SSHDActivityState.State.actor ! SSHDActivityState.Event.OnDestroy(this)
    super.onDestroy()
  }
}

object SSHDActivityState extends Logging {
  @volatile private var state: State = State.Initializing
  def get() = state
  /**
   * FSA android life cycle state trait
   */
  sealed abstract class State {
    def m: Map[Int, State]
    def event(e: Event): Option[State] = m.get(e.id)
  }
  object State {
    private[SSHDActivityState] lazy val actor = {
      val actor = new Actor {
        /** FSA */
        def act = {
          loop {
            react {
              case event: Event =>
                log.debug("start transition %s(event %s)".format(state, event))
                SSHDActivity.ppGroup("SSHDActivity$." + state + "<-" + event) {
                  state.event(event)
                } match {
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
    object Initializing extends State {
      lazy val m = Map[Int, State](
        Event.OnCreateID -> OnCreate)
      override def toString = "State.Initializing"
      override def event(e: Event) = e match {
        case Event.OnCreate(activity, savedInstanceState) =>
          //Thread.sleep(1000) // wait before load 
          SSHDActivity.stateInit(activity)
          SSHDActivity.stateCreate(activity)
          super.event(e)
        case event =>
          log.fatal("illegal event " + event)
          None
      }
    }
    object Suspended extends State {
      lazy val m = Map[Int, State](
        Event.OnCreateID -> OnCreate)
      override def toString = "State.Suspended"
      override def event(e: Event) = e match {
        case Event.OnCreate(activity, savedInstanceState) =>
          SSHDActivity.stateCreate(activity)
          super.event(e)
        case event =>
          log.fatal("illegal event " + event)
          None
      }
    }
    object OnCreate extends State {
      override def toString = "State.OnCreate"
      lazy val m = Map[Int, State](
        Event.OnStartID -> OnStart)
      override def event(e: Event) = e match {
        case Event.OnStart(activity) =>
          SSHDActivity.stateStart(activity)
          super.event(e)
        case event =>
          log.fatal("illegal event " + event)
          None
      }
    }
    object OnStart extends State {
      override def toString = "State.OnStart"
      lazy val m = Map[Int, State](
        Event.OnResumeID -> Running)
      override def event(e: Event) =
        e match {
          case Event.OnResume(activity) =>
            SSHDActivity.stateResume(activity)
            super.event(e)
          case event =>
            log.fatal("illegal event " + event)
            None
        }
    }
    object Running extends State {
      override def toString = "State.Running"
      lazy val m = Map[Int, State](
        Event.OnSaveInstanceStateID -> OnSaveInstanceState,
        Event.OnPauseID -> OnPause,
        Event.OnStopID -> OnStop,
        Event.OnDestroyID -> Suspended)
      override def event(e: Event) = e match {
        case Event.OnSaveInstanceState(activity, outState) =>
          super.event(e)
        case Event.OnPause(activity) =>
          SSHDActivity.statePause(activity)
          super.event(e)
        case Event.OnStop(activity) =>
          SSHDActivity.statePause(activity)
          SSHDActivity.stateStop(activity)
          super.event(e)
        case Event.OnDestroy(activity) =>
          SSHDActivity.statePause(activity)
          SSHDActivity.stateStop(activity)
          SSHDActivity.stateDestroy(activity)
          super.event(e)
        case event =>
          log.fatal("illegal event " + event)
          None
      }
    }
    object OnSaveInstanceState extends State {
      override def toString = "State.OnSaveInstanceState"
      lazy val m = Map[Int, State](
        Event.OnPauseID -> OnPause,
        Event.OnStopID -> OnStop,
        Event.OnDestroyID -> Suspended)
      override def event(e: Event) = e match {
        case Event.OnPause(activity) =>
          SSHDActivity.statePause(activity)
          super.event(e)
        case Event.OnStop(activity) =>
          SSHDActivity.statePause(activity)
          SSHDActivity.stateStop(activity)
          super.event(e)
        case Event.OnDestroy(activity) =>
          SSHDActivity.statePause(activity)
          SSHDActivity.stateStop(activity)
          SSHDActivity.stateDestroy(activity)
          super.event(e)
        case event =>
          log.fatal("illegal event " + event)
          None
      }
    }
    object OnPause extends State {
      override def toString = "State.OnPause"
      lazy val m = Map[Int, State](
        Event.OnStopID -> OnStop,
        Event.OnDestroyID -> Suspended,
        /*
         * fucking android, OnSaveInstanceState AFTER OnPause! LOL. WTF with documentation about lifecycle?
         */
        Event.OnSaveInstanceStateID -> OnPause)
      override def event(e: Event) = e match {
        case Event.OnSaveInstanceState(activity, outState) =>
          log.warn("another bug in 4.x, OnSaveInstanceState after OnPause")
          super.event(e)
        case Event.OnStop(activity) =>
          SSHDActivity.stateStop(activity)
          super.event(e)
        case Event.OnDestroy(activity) =>
          SSHDActivity.stateStop(activity)
          SSHDActivity.stateDestroy(activity)
          super.event(e)
        case event =>
          log.fatal("illegal event " + event)
          None
      }
    }
    object OnStop extends State {
      override def toString = "State.OnStop"
      lazy val m = Map[Int, State](
        Event.OnStartID -> OnStart,
        Event.OnDestroyID -> Suspended)
      override def event(e: Event) = e match {
        case Event.OnStart(activity) =>
          SSHDActivity.stateStart(activity)
          super.event(e)
        case Event.OnDestroy(activity) =>
          SSHDActivity.stateDestroy(activity)
          super.event(e)
        case event =>
          log.fatal("illegal event " + event)
          None
      }
    }
  }
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
}
