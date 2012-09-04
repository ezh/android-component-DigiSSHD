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

import org.digimead.digi.lib.ctrl.AnyBase
import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.ctrl.base.AppComponent
import org.digimead.digi.lib.ctrl.declaration.DPreference
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.ctrl.sshd.service.FilterBlock

import android.content.Context

class RemoveAdapter(protected val context: Context) extends Adapter(context) with Logging {
  log.debug("alive")

  @Loggable
  def submit() = synchronized {
    val pref = context.getSharedPreferences(DPreference.FilterInterface, Context.MODE_PRIVATE)
    val editor = pref.edit()
    setPending(Seq()).foreach(filter => editor.remove(filter))
    editor.commit()
    AnyBase.runOnUiThread { update }
  }
}

object RemoveAdapter extends Logging {
  private[service] lazy val adapter: Option[RemoveAdapter] = AppComponent.Context map {
    context =>
      Some(new RemoveAdapter(context))
  } getOrElse { log.fatal("unable to create FilterRemoveAdaper"); None }
  log.debug("alive")

  @Loggable
  def update(context: Context) = adapter.foreach {
    adapter =>
      // get list of predefined filters - active filters 
      val actual = FilterBlock.listFilters(context)
      // get list of actual filters - pending filters 
      adapter.availableFilters = actual.map(v => (v, adapter.pendingFilters.exists(_ == v)))
      AnyBase.runOnUiThread { adapter.update }
  }
}
