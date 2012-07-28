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

import com.actionbarsherlock.app.SherlockDialogFragment
import android.content.DialogInterface
import org.digimead.digi.ctrl.lib.androidext.SafeDialog
import android.support.v4.app.FragmentManager
import org.digimead.digi.ctrl.lib.log.Logging

class SherlockSafeDialogFragment extends SherlockDialogFragment with SafeDialog with Logging {
/*  override def show(manager: FragmentManager, tag: String) {
    // DialogFragment.show() will take care of adding the fragment
    // in a transaction.  We also want to remove any currently showing
    // dialog, so make our own transaction and take care of that here.
    val ft = manager.beginTransaction()
    val prev = manager.findFragmentByTag(tag)
    if (prev != null) {
      ft.remove(prev)
    }
    ft.addToBackStack(null)
    ft.commitAllowingStateLoss
    log.g_a_s_e("!!!")
    super.show(manager, tag)
  }*/
  // http://stackoverflow.com/questions/10579545/dialogfragment-using-alertdialog-with-custom-layout
  override def onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    notifySafeDialogDismissed(dialog)
  }
}