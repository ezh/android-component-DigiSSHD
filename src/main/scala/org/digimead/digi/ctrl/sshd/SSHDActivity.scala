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
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package org.digimead.digi.ctrl.sshd

import scala.actors.Futures.future

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.base.AppService
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.log.AndroidLogger
import org.digimead.digi.ctrl.lib.log.FileLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.Activity
import org.digimead.digi.ctrl.sshd.comm.TabActivity
import org.digimead.digi.ctrl.sshd.info.TabActivity
import org.digimead.digi.ctrl.sshd.service.TabActivity

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
  private lazy val statusText = findViewById(R.id.status).asInstanceOf[TextView]
  private lazy val toggleStartStop = findViewById(R.id.toggleStartStop).asInstanceOf[ToggleButton]
  private val receiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = {
      intent.getAction() match {
        case DIntent.Update =>
          updateStatus()
        case _ =>
          log.error("skip unknown intent " + intent + " with context " + context)
      }
    }
  }
  var dialogAbout: SSHDDialogAbout = null
  if (true)
    Logging.addLogger(Seq(AndroidLogger, FileLogger))
  else
    Logging.addLogger(FileLogger)
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
    intent = new Intent().setClass(this, classOf[org.digimead.digi.ctrl.sshd.service.TabActivity])
    spec = tabHost.newTabSpec(classOf[org.digimead.digi.ctrl.sshd.service.TabActivity].getName()).setIndicator("Service",
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
        case id if id == classOf[org.digimead.digi.ctrl.sshd.service.TabActivity].getName() =>
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

    AppActivity.Inner ! AppActivity.Message.PrepareEnvironment(this, true, true, (success) => {
      if (AppActivity.Inner.state.get.code != DState.Broken)
        if (success)
          AppActivity.State(DState.Passive, Android.getString(this, "status_ready").
            getOrElse("Ready"))
        else
          AppActivity.State(DState.Broken, Android.getString(this, "status_error").
            getOrElse("Error"))
    })
  }
  @Loggable
  override def onStart() {
    super.onStart()
  }
  @Loggable
  override def onResume() {
    val filter = new IntentFilter()
    filter.addAction(DIntent.Update)
    registerReceiver(receiver, filter)
    AppService.Inner.bind(this)
    super.onResume()
  }
  @Loggable
  override def onPause() {
    super.onPause()
    AppService.Inner.unbind()
    unregisterReceiver(receiver)
  }
  @Loggable
  override def onDestroy() {
    super.onDestroy()
  }
  /**
   * latest point before complete initialization
   * user interface already visible and it is alive
   * now we may start processes under the hood
   *
   * after initialization AppActivity.LazyInit.init do nothing
   */
  @Loggable
  override def onWindowFocusChanged(hasFocus: Boolean) =
    if (hasFocus)
      future { AppActivity.LazyInit.init }
  @Loggable
  def onStartStop(v: View) {
    /*    if (toggleStartStop.isChecked())
      App.serviceStart()
    else
      App.serviceStop()*/
  }
  @Loggable
  def updateStatus() = {
    AppActivity.Inner.state.get match {
      case AppActivity.State(DState.Initializing, message, callback) =>
        statusText.setText(getString(R.string.status_initializing))
      case AppActivity.State(DState.Passive, message, callback) =>
        statusText.setText(getString(R.string.status_ready))
      case AppActivity.State(DState.Active, message, callback) =>
      // TODO
      //        statusText.setText(getString(R.string.st))
      case AppActivity.State(DState.Broken, message, callback) =>
        val errorMessage = getString(R.string.status_error).format(message.asInstanceOf[String])
        statusText.setText(errorMessage)
    }
  }
  @Loggable
  def onStatusClick(v: View) = {
    AppActivity.Inner.state.get.onClickCallback match {
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
