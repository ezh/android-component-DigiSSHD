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

package org.digimead.digi.ctrl.sshd.service

import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.sshd.SSHDActivity

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView

class TabDescription extends Fragment {
  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = {
    val view = new TextView(getActivity)
    view.setText("This is an instance of MyDialogFragment SERVICE")
    view
  }
}

object TabDescription {
  /** profiling support */
  private val ppLoading = SSHDActivity.ppGroup.start("service.TabDescription$")
  private lazy val description = AppComponent.Context.map(context =>
    Fragment.instantiate(context.getApplicationContext, classOf[TabDescription].getName, null))
  ppLoading.stop

  def apply() = description
}
