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

package org.digimead.digi.ctrl.sshd.service.filter

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.sshd.service.FilterBlock

import android.content.Context

class AddAdapter(protected val context: Context) extends Adapter(context) with Logging {
  log.debug("alive")

  @Loggable
  def submit() = synchronized {
    val pref = context.getSharedPreferences(DPreference.FilterInterface, Context.MODE_PRIVATE)
    val editor = pref.edit()
    setPending(Seq()).foreach(filter => editor.putBoolean(filter, true))
    editor.commit()
    AnyBase.runOnUiThread { update }
  }
}

object AddAdapter extends Logging {
  private[service] lazy val adapter: Option[AddAdapter] = AppComponent.Context map {
    context =>
      Some(new AddAdapter(context))
  } getOrElse { log.fatal("unable to create FilterAddAdaper"); None }
  log.debug("alive")

  @Loggable
  def update(context: Context) = adapter.foreach {
    adapter =>
      // get list of predefined filters - active filters 
      val actual = predefinedFilters.diff(FilterBlock.listFilters(context))
      // get list of actual filters - pending filters 
      adapter.availableFilters = actual.map(v => (v, adapter.pendingFilters.exists(_ == v)))
      AnyBase.runOnUiThread { adapter.update }
  }
  @Loggable
  private def predefinedFilters(): Seq[String] = {
    log.debug("predefinedFilters(...)")
    Common.listInterfaces().map(entry => {
      val Array(interface, ip) = entry.split(":")
      if (ip == "0.0.0.0")
        Seq(interface + ":*.*.*.*")
      else
        Seq(entry, interface + ":*.*.*.*", "*:" + ip)
    }).flatten
  }
}
