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

import org.digimead.digi.lib.ctrl.AnyBase
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDConsoleFragment

import com.actionbarsherlock.app.SherlockFragmentActivity

import android.support.v4.app.Fragment
import android.view.View

trait TabInterface extends Logging {
  this: Fragment with TabInterface.Sherlock =>
  private var bottomPanelHideCounter = 0
  def onTabSelected()
  def getSherlockActivity(): SherlockFragmentActivity
  def getTabDescriptionFragment(): Option[Fragment]
  def showConsoleFragment() = if (isTopPanelAvailable)
    SSHDConsoleFragment.show(this)
  def showTabDescriptionFragment() = if (isTopPanelAvailable)
    getTabDescriptionFragment.foreach {
      case fragment if !fragment.isAdded =>
        log.debug("show description fragment for " + this.getClass.getName)
        val manager = getActivity.getSupportFragmentManager
        if (manager.getBackStackEntryCount == 0) {
          val ft = manager.beginTransaction()
          ft.replace(R.id.main_topPanel, fragment)
          ft.commit()
        }
      case _ =>
        log.debug("skip show description fragment for " + this.getClass.getName)
    }
  def isTopPanelAvailable =
    Option(getActivity.findViewById(R.id.main_topPanel)).exists(_.isShown)
  def hideBottomPanel() = synchronized {
    bottomPanelHideCounter += 1
    if (bottomPanelHideCounter == 1)
      AnyBase.runOnUiThread { getSherlockActivity.findViewById(R.id.main_bottomPanel).setVisibility(View.GONE) }
    log.debug("new bottom panel hide counter is " + bottomPanelHideCounter)
  }
  def showBottomPanel() = synchronized {
    bottomPanelHideCounter -= 1
    if (bottomPanelHideCounter == 0)
      AnyBase.runOnUiThread { getSherlockActivity.findViewById(R.id.main_bottomPanel).setVisibility(View.VISIBLE) }
    log.debug("new bottom panel hide counter is " + bottomPanelHideCounter)
  }
}

object TabInterface {
  type Sherlock = { def getSherlockActivity(): SherlockFragmentActivity }
}
