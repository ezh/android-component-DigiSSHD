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

import org.digimead.digi.ctrl.lib.androidext.SafeDialog
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.R

import com.actionbarsherlock.app.SherlockDialogFragment

import android.content.DialogInterface
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.view.View

class SherlockSafeDialogFragment extends SherlockDialogFragment with SafeDialog with Logging {
  @Loggable
  override def onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    notifySafeDialogDismissed(dialog)
  }
  @Loggable
  override def onResume() = {
    if (!getShowsDialog) {
      Option(getSherlockActivity.findViewById(R.id.main_bottomPanel)).foreach {
        case panel if panel.isShown =>
          panel.setVisibility(View.GONE)
        case _ =>
      }
    }
    super.onResume
  }
  @Loggable
  override def onPause() = {
    /*
     * android fragment transaction architecture is incomplete
     * backstack persistency is unfinished
     * reimplement it by hands is unreasonable
     * deadlines, beer, other stuff... we must to forgive android framework coders
     * in the hope of android 5.x
     * 30.07.2012 Ezh
     */
    // main activity unavailable (configuration change are in progress for example)
    if (!SafeDialog.isEnabled)
      getSherlockActivity.getSupportFragmentManager.
        popBackStack(this.toString, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    if (!getShowsDialog) {
      Option(getSherlockActivity.findViewById(R.id.main_bottomPanel)).foreach {
        case panel if !panel.isShown =>
          panel.setVisibility(View.VISIBLE)
        case _ =>
      }
    }
    super.onPause
  }
  def isInBackStack(manager: FragmentManager): Boolean = {
    val tag = toString
    if (manager.findFragmentByTag(tag) == null)
      return false
    for (i <- 0 until manager.getBackStackEntryCount)
      if (manager.getBackStackEntryAt(i).getName == tag)
        return true
    false
  }
  def show(fragment: TabInterface) {
    val tag = toString
    val context = fragment.getSherlockActivity
    val manager = context.getSupportFragmentManager
    if (isInBackStack(manager) && fragment.isTopPanelAvailable) {
      log.debug("restore " + tag)
      manager.popBackStack(tag, 0)
    } else {
      log.debug("show " + tag)
      SafeDialog.transaction.prepend((ft, fragment, target) => {
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        ft.addToBackStack(tag)
      }).show(context, Some(R.id.main_topPanel), tag, () => this)
    }
  }
}

object SherlockSafeDialogFragment {
  implicit def dialog2string(d: SherlockSafeDialogFragment) = d.toString
}
