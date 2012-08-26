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

package org.digimead.digi.ctrl.sshd

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging
import android.app.Activity
import com.actionbarsherlock.app.SherlockFragmentActivity
import com.actionbarsherlock.view.Menu

abstract class SSHDActivityMenu extends SSHDActivityState {
  this: SSHDActivity =>
  @Loggable
  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    super.onCreateOptionsMenu(menu)
    val inflater = getSupportMenuInflater()
    inflater.inflate(R.menu.menu, menu)
    true
  }
  /*
   *   @Loggable
  override def onCreateOptionsMenu(menu: Menu): Boolean = 
   * {

    //import com.actionbarsherlock.view.MenuItem
    //
    //menu.add(0, 1, 1, android.R.string.cancel).setIcon(android.R.drawable.ic_menu_camera).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    //menu.add(0, 2, 2, android.R.string.cut).setIcon(android.R.drawable.ic_menu_agenda).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    //menu.add(0, 3, 3, android.R.string.paste).setIcon(android.R.drawable.ic_menu_compass).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    //menu.add(0, 4, 4, android.R.string.search_go).setIcon(android.R.drawable.ic_menu_upload).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
  }
   */
}
