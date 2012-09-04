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

import org.digimead.digi.lib.ctrl.ext.XResource
import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.ctrl.sshd.SSHDTabAdapter
import org.digimead.digi.ctrl.sshd.ext.SSHDFragment

import android.app.Activity
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.ListView

class DenyFragment extends Fragment {
  protected val updateAdapterFunction = DenyAdapter.update _
  DenyFragment.fragment = Some(this)

  def tag = "fragment_session_filter_deny"
  @Loggable
  override def onAttach(activity: Activity) {
    super.onAttach(activity)
    DenyFragment.fragment = Some(this)
  }
  @Loggable
  override def onActivityCreated(savedInstanceState: Bundle) =
    DenyAdapter.adapter.foreach(adapter => onActivityCreated(adapter, DenyAdapter.update, savedInstanceState))
  @Loggable
  override def onResume() = {
    DenyAdapter.adapter.foreach(onResume)
    val activity = getSherlockActivity()
    val title = XResource.getString(activity, "app_name_filter_deny").getOrElse("Deny connection filters")
    activity.setTitle(Html.fromHtml("<b><i>" + title + "</i></b>"))
    Futures.future { DenyAdapter.update(activity) }
  }
  @Loggable
  override def onPause() = DenyAdapter.adapter.foreach(onPause)
  @Loggable
  override def onDetach() {
    DenyFragment.fragment = None
    super.onDetach()
  }
  @Loggable
  def onClickAddFilter(v: View): Unit =
    DenyAdapter.adapter.foreach(onClickAddFilter(_, false))
  @Loggable
  def onClickApply(v: View): Unit =
    Futures.future { DenyAdapter.adapter.foreach(onClickApply(_, false)) }
  @Loggable
  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) =
    DenyAdapter.adapter.foreach(adapter => onListItemClick(adapter, l, v, position, id))
}

object DenyFragment extends Logging {
  /** DenyFragment fragment instance */
  @volatile private[filter] var fragment: Option[DenyFragment] = None
  log.debug("alive")

  @Loggable
  def show() = SSHDTabAdapter.getSelectedFragment match {
    case Some(currentTabFragment) =>
      SSHDFragment.show(classOf[DenyFragment], currentTabFragment)
    case None =>
      log.fatal("current tab fragment not found")
  }
}
