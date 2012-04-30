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

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.RobotEsTrick
import org.scalatest.matchers.ShouldMatchers._
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import com.xtremelabs.robolectric.shadows.ShadowAlertDialog
import com.xtremelabs.robolectric.Robolectric
import android.app.Activity
import org.digimead.digi.ctrl.lib.aop.Logging

class SSHDActivityTest extends FunSuite with BeforeAndAfter with RobotEsTrick {
  lazy val roboClassHandler = RobotEsTrick.classHandler
  lazy val roboClassLoader = RobotEsTrick.classLoader
  lazy val roboDelegateLoadingClasses = RobotEsTrick.delegateLoadingClasses
  lazy val roboConfig = RobotEsTrick.config
  override val debug = true

  before {
    roboSetup
    //Logging.androidArg = false
    //Logging.fileArg = 0
    //println("!!!!!!!" + Logging.androidArg)
  }

  test("SSHDActivity") {
    val activity = new SSHDActivity()
    activity should not be (null)
    assert(activity.isInstanceOf[SSHDActivity])
    //Logging.androidArg = false
    //Logging.fileArg = 0
    //println("!!!!!!!" + Logging.androidArg)
    activity.onCreate(null)
    /*    FailedMarket.getId(activity) should (not equal (null) and not be (0) and not be (-1))
    val dialog = FailedMarket.createDialog(activity)
    dialog.show()
    val lastDialog = ShadowAlertDialog.getLatestAlertDialog()
    lastDialog should be === dialog
    val shadow = Robolectric.shadowOf(lastDialog)
    shadow.getTitle() should be === "Market failed"
    shadow.getMessage() should be === "Market application not found on the device"*/
  }

}
