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

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import org.digimead.digi.lib.ctrl.AnyBase
import org.digimead.digi.lib.ctrl.log.appender.AndroidAppender
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.log.Record
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.digi.lib.util.SyncVar
import org.scalatest.junit.JUnitSuite
import org.scalatest.junit.ShouldMatchersForJUnit

import com.jayway.android.robotium.solo.Solo

import android.test.ActivityInstrumentationTestCase2

class Football
  extends ActivityInstrumentationTestCase2[SSHDActivity](classOf[SSHDActivity])
  with JUnitSuite with ShouldMatchersForJUnit with Logging {
  implicit val dispatcher = org.digimead.digi.ctrl.sshd.TestDispatcher.dispatcher
  @volatile private var solo: Solo = null
  @volatile private var activity: SSHDActivity = null
  @volatile var startupTime = System.currentTimeMillis
  val logResult = new SyncVar[Record]()
  val logSubscriber = new LogSubscriber

  def testFootball() {
    log.warn("testFootball BEGIN")

    Logging.suspend
    logSubscriber.lockAfterMatch.set(true)
    startupTime = System.currentTimeMillis
    activity = getActivity
    startupTime = (System.currentTimeMillis - startupTime) / 1000
    solo = new Solo(getInstrumentation(), activity)
    log.warn("current startup time is " + startupTime + "s")

    startupTime.toInt should be < (5)
    Thread.sleep(10000)
    AnyBase.ppGroup.names.toSeq.sorted.foreach(name => log.debug(AnyBase.ppGroup.snapshot(name).toShortString))
    SSHDActivity.ppGroup.names.toSeq.sorted.foreach(name => log.debug(AnyBase.ppGroup.snapshot(name).toShortString))

    log.warn("testFootball END")
  }

  def assertLog(s: String, f: (String, String) => Boolean, timeout: Long): Record = synchronized {
    logSubscriber.want.set(s, f)
    Logging.resume
    logResult.unset()
    val result = logResult.get(timeout)
    assert(result != None, "log record \"" + s + "\" not found")
    result.get
  }
  override def setUp() {
    super.setUp()
    Logging.reset()
    Logging.Event.subscribe(logSubscriber)
    Logging.resume()
    Logging.addAppender(AndroidAppender)
    log.info("setUp")
    logResult.unset()
  }
  override def tearDown() = {
    Logging.resume()
    logResult.unset()
    log.info("tearDown")
    Logging.Event.removeSubscriptions
    try {
      if (activity != null) {
        activity.finish()
        activity = null
      }
      if (solo != null) {
        solo.finalize()
        solo = null
      }
    } catch {
      case e =>
        e.printStackTrace()
    }
    super.tearDown()
    Thread.sleep(1000)
  }
  class LogSubscriber extends Logging.Event.Sub {
    val lockAfterMatch = new AtomicBoolean(false)
    val want = new AtomicReference[(String, (String, String) => Boolean)](null)
    def notify(pub: Logging.Event.Pub, event: Logging.Event) = {
      event match {
        case event: Logging.Event.Outgoing =>
          want.get match {
            case null =>
            case (message, f) if event.record.message == null =>
            case (message, f) if f != null && message != null && event.record.message != null =>
              if (f(event.record.message.trim, message.trim)) {
                logSubscriber.want.set(null)
                logResult.put(event.record, 60000)
                if (lockAfterMatch.get)
                  if (logSubscriber.want.get == null)
                    logResult.waitUnset(60000)
              }
          }
        case _ =>
      }
    }
  }
}
