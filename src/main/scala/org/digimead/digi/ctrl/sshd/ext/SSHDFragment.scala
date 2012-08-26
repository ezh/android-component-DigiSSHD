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

package org.digimead.digi.ctrl.sshd.ext

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDActivity

import com.actionbarsherlock.app.SherlockFragmentActivity

import android.support.v4.app.Fragment
import android.view.View

trait SSHDFragment extends Logging {
  this: Fragment with SSHDFragment.Sherlock =>
  def onTabFragmentShow(tab: TabInterface) = {
    log.debug("SSHDFragment::onTabFragmentShow")
    if (tab.isTopPanelAvailable)
      tab.hideBottomPanel
    else
      SSHDActivity.activity.foreach {
        activity =>
          activity.findViewById(R.id.main_primary).setVisibility(View.GONE)
          activity.findViewById(R.id.main_secondary).setVisibility(View.VISIBLE)
      }
  }
  def onTabFragmentHide(tab: TabInterface) = {
    log.debug("SSHDFragment::onTabFragmentHide")
    if (tab.isTopPanelAvailable)
      tab.showBottomPanel
    else
      SSHDActivity.activity.foreach {
        activity =>
          activity.findViewById(R.id.main_primary).setVisibility(View.VISIBLE)
          activity.findViewById(R.id.main_secondary).setVisibility(View.GONE)
      }
  }
  def getSherlockActivity(): SherlockFragmentActivity
  def tag(): String
}

object SSHDFragment extends Logging {
  type Sherlock = { def getSherlockActivity(): SherlockFragmentActivity }

  @Loggable
  def show(fragment: Class[_ <: SSHDFragment], tab: TabInterface) = AnyBase.runOnUiThread {
    val manager = tab.getSherlockActivity.getSupportFragmentManager
    val fragmentInstance = Fragment.instantiate(tab.getSherlockActivity, fragment.getName, null)
    val tag = fragmentInstance.asInstanceOf[SSHDFragment].tag
    val target = if (tab.isTopPanelAvailable) {
      log.debug("show embedded fragment " + tag)
      R.id.main_topPanel
    } else {
      log.debug("show modal fragment " + tag)
      R.id.main_secondary
    }
    val ft = manager.beginTransaction()
    ft.replace(target, fragmentInstance, tag)
    ft.addToBackStack(tag)
    ft.commit()
  }
}
