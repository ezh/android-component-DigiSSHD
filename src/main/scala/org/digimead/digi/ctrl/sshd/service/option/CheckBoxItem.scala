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

package org.digimead.digi.ctrl.sshd.service.option

import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.sshd.service.OptionBlock.Item

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget

trait CheckBoxItem extends Item {
  def onCheckboxClick(view: widget.CheckBox, lastState: Boolean)
  def getView(context: Context, inflater: LayoutInflater): View = {
    val view = inflater.inflate(XResource.getId(context, "element_option_list_item_multiple_choice", "layout"), null)
    val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[widget.CheckBox]
    checkbox.setClickable(false)
    checkbox.setFocusable(false)
    checkbox.setFocusableInTouchMode(false)
    checkbox.setChecked(getState[Boolean](context))
    view
  }
  def getState[T](context: Context)(implicit m: Manifest[T]): T = {
    assert(m.erasure == option.kind)
    val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
    pref.getBoolean(option.tag, option.default.asInstanceOf[Boolean]).asInstanceOf[T]
  }
}
