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

import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.R

import android.support.v4.app.Fragment
import android.view.View

trait TabInterface extends Logging {
  this: Fragment =>
  def onTabSelected()
  def getTabDescriptionFragment(): Option[Fragment]
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
    Option(getActivity.findViewById(R.id.main_topPanel)).exists(_.getVisibility == View.VISIBLE)
}
