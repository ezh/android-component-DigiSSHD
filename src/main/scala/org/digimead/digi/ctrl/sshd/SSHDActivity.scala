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

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

import scala.actors.Futures.future
import scala.actors.Actor
import scala.ref.WeakReference
import scala.util.Random

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.base.AppService
import org.digimead.digi.ctrl.lib.declaration.DMessage.Origin.anyRefToOrigin
import org.digimead.digi.ctrl.lib.declaration.DMessage.IAmBusy
import org.digimead.digi.ctrl.lib.declaration.DMessage.IAmReady
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DMessage
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.dialog.FailedMarket
import org.digimead.digi.ctrl.lib.dialog.Report
import org.digimead.digi.ctrl.lib.log.AndroidLogger
import org.digimead.digi.ctrl.lib.log.FileLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.Activity
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.info.TabActivity
import org.digimead.digi.ctrl.sshd.service.TabActivity
import org.digimead.digi.ctrl.sshd.session.TabActivity

import android.app.AlertDialog
import android.app.Dialog
import android.app.ProgressDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.text.Html
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
    SSHDActivity.activity = new WeakReference(this)

    val res = getResources() // Resource object to get Drawables
    val tabHost = getTabHost() // The activity TabHost
    var spec: TabHost#TabSpec = null // Resusable TabSpec for each tab
    var intent: Intent = null // Reusable Intent for each tab

    // Create an Intent to launch an Activity for the tab (to be reused)
    // Initialize a TabSpec for each tab and add it to the TabHost
    intent = new Intent().setClass(this, classOf[org.digimead.digi.ctrl.sshd.service.TabActivity])
    spec = tabHost.newTabSpec(classOf[org.digimead.digi.ctrl.sshd.service.TabActivity].getName()).setIndicator("Service",
      res.getDrawable(R.drawable.ic_tab_service))
      .setContent(intent)
    tabHost.addTab(spec)

    intent = new Intent().setClass(this, classOf[session.TabActivity])
    spec = tabHost.newTabSpec(classOf[session.TabActivity].getName()).setIndicator("Session",
      res.getDrawable(R.drawable.ic_tab_session))
      .setContent(intent)
    tabHost.addTab(spec)

    intent = new Intent().setClass(this, classOf[info.TabActivity])
    spec = tabHost.newTabSpec(classOf[info.TabActivity].getName()).setIndicator("Info",
      res.getDrawable(R.drawable.ic_tab_info))
      .setContent(intent)
    tabHost.addTab(spec)

    tabHost.setOnTabChangedListener(new OnTabChangeListener() {
      def onTabChanged(tab: String) = tab match {
        case id if id == classOf[org.digimead.digi.ctrl.sshd.service.TabActivity].getName() =>
          setTitle(R.string.app_name_service)
        case id if id == classOf[session.TabActivity].getName() =>
          setTitle(R.string.app_name_session)
        case id if id == classOf[info.TabActivity].getName() =>
          setTitle(R.string.app_name_info)
        case id =>
          log.error("unknown tab " + tab)
      }
    })

    tabHost.setCurrentTab(2)
  }
  @Loggable
  override def onStart() {
    super.onStart()
  }
  @Loggable
  override def onResume() {
    AppService.Inner.bind(this)
    SSHDActivity.consistent = true
    if (SSHDActivity.consistent && SSHDActivity.focused && AppActivity.LazyInit.nonEmpty)
      future {
        IAmBusy(SSHDActivity, Android.getString(this, "state_loading_internal_routines").getOrElse("loading internals"))
        AppActivity.Inner.state.set(AppActivity.State(DState.Passive, Android.getCapitalized(this, "status_ready").getOrElse("Ready")))
        AppActivity.LazyInit.init
        System.gc
        Thread.sleep(500)
        IAmReady(SSHDActivity, Android.getString(this, "state_loaded_internal_routines").getOrElse("loaded internals"))
      }
    super.onResume()
  }
  @Loggable
  override def onPause() {
    super.onPause()
    SSHDActivity.consistent = false
    AppService.Inner.unbind()
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
  override def onWindowFocusChanged(hasFocus: Boolean) = {
    super.onWindowFocusChanged(hasFocus)
    SSHDActivity.focused = hasFocus
    //SSHDActivity.onBusy(this) // show busy dialog if any
    if (SSHDActivity.consistent && SSHDActivity.focused && AppActivity.LazyInit.nonEmpty)
      future {
        IAmBusy(SSHDActivity, Android.getString(this, "state_loading_internal_routines").getOrElse("loading internals"))
        AppActivity.Inner.state.set(AppActivity.State(DState.Passive, Android.getCapitalized(this, "status_ready").getOrElse("Ready")))
        AppActivity.LazyInit.init
        System.gc
        Thread.sleep(500)
        IAmReady(SSHDActivity, Android.getString(this, "state_loaded_internal_routines").getOrElse("loaded internals"))
      }
  }
  @Loggable
  def onStartStop(v: View) {
    /*    if (toggleStartStop.isChecked())
      App.serviceStart()
    else
      App.serviceStop()*/
  }
  @Loggable
  def updateStatus() = {
    /*    AppActivity.Inner.state.get match {
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
    }*/
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
      case R.id.menu_help =>
        // leave UI thread
        future {
          showDialogSafe[AlertDialog](() => {
            val dialog = new AlertDialog.Builder(this).
              setTitle(R.string.dialog_help_title).
              setMessage(R.string.dialog_help_message).
              setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                def onClick(dialog: DialogInterface, whichButton: Int) {}
              }).
              setIcon(R.drawable.ic_menu_help).
              create()
            dialog.show()
            dialog
          })
        }
        true
      case R.id.menu_gplay =>
        try {
          val intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pname:" + DConstant.marketPackage))
          intent.addCategory(Intent.CATEGORY_BROWSABLE)
          startActivity(intent)
        } catch {
          case _ => showDialogSafe(FailedMarket.getId(this))
        }
        true
      case R.id.menu_report =>
        Report.submit(this)
        true
      case R.id.menu_quit =>
        // leave UI thread
        future {
          showDialogSafe[AlertDialog](() => {
            val dialog = new AlertDialog.Builder(this).
              setTitle(R.string.dialog_exit_title).
              setMessage(R.string.dialog_exit_message).
              setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                def onClick(dialog: DialogInterface, whichButton: Int) { finish }
              }).
              setNegativeButton(android.R.string.cancel, null).
              setIcon(R.drawable.ic_menu_quit).
              create()
            dialog.show()
            dialog
          })
        }

        true
      case _ =>
        super.onOptionsItemSelected(item)
    }
  @Loggable
  override def onCreateDialog(id: Int): Dialog = {
    Option(Common.onCreateDialog(id, this)).foreach(dialog => return dialog)
    id match {
      case id =>
        super.onCreateDialog(id)
    }
  }
}

object SSHDActivity extends Actor with Logging {
  private var activity = new WeakReference[SSHDActivity](null)
  @volatile private var focused = false
  @volatile private var consistent = false
  // null - empty, None - dialog upcoming, Some - dialog in progress
  private val busyDialog = new AtomicReference[Option[ProgressDialog]](None)
  private val busyCounter = new AtomicInteger()
  private val busySize = 5
  private var busyBuffer = Seq[String]()
  private var busyKicker: Option[ScheduledExecutorService] = None
  private val busyKickerF = new Runnable { def run = SSHDActivity.this ! null }
  private val busyKickerDelay = 3000
  private val busyKickerRate = 50
  private val publicReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = {
      intent.getAction() match {
        case DIntent.Update =>
          activity.get.foreach(_.updateStatus)
        case _ =>
          log.error("skip unknown intent " + intent + " with context " + context)
      }
    }
  }

  start
  AppActivity.LazyInit("main activity onCreate logic") {
    activity.get.foreach {
      activity =>
        // register BroadcastReceiver
        val filter = new IntentFilter()
        filter.addAction(DIntent.Update)
        activity.registerReceiver(publicReceiver, filter)
        //
        AppActivity.Inner ! AppActivity.Message.PrepareEnvironment(activity, true, true, (success) => {
          if (AppActivity.Inner.state.get.code != DState.Broken)
            if (success)
              AppActivity.State(DState.Passive, Android.getString(activity, "status_ready").
                getOrElse("Ready"))
            else
              AppActivity.State(DState.Broken, Android.getString(activity, "status_error").
                getOrElse("Error"))
        })
    }
  }
  log.debug("alive")

  def act = {
    loop {
      react {
        case msg: DMessage.IAmBusy =>
          busyCounter.incrementAndGet
          activity.get.foreach(onBusy)
        case msg: DMessage.IAmReady =>
          busyCounter.decrementAndGet
          onReady
        case null => // kick it
          activity.get.foreach(onKick)
        case message: AnyRef =>
          log.error("skip unknown message " + message.getClass.getName + ": " + message)
        case message =>
          log.error("skip unknown message " + message)
      }
    }
  }
  @Loggable
  private def onBusy(activity: SSHDActivity) = synchronized {
    busyDialog.get() match {
      case Some(dialog) =>
      case None =>
        AppActivity.Inner.state.set(AppActivity.State(DState.Busy))
        future {
          val message = Android.getString(activity, "decoder_not_implemented").getOrElse("decoder not implemented ;-)")
          busyBuffer = Seq(message)
          for (i <- 1 until busySize) addDataToBusyBuffer
          busyDialog.set(activity.showDialogSafe[ProgressDialog](() =>
            if (busyCounter.get > 0) {
              busyKicker = Some(Executors.newSingleThreadScheduledExecutor())
              busyKicker.get.scheduleAtFixedRate(busyKickerF, busyKickerDelay, busyKickerRate, TimeUnit.MILLISECONDS)
              ProgressDialog.show(activity, "Please wait...", Html.fromHtml(busyBuffer.takeRight(busySize).mkString("<br/>")), true)
            } else
              null))
        }
    }
  }
  @Loggable
  private def onReady() = synchronized {
    busyDialog.get() match {
      case Some(dialog) =>
        busyKicker.foreach(_.shutdownNow)
        busyKicker = None
        dialog.dismiss
      case None =>
    }
  }
  private def onKick(activity: SSHDActivity) = synchronized {
    busyDialog.get() match {
      case Some(dialog) =>
        addDataToBusyBuffer()
        activity.runOnUiThread(new Runnable { def run = dialog.setMessage(Html.fromHtml(busyBuffer.takeRight(busySize).mkString("<br/>"))) })
      case None =>
    }
  }
  private def addDataToBusyBuffer() = synchronized {
    var value = Random.nextInt
    val displayMask = 1 << 31;
    var data = (for (i <- 1 to 24) yield {
      var result = if ((value & displayMask) == 0) "0" else "1"
      value <<= 1
      if (i % 8 == 0)
        result += " "
      result
    }).mkString
    busyBuffer = busyBuffer :+ data
    if (busyBuffer.size > busySize)
      busyBuffer = busyBuffer.tail
  }
}
