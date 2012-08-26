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

import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.androidext.XAlertDialog
import org.digimead.digi.ctrl.sshd.SSHDTabAdapter

import com.actionbarsherlock.app.SherlockDialogFragment

import android.view.View

abstract class SSHDAlertDialog(val icon: Option[Int]) extends SherlockDialogFragment with XAlertDialog {
  lazy val extContent: Option[View] = None
  protected var parentVisibleTabFragment = new WeakReference[TabInterface](null)

  def this() = this(None)
  def getDialogActivity() = getSherlockActivity()
  def onTabFragmentHide(tab: TabInterface) = {
    log.debug("SSHDAlertDialog::onTabFragmentHide")
    tab.showBottomPanel
  }
  override def onResume() = {
    SSHDTabAdapter.getSelectedFragment.foreach(tab => parentVisibleTabFragment = new WeakReference(tab))
    parentVisibleTabFragment.get.foreach(tab => if (tab.isTopPanelAvailable && !getShowsDialog()) tab.hideBottomPanel)
    super.onResume
  }
  override def onPause() = {
    super.onPause
    parentVisibleTabFragment.get.foreach(tab => if (tab.isTopPanelAvailable && !getShowsDialog()) tab.showBottomPanel)
    parentVisibleTabFragment = new WeakReference(null)
  }
}
