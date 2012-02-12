/*
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
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package org.digimead.digi.ctrl.sshd

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.dialog.FailedMarket
import org.digimead.digi.ctrl.lib.Common

import android.content.Intent
import android.net.Uri

object SSHDOptionMenu {
  @Loggable
  def onClickAbout(activity: SSHDActivity): Boolean = {
    activity.showDialog(activity.dialogAbout.id)
    true
  }
  @Loggable
  def onClickMarket(activity: SSHDActivity): Boolean = {
    try {
      val intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pname:" + Common.Constant.marketPackage))
      intent.addCategory(Intent.CATEGORY_BROWSABLE)
      activity.startActivity(intent)
    } catch {
      case _ => activity.showDialog(FailedMarket.getId(activity))
    }
    true
  }
}