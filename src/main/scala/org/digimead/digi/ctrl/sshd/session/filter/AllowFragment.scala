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

package org.digimead.digi.ctrl.sshd.session.filter

import scala.actors.Futures

import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.SSHDTabAdapter
import org.digimead.digi.ctrl.sshd.ext.SSHDFragment

import android.app.Activity
import android.os.Bundle
import android.text.Html

class AllowFragment extends Fragment {
  AllowFragment.fragment = Some(this)

  def tag = "fragment_session_filter_allow"
  @Loggable
  override def onAttach(activity: Activity) {
    super.onAttach(activity)
    AllowFragment.fragment = Some(this)
  }
  @Loggable
  override def onActivityCreated(savedInstanceState: Bundle) = {
    super.onActivityCreated(savedInstanceState)
    AllowAdapter.adapter.foreach(setListAdapter)
  }
  @Loggable
  override def onResume() = {
    super.onResume
    val activity = getSherlockActivity()
    val title = XResource.getString(activity, "app_name_filter_allow").getOrElse("Allow connection filters")
    activity.setTitle(Html.fromHtml("<b><i>" + title + "</i></b>"))
    Futures.future { AllowAdapter.update(activity) }
  }
  @Loggable
  override def onDetach() {
    AllowFragment.fragment = None
    super.onDetach()
  }
}

object AllowFragment extends Logging {
  /** AllowFragment instance */
  @volatile private var fragment: Option[AllowFragment] = None
  log.debug("alive")

  @Loggable
  def show() = SSHDTabAdapter.getSelectedFragment match {
    case Some(currentTabFragment) =>
      SSHDFragment.show(classOf[AllowFragment], currentTabFragment)
    case None =>
      log.fatal("current tab fragment not found")
  }
}
