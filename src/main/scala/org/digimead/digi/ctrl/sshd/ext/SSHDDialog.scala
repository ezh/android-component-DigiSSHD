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

import scala.Option.option2Iterable
import scala.ref.WeakReference
import org.digimead.digi.ctrl.lib.androidext.SafeDialog
import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.R
import com.actionbarsherlock.app.SherlockDialogFragment
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import org.digimead.digi.ctrl.lib.androidext.XDialog

abstract class SSHDDialog extends SherlockDialogFragment with XDialog with SafeDialog with Logging {
  @Loggable
  override def onPause() = {
    super.onPause
    if (!getShowsDialog) {
      Option(getSherlockActivity.findViewById(R.id.main_bottomPanel)).foreach {
        case panel if !panel.isShown =>
          panel.setVisibility(View.VISIBLE)
        case _ =>
      }
    }
  }
  @Loggable
  def show(fragment: TabInterface) {
    val context = fragment.getSherlockActivity
    val manager = context.getSupportFragmentManager
    if (isInBackStack(manager) && fragment.isTopPanelAvailable) {
      log.debug("restore " + tag)
      manager.popBackStack(tag, 0)
    } else {
      log.debug("show " + tag)
      SafeDialog(context, tag, () => this).transaction((ft, fragment, target) => {
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        ft.addToBackStack(tag)
      }).target(R.id.main_topPanel).show()
    }
  }
  def getDialogActivity() = getSherlockActivity()
}
