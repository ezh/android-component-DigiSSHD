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

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.log.Logging

import android.content.Context
import android.widget.ArrayAdapter

/*class FilterRemoveAdapter(context: FilterRemoveActivity, values: Seq[FilterRemoveActivity.FilterItem],
  private val resource: Int = android.R.layout.simple_list_item_1,
  private val fieldId: Int = android.R.id.text1)
  extends ArrayAdapter[String](context, resource, fieldId) with Logging {
  private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
  private var separatorPos = values.length
  private val separator = inflater.inflate(R.layout.header, context.getListView(), false).asInstanceOf[TextView]
  separator.setText(R.string.selected_filters)
  values.foreach(v => add(v.value))
  add(null) // separator
  context.runOnUiThread(new Runnable { def run = notifyDataSetChanged() })
  def itemClick(position: Int) = synchronized {
    def update() {
      clear()
      values.foreach(v => if (!v.pending) add(v.value))
      add(null) // separator
      values.foreach(v => if (v.pending) add(v.value))
      notifyDataSetChanged()
    }
    if (position < separatorPos) {
      log.debug("available item click at position " + position)
      val want = position + 1
      var filtered = 0
      val selected = values.indexWhere(v => {
        if (v.pending == false)
          filtered += 1
        filtered == want
      })
      values(selected).pending = true
      separatorPos -= 1
      update()
      Toast.makeText(context, context.getString(R.string.service_filter_select).format(values(selected).value), DConstant.toastTimeout).show()
    } else if (position > separatorPos) {
      log.debug("pending item click at position " + position)
      val want = position - separatorPos
      var filtered = 0
      val selected = values.indexWhere(v => {
        if (v.pending == true)
          filtered += 1
        filtered == want
      })
      values(selected).pending = false
      separatorPos += 1
      update()
      Toast.makeText(context, context.getString(R.string.service_filter_select).format(values(selected).value), DConstant.toastTimeout).show()
    }
  }
  def getPending() = values.filter(_.pending == true)

  /*
   * skip separator view from RecycleBin
   */

}*/

class FilterRemoveAdapter(protected val context: Context)
  extends ArrayAdapter[String](context, android.R.layout.simple_expandable_list_item_1, android.R.id.text1)
  with FilterBlock.FragmentAdapter with Logging {
  log.debug("alive")

  @Loggable
  def submit() = synchronized {
    log.___gaze("SUBMIT")
    val pref = context.getSharedPreferences(DPreference.FilterInterface, Context.MODE_PRIVATE)
    val editor = pref.edit()
    setPending(Seq()).foreach(filter => editor.remove(filter))
    editor.commit()
    AnyBase.runOnUiThread { update }
  }
}

object FilterRemoveAdapter extends Logging {
  private[service] lazy val adapter: Option[FilterRemoveAdapter] = AppComponent.Context map {
    context =>
      Some(new FilterRemoveAdapter(context))
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
