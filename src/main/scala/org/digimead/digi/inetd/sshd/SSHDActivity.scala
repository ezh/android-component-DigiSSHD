/*
 * DigiSSHD - DigiINETD component for Android Platform
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

package org.digimead.digi.inetd.sshd

import org.digimead.digi.inetd.lib.aop.Loggable
import org.digimead.digi.inetd.lib.base.Activity
import org.digimead.digi.inetd.lib.Android
import org.digimead.digi.inetd.lib.AppActivity
import org.digimead.digi.inetd.lib.AppService
import org.digimead.digi.inetd.lib.Common
import org.digimead.digi.inetd.sshd.comm.TabActivity
import org.digimead.digi.inetd.sshd.info.TabActivity
import org.digimead.digi.inetd.sshd.service.TabActivity
import org.slf4j.LoggerFactory

import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TabHost.OnTabChangeListener
import android.widget.TabHost
import android.widget.TextView
import android.widget.ToggleButton

class SSHDActivity extends android.app.TabActivity with Activity {
  private val log = LoggerFactory.getLogger(getClass.getName().replaceFirst("org.digimead.digi.inetd", "o.d.d.i"))
  private lazy val statusText = findViewById(R.id.status).asInstanceOf[TextView]
  private lazy val toggleStartStop = findViewById(R.id.toggleStartStop).asInstanceOf[ToggleButton]
  private val receiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = {
      intent.getAction() match {
        case Common.Intent.update =>
          updateStatus()
        case _ =>
          log.error("skip unknown intent " + intent + " with context " + context)
      }
    }
  }
  var dialogAbout: SSHDDialogAbout = null
  log.debug("alive")

  /** Called when the activity is first created. */
  @Loggable
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)

    dialogAbout = new SSHDDialogAbout(this)
    val res = getResources() // Resource object to get Drawables
    val tabHost = getTabHost() // The activity TabHost
    var spec: TabHost#TabSpec = null // Resusable TabSpec for each tab
    var intent: Intent = null // Reusable Intent for each tab

    // Create an Intent to launch an Activity for the tab (to be reused)
    // Initialize a TabSpec for each tab and add it to the TabHost
    intent = new Intent().setClass(this, classOf[org.digimead.digi.inetd.sshd.service.TabActivity])
    spec = tabHost.newTabSpec(classOf[org.digimead.digi.inetd.sshd.service.TabActivity].getName()).setIndicator("Service",
      res.getDrawable(R.drawable.ic_tab_settings_service))
      .setContent(intent)
    tabHost.addTab(spec)

    intent = new Intent().setClass(this, classOf[comm.TabActivity])
    spec = tabHost.newTabSpec(classOf[comm.TabActivity].getName()).setIndicator("Comm",
      res.getDrawable(R.drawable.ic_tab_settings_comm))
      .setContent(intent)
    tabHost.addTab(spec)

    intent = new Intent().setClass(this, classOf[info.TabActivity])
    spec = tabHost.newTabSpec(classOf[info.TabActivity].getName()).setIndicator("Info",
      res.getDrawable(R.drawable.ic_tab_settings_info))
      .setContent(intent)
    tabHost.addTab(spec)

    tabHost.setOnTabChangedListener(new OnTabChangeListener() {
      def onTabChanged(tab: String) = tab match {
        case id if id == classOf[org.digimead.digi.inetd.sshd.service.TabActivity].getName() =>
          setTitle(R.string.app_name_service)
        case id if id == classOf[comm.TabActivity].getName() =>
          setTitle(R.string.app_name_comm)
        case id if id == classOf[info.TabActivity].getName() =>
          setTitle(R.string.app_name_info)
        case id =>
          log.error("unknown tab " + tab)
      }
    })

    tabHost.setCurrentTab(2)

    AppActivity.Inner map {
      inner =>
        inner ! AppActivity.Message.PrepareEnvironment(this, true, true, (success) => {
          if (inner.getStatus().state != Common.State.error)
            if (success)
              AppActivity.Status(Common.State.ready, Android.getString(this, "status_ready"))
            else
              AppActivity.Status(Common.State.error, Android.getString(this, "status_error"))
        })
    }
  }
  @Loggable
  override def onStart() {
    super.onStart()
  }
  @Loggable
  override def onResume() {
    AppService.Inner map {
      inner =>
        val filter = new IntentFilter()
        filter.addAction(Common.Intent.update)
        registerReceiver(receiver, filter)
        inner.bind(this)
    }

    super.onResume()
  }
  @Loggable
  override def onPause() {
    super.onPause()
    AppService.Inner map {
      inner =>
        inner.unbind()
        unregisterReceiver(receiver)
    }
  }
  @Loggable
  override def onDestroy() {
    super.onDestroy()
  }
  @Loggable
  def onStartStop(v: View) {
    /*    if (toggleStartStop.isChecked())
      App.serviceStart()
    else
      App.serviceStop()*/
  }
  @Loggable
  def updateStatus() = AppActivity.Inner foreach {
    inner =>
      inner.getStatus() match {
        case AppActivity.Status(Common.State.initializing, message, callback) =>
          statusText.setText(getString(R.string.status_initializing))
        case AppActivity.Status(Common.State.ready, message, callback) =>
          statusText.setText(getString(R.string.status_ready))
        case AppActivity.Status(Common.State.active, message, callback) =>
        // TODO
        //        statusText.setText(getString(R.string.st))
        case AppActivity.Status(Common.State.error, message, callback) =>
          val errorMessage = getString(R.string.status_error).format(message.asInstanceOf[String])
          statusText.setText(errorMessage)
      }
  }
  @Loggable
  def onStatusClick(v: View) = AppActivity.Inner foreach {
    inner =>
      inner.getStatus().onClickCallback match {
        case cb: Function0[_] => cb()
        case _ =>
      }
  }
  @Loggable
  def onUpdateServiceIntent() {

  }
  @Loggable
  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    val inflater = getMenuInflater()
    inflater.inflate(R.menu.menu, menu);
    true
  }
  @Loggable
  override def onOptionsItemSelected(item: MenuItem): Boolean =
    item.getItemId() match {
      case R.id.menu_about =>
        SSHDOptionMenu.onClickAbout(this)
      case R.id.menu_market =>
        SSHDOptionMenu.onClickMarket(this)
      case _ =>
        super.onOptionsItemSelected(item)
    }
  @Loggable
  override def onCreateDialog(id: Int): Dialog =
    id match {
      case id if id == dialogAbout.id =>
        dialogAbout.createDialog()
      case id =>
        Common.onCreateDialog(id, this)
    }
}
