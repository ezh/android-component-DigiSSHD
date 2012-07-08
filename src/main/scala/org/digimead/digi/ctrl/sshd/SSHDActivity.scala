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

import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import scala.actors.Actor
import scala.actors.Futures.future
import scala.collection.mutable.Subscriber
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.DActivity
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.base.AppControl
import org.digimead.digi.ctrl.lib.declaration.DConnection
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DPermission
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.dialog.FailedMarket
import org.digimead.digi.ctrl.lib.dialog.InstallControl
import org.digimead.digi.ctrl.lib.dialog.Preferences
import org.digimead.digi.ctrl.lib.dialog.Report
import org.digimead.digi.ctrl.lib.info.ComponentInfo
import org.digimead.digi.ctrl.lib.info.ComponentState
import org.digimead.digi.ctrl.lib.info.ExecutableInfo
import org.digimead.digi.ctrl.lib.log.FileLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.DMessage
import org.digimead.digi.ctrl.lib.message.IAmBusy
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.lib.message.IAmReady
import org.digimead.digi.ctrl.lib.message.IAmWarn
import org.digimead.digi.ctrl.lib.message.IAmYell
import org.digimead.digi.ctrl.lib.message.Origin.anyRefToOrigin
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.util.SyncVar
import org.digimead.digi.ctrl.lib.util.Version
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.info.TabActivity
import org.digimead.digi.ctrl.sshd.service.TabActivity
import org.digimead.digi.ctrl.sshd.session.TabActivity

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.ProgressDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.Html
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup.LayoutParams
import android.widget.Button
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TabHost
import android.widget.TabHost.OnTabChangeListener
import android.widget.TextView
import android.widget.ToggleButton

class SSHDActivity extends android.app.TabActivity with DActivity {
  implicit val dispatcher = org.digimead.digi.ctrl.sshd.Message.dispatcher
  private lazy val statusText = new WeakReference(findViewById(R.id.status).asInstanceOf[TextView])
  private lazy val buttonToggleStartStop1 = new WeakReference(findViewById(R.id.toggleStartStop1).asInstanceOf[ToggleButton])
  private lazy val buttonToggleStartStop2 = new WeakReference(findViewById(R.id.toggleStartStop2).asInstanceOf[ToggleButton])
  private lazy val buttonGrowShrink = new WeakReference(findViewById(R.id.buttonGrowShrink).asInstanceOf[ImageButton])
  private val onAppComponentStateHelper = new AtomicBoolean(false)
  SSHDActivity.focused = false
  private val activityStateUpdaterActor = new Actor {
    def act = {
      loop {
        react {
          case SSHDActivity.Message.UpdateAppComponentStatus(status) =>
            log.debug("receive state notification, new state: " + status)
            onAppComponentStateChanged(status)
          case message: AnyRef =>
            log.errorWhere("skip unknown message " + message.getClass.getName + ": " + message)
          case message =>
            log.errorWhere("skip unknown message " + message)
        }
      }
    }
  }
  activityStateUpdaterActor.start
  log.debug("alive")

  @Loggable
  override def onCreate(savedInstanceState: Bundle) = {
    SSHDActivity.activity = Some(this)
    // some times there is java.lang.IllegalArgumentException in scala.actors.threadpool.ThreadPoolExecutor
    // if we started actors from the singleton
    SSHDActivity.actor.start
    Preferences.DebugLogLevel.set(this)
    Preferences.DebugAndroidLogger.set(this)
    super.onCreate(savedInstanceState)
    onCreateExt(this)
    SSHDPreferences.initActivityPersistentOptions(this)
    setRequestedOrientation(AppComponent.Inner.preferredOrientation.get)
    setContentView(R.layout.main)

    val res = getResources() // Resource object to get Drawables
    val tabHost = getTabHost() // The activity TabHost
    var spec: TabHost#TabSpec = null // Resusable TabSpec for each tab
    var intent: Intent = null // Reusable Intent for each tab

    // Create an Intent to launch an Activity for the tab (to be reused)
    // Initialize a TabSpec for each tab and add it to the TabHost
    intent = new Intent().setClass(this, classOf[org.digimead.digi.ctrl.sshd.service.TabActivity])
    spec = tabHost.newTabSpec(classOf[org.digimead.digi.ctrl.sshd.service.TabActivity].getName()).setIndicator(getString(R.string.tab_name_service),
      res.getDrawable(R.drawable.ic_tab_service))
      .setContent(intent)
    tabHost.addTab(spec)

    intent = new Intent().setClass(this, classOf[session.TabActivity])
    spec = tabHost.newTabSpec(classOf[session.TabActivity].getName()).setIndicator(getString(R.string.tab_name_sessions),
      res.getDrawable(R.drawable.ic_tab_session))
      .setContent(intent)
    tabHost.addTab(spec)

    intent = new Intent().setClass(this, classOf[info.TabActivity])
    spec = tabHost.newTabSpec(classOf[info.TabActivity].getName()).setIndicator(getString(R.string.tab_name_information),
      res.getDrawable(R.drawable.ic_tab_info))
      .setContent(intent)
    tabHost.addTab(spec)

    tabHost.setOnTabChangedListener(new OnTabChangeListener() {
      def onTabChanged(tab: String) = tab match {
        case id if id == classOf[org.digimead.digi.ctrl.sshd.service.TabActivity].getName() =>
          log.info("activate tab " + getString(R.string.app_name_service))
          setTitle(getString(R.string.app_name_service))
          val edit = Common.getPublicPreferences(SSHDActivity.this).edit
          edit.putInt(SSHDPreferences.DOption.SelectedTab.tag, 0)
          edit.apply
        case id if id == classOf[session.TabActivity].getName() =>
          log.info("activate tab " + getString(R.string.app_name_sessions))
          setTitle(getString(R.string.app_name_sessions))
          val edit = Common.getPublicPreferences(SSHDActivity.this).edit
          edit.putInt(SSHDPreferences.DOption.SelectedTab.tag, 1)
          edit.apply
        case id if id == classOf[info.TabActivity].getName() =>
          log.info("activate tab " + getString(R.string.app_name_information))
          setTitle(getString(R.string.app_name_information))
          val edit = Common.getPublicPreferences(SSHDActivity.this).edit
          edit.putInt(SSHDPreferences.DOption.SelectedTab.tag, 2)
          edit.apply
        case id =>
          log.error("unknown tab " + tab)
      }
    })

    tabHost.setCurrentTab(Common.getPublicPreferences(this).
      getInt(SSHDPreferences.DOption.SelectedTab.tag, SSHDPreferences.DOption.SelectedTab.default.asInstanceOf[Int]))

    buttonToggleStartStop1.get.foreach(b => {
      b.setChecked(false)
      b.setEnabled(false)
    })
    buttonToggleStartStop2.get.foreach(b => {
      b.setChecked(false)
      b.setEnabled(false)
    })

    statusText.get.foreach(registerForContextMenu)

    SSHDActivity.ic_grow = Some(getResources.getDrawable(R.drawable.ic_grow))
    SSHDActivity.ic_shrink = Some(getResources.getDrawable(R.drawable.ic_shrink))
  }
  @Loggable
  override def onStart() {
    super.onStart()
    onStartExt(this, super.registerReceiver)
    SSHDActivity.busyCounter.set(0)
    SSHDActivity.busyDialog.unset()
    if (AppControl.Inner.isAvailable != Some(true))
      future {
        log.debug("try to bind " + DConstant.controlPackage)
        AppComponent.Inner.minVersionRequired(DConstant.controlPackage) match {
          case Some(minVersion) => try {
            val pm = getPackageManager()
            val pi = pm.getPackageInfo(DConstant.controlPackage, 0)
            val version = new Version(pi.versionName)
            log.debug(DConstant.controlPackage + " minimum version '" + minVersion + "' and current version '" + version + "'")
            if (version.compareTo(minVersion) == -1) {
              val message = Android.getString(this, "error_digicontrol_minimum_version").
                getOrElse("Required minimum version of DigiControl: %s. Current version is %s").format(minVersion, version)
              IAmYell(message)
              AppControl.Inner.bindStub("error_digicontrol_minimum_version", minVersion.toString, version.toString)
            } else {
              AppControl.Inner.bind(getApplicationContext)
            }
          } catch {
            case e: NameNotFoundException =>
              log.debug("DigiControl package " + DConstant.controlPackage + " not found")
          }
          case None =>
            AppControl.Inner.bind(getApplicationContext)
        }
      }
    AppComponent.Inner.state.subscribe(SSHDActivity.stateSubscriber)
  }
  @Loggable
  override def onResume() {
    super.onResume()
    onResumeExt(this)
    setRequestedOrientation(AppComponent.Inner.preferredOrientation.get)
    AppComponent.Inner.disableRotation()
    for {
      buttonToggleStartStop1 <- buttonToggleStartStop1.get
      buttonToggleStartStop2 <- buttonToggleStartStop2.get
      statusText <- statusText.get
      buttonGrowShrink <- buttonGrowShrink.get
      ic_grow <- SSHDActivity.ic_grow
      ic_shrink <- SSHDActivity.ic_shrink
    } {
      if (SSHDActivity.collapsed.get) {
        buttonGrowShrink.setBackgroundDrawable(ic_shrink)
        buttonToggleStartStop1.setVisibility(View.GONE)
        statusText.setVisibility(View.GONE)
        buttonToggleStartStop2.setVisibility(View.VISIBLE)
      } else {
        buttonGrowShrink.setBackgroundDrawable(ic_grow)
        buttonToggleStartStop2.setVisibility(View.GONE)
        statusText.setVisibility(View.VISIBLE)
        buttonToggleStartStop1.setVisibility(View.VISIBLE)
      }
    }
    buttonToggleStartStop1.get.foreach(b => {
      b.setChecked(false)
      b.setEnabled(false)
    })
    buttonToggleStartStop2.get.foreach(b => {
      b.setChecked(false)
      b.setEnabled(false)
    })
    SSHDActivity.consistent = true
    if (SSHDActivity.consistent && SSHDActivity.focused) {
      AppComponent.Inner.enableSafeDialogs
      future {
        // screen may occasionally rotate, delay in 1 second prevent to lock on transient orientation
        Thread.sleep(1000)
        if (SSHDActivity.consistent) {
          initializeOnCreate
          initializeOnResume
        }
      }
    }
  }
  @Loggable
  override def onPause() {
    SSHDActivity.consistent = false
    SSHDActivity.initializeOnResume.set(true)
    onPauseExt(this)
    super.onPause()
  }
  @Loggable
  override def onStop() {
    AppComponent.Inner.state.removeSubscription(SSHDActivity.stateSubscriber)
    onStopExt(this, true, super.unregisterReceiver)
    super.onStop()
  }
  @Loggable
  override def onDestroy() {
    SSHDActivity.initializeOnCreate.set(true)
    onDestroyExt(this)
    super.onDestroy()
  }
  /**
   * latest point before complete initialization
   * user interface already visible and it is alive
   * now we may start processes under the hood
   *
   * after initialization AppComponent.LazyInit.init do nothing
   */
  @Loggable
  override def onWindowFocusChanged(hasFocus: Boolean) = {
    super.onWindowFocusChanged(hasFocus)
    SSHDActivity.focused = hasFocus
    if (SSHDActivity.consistent && SSHDActivity.focused) {
      AppComponent.Inner.enableSafeDialogs
      buttonToggleStartStop1.get.foreach(_.setEnabled(true))
      buttonToggleStartStop2.get.foreach(_.setEnabled(true))
      future {
        // screen may occasionally rotate, delay in 1 second prevent to lock on transient orientation
        Thread.sleep(1000)
        if (SSHDActivity.consistent) {
          initializeOnCreate
          initializeOnResume
        }
      }
    } else {
      AppComponent.Inner.suspendSafeDialogs
      buttonToggleStartStop1.get.foreach(_.setEnabled(false))
      buttonToggleStartStop2.get.foreach(_.setEnabled(false))
    }
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) = {
    log.debug("create context menu")
    menu.setHeaderTitle(Android.getString(this, "history_menu_title").getOrElse("Event logs"))
    Android.getId(this, "ic_menu_event_logs", "drawable") match {
      case i if i != 0 =>
        menu.setHeaderIcon(i)
      case _ =>
    }
    // TODO
    //    menu.add(Menu.NONE, Android.getId(this, "history_menu_complex"), 1,
    //      Android.getString(this, "history_menu_complex").getOrElse("Complex"))
    menu.add(Menu.NONE, Android.getId(this, "history_menu_sessions"), 1,
      Android.getString(this, "history_menu_sessions").getOrElse("Sessions"))
    menu.add(Menu.NONE, Android.getId(this, "history_menu_activity"), 1,
      Android.getString(this, "history_menu_activity").getOrElse("Activity"))
  }
  @Loggable
  override def onContextItemSelected(menuItem: MenuItem): Boolean = {
    menuItem.getItemId match {
      case id if id == Android.getId(this, "history_menu_complex") =>
        false
      case id if id == Android.getId(this, "history_menu_activity") =>
        try {
          val intent = new Intent(DIntent.HostHistoryActivity)
          intent.putExtra(DIntent.DroneName, Android.getString(this, "app_name").getOrElse("DigiSSHD"))
          intent.putExtra(DIntent.DronePackage, this.getPackageName)
          startActivity(intent)
        } catch {
          case e =>
            IAmYell("Unable to open activity for " + DIntent.HostHistoryActivity, e)
            onAppComponentStateError(AppComponent.Inner.state.get.rawMessage)
        }
        true
      case id if id == Android.getId(this, "history_menu_sessions") =>
        try {
          val intent = new Intent(DIntent.HostHistorySessions)
          intent.putExtra(DIntent.DroneName, Android.getString(this, "app_name").getOrElse("DigiSSHD"))
          intent.putExtra(DIntent.DronePackage, this.getPackageName)
          startActivity(intent)
        } catch {
          case e =>
            IAmYell("Unable to open activity for " + DIntent.HostHistorySessions, e)
            onAppComponentStateError(AppComponent.Inner.state.get.rawMessage)
        }
        true
      case item =>
        log.fatal("skip unknown context item " + item)
        false
    }
  }
  @Loggable
  private def onInterfaceFilterUpdateBroadcast(intent: Intent) =
    IAmMumble("update interface filters")
  @Loggable
  private def onConnectionFilterUpdateBroadcast(intent: Intent) =
    IAmMumble("update connection filters")
  @Loggable
  private def onComponentUpdateBroadcast(intent: Intent) = future {
    log.trace("receive update broadcast " + intent.toUri(0))
    val state = intent.getParcelableExtra[ComponentState](DState.getClass.getName()).state
    state match {
      case DState.Active =>
        AppComponent.Inner.state.set(AppComponent.State(DState.Active))
      case DState.Passive =>
        AppComponent.Inner.state.set(AppComponent.State(DState.Passive))
      case _ =>
    }
  }
  @Loggable
  private def onMessageBroadcast(intent: Intent, droneName: String, dronePackage: String): Unit = {
    Option(intent.getParcelableArrayExtra(DIntent.Message)).foreach(_.foreach {
      case message: DMessage =>
        val logger = Logging.getRichLogger(message.origin.name)
        logger.info(dronePackage + "/" + message.message + " ts#" + message.ts)
        message.stash = Some(droneName)
        SSHDActivity.busyBuffer = SSHDActivity.busyBuffer.takeRight(SSHDActivity.busySize - 1) :+ (droneName + "/" + message.message)
        SSHDActivity.onUpdate(this)
      case broken =>
        log.error("recieve broken message: " + broken)
    })
  }
  @Loggable
  private def onAppComponentStateChanged(state: AppComponent.State): Unit = for {
    statusText <- statusText.get
  } {
    if (AppComponent.Inner.state.isBusy && !SSHDActivity.busyDialog.isSet)
      SSHDActivity.onBusy(this)
    val text = state match {
      case AppComponent.State(DState.Initializing, rawMessage, callback) =>
        log.debug("set status text to " + DState.Initializing)
        Some(Android.getCapitalized(SSHDActivity.this, "status_initializing").getOrElse("Initializing"))
      case AppComponent.State(DState.Passive, rawMessage, callback) =>
        log.debug("set status text to " + DState.Passive)
        SSHDActivity.running = false
        future { onAppPassive }
        Some(Android.getCapitalized(SSHDActivity.this, "status_ready").getOrElse("Ready"))
      case AppComponent.State(DState.Active, rawMessage, callback) =>
        log.debug("set status text to " + DState.Active)
        SSHDActivity.running = true
        future { onAppActive }
        Some(Android.getCapitalized(SSHDActivity.this, "status_active").getOrElse("Active"))
      case AppComponent.State(DState.Broken, rawMessage, callback) =>
        log.debug("set status text to " + DState.Broken + " with raw message: " + rawMessage)
        val message = if (rawMessage.length > 1)
          Android.getString(SSHDActivity.this, rawMessage.head, rawMessage.tail: _*).getOrElse(rawMessage.head)
        else if (rawMessage.length == 1)
          Android.getString(SSHDActivity.this, rawMessage.head).getOrElse(rawMessage.head)
        else
          Android.getString(SSHDActivity.this, "unknown").getOrElse("unknown")
        Some(Android.getCapitalized(SSHDActivity.this, "status_error").getOrElse("Error %s").format(message))
      case AppComponent.State(DState.Busy, rawMessage, callback) =>
        log.debug("set status text to " + DState.Busy)
        Some(Android.getCapitalized(SSHDActivity.this, "status_busy").getOrElse("Busy"))
      case AppComponent.State(DState.Unknown, rawMessage, callback) =>
        log.debug("skip notification with state DState.Unknown")
        None
      case state =>
        log.fatal("unknown state " + state)
        None
    }
    if (!AppComponent.Inner.state.isBusy && SSHDActivity.busyDialog.isSet)
      SSHDActivity.onReady(this)
    val uiWait = new SyncVar[Any]()
    runOnUiThread(new Runnable {
      def run = {
        uiWait.set({
          text.foreach(statusText.setText)
          buttonToggleStartStop1.get.foreach(_.setChecked(SSHDActivity.running))
          buttonToggleStartStop2.get.foreach(_.setChecked(SSHDActivity.running))
        })
      }
    })
    uiWait.get(DTimeout.shortest)
  }
  @Loggable
  private def onAppComponentStateError(reason: Seq[String]) = reason match {
    case Seq("error_digicontrol_minimum_version", _*) =>
      AppComponent.Inner.showDialogSafe(this, InstallControl.getClass.getName, InstallControl.getId(this))
    case Seq("error_digicontrol_not_found") =>
      AppComponent.Inner.showDialogSafe(this, InstallControl.getClass.getName, InstallControl.getId(this))
    case err =>
      log.error("component is trying to recover from error \"" + err + "\"")
      if (onAppComponentStateHelper.compareAndSet(false, true)) {
        log.debug("set recover in progress flag")
        AppComponent.Inner.showDialogSafe[AlertDialog](this, "dialog_recovery", () => {
          val dialog = new AlertDialog.Builder(this).
            setTitle(R.string.dialog_recovery_title).
            setMessage(R.string.dialog_recovery_message).
            setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
              def onClick(dialog: DialogInterface, whichButton: Int) =
                future { recover(true) }
            }).
            setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
              def onClick(dialog: DialogInterface, whichButton: Int) = {
                log.debug("reset recover in progress flag")
                onAppComponentStateHelper.set(false)
              }
            }).
            setIcon(android.R.drawable.ic_dialog_alert).
            create()
          dialog.show()
          dialog
        }, () => { future { recover(false) }; () })
      }
  }
  @Loggable
  private def onAppActive() {
    info.TabActivity.updateActiveInterfaces(DState.Active)
    service.TabActivity.updateComponents(DState.Active)
  }
  @Loggable
  private def onAppPassive() {
    info.TabActivity.updateActiveInterfaces(DState.Passive)
    service.TabActivity.updateComponents(DState.Passive)
  }
  @Loggable
  def onClickServiceFilterAdd(v: View) =
    future { service.TabActivity.getActivity.foreach(_.onClickServiceFilterAdd(v)) }
  @Loggable
  def onClickServiceFilterRemove(v: View) =
    future { service.TabActivity.getActivity.foreach(_.onClickServiceFilterRemove(v)) }
  @Loggable
  def onClickServiceReinstall(v: View) =
    future { service.TabActivity.getActivity.foreach(_.onClickServiceReinstall(v)) }
  @Loggable
  def onClickServiceReset(v: View) =
    future { service.TabActivity.getActivity.foreach(_.onClickServiceReset(v)) }
  @Loggable
  def onClickStartStop(v: View): Unit = future {
    val button = v.asInstanceOf[ToggleButton]
    AppComponent.Inner.state.get.value match {
      case DState.Active =>
        IAmBusy(SSHDActivity, Android.getString(this, "state_stopping_service").getOrElse("stopping service"))
        future {
          stop((componentState, serviceState, serviceBusy) => {
            log.debug("stoped, component state:" + componentState + ", service state:" + serviceState + ", service busy:" + serviceBusy)
            IAmReady(SSHDActivity, Android.getString(this, "state_stopped_service").getOrElse("stopped service"))
          })
        }
      case DState.Passive =>
        val networkPort = SSHDPreferences.NetworkPort.get(this)
        if (networkPort < 1024 && !SSHDPreferences.AsRoot.get(this)) {
          AppComponent.Inner.state.set(AppComponent.State(DState.Broken,
            Seq("error_privileged_port_unavailable", networkPort.toString),
            (a) => {
              org.digimead.digi.ctrl.sshd.service.option.NetworkPort.showDialog
              recover(true)
            }))
        } else {
          IAmBusy(SSHDActivity, Android.getString(this, "state_starting_service").getOrElse("starting service"))
          future {
            start((componentState, serviceState, serviceBusy) => {
              log.debug("started, component state:" + componentState + ", service state:" + serviceState + ", service busy:" + serviceBusy)
              IAmReady(SSHDActivity, Android.getString(this, "state_started_service").getOrElse("started service"))
            })
          }
        }
      case state =>
        val message = "Unable to move component to next finite state while is on an indeterminate position '" + state + "'"
        IAmWarn(message)
        runOnUiThread(new Runnable {
          def run {
            val state = buttonToggleStartStop1.get.map(_.isChecked()).getOrElse(false)
            buttonToggleStartStop1.get.foreach(_.setChecked(SSHDActivity.running))
            buttonToggleStartStop2.get.foreach(_.setChecked(SSHDActivity.running))
          }
        })
        onAppComponentStateError(AppComponent.Inner.state.get.rawMessage)
    }
  }
  @Loggable
  def onClickStatus(v: View) = future {
    AppComponent.Inner.state.get.onClickCallback(this)
  }
  @Loggable
  def onClickGrowShrink(v: View) = {
    if (SSHDActivity.collapsed.get) {
      for {
        buttonGrowShrink <- buttonGrowShrink.get
        ic_grow <- SSHDActivity.ic_grow
      } buttonGrowShrink.setBackgroundDrawable(ic_grow)
      SSHDActivity.actor ! SSHDActivity.Message.TakeMySpaceIfYouPlease
    } else {
      for {
        buttonGrowShrink <- buttonGrowShrink.get
        ic_shrink <- SSHDActivity.ic_shrink
      } buttonGrowShrink.setBackgroundDrawable(ic_shrink)
      SSHDActivity.actor ! SSHDActivity.Message.GiveMeMoreSpaceIfYouPlease
    }
  }
  @Loggable
  def onClickDigiControl(v: View) = {
    try {
      val intent = new Intent(DIntent.HostActivity)
      startActivity(intent)
    } catch {
      case e =>
        IAmYell("Unable to open activity for " + DIntent.HostActivity, e)
        AppComponent.Inner.showDialogSafe(this, InstallControl.getClass.getName, InstallControl.getId(this))
    }
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
      case R.id.menu_event_logs =>
        statusText.get.foreach(openContextMenu)
        true
      case R.id.menu_help =>
        SSHDCommon.showHelpDialog(this)
        true
      case R.id.menu_gplay =>
        try {
          val intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pname:" + DConstant.controlPackage))
          intent.addCategory(Intent.CATEGORY_BROWSABLE)
          startActivity(intent)
        } catch {
          case _ => AppComponent.Inner.showDialogSafe(this, FailedMarket.getClass.getName, FailedMarket.getId(this))
        }
        true
      case R.id.menu_report =>
        Report.submit(this)
        true
      case R.id.menu_control =>
        try {
          val intent = new Intent(DIntent.HostActivity)
          startActivity(intent)
        } catch {
          case _ => AppComponent.Inner.showDialogSafe(this, InstallControl.getClass.getName, InstallControl.getId(this))
        }
        true
      case R.id.menu_options =>
        try {
          val intent = new Intent(this, classOf[SSHDPreferences])
          startActivity(intent)
        } catch {
          case e =>
            log.error(e.getMessage, e)
        }
        true
      case _ =>
        super.onOptionsItemSelected(item)
    }
  @Loggable
  override def onCreateDialog(id: Int, args: Bundle): Dialog = {
    Option(onCreateDialogExt(this, id, args)).foreach(dialog => return dialog)
    id match {
      case id if id == SSHDActivity.Dialog.ComponentInfo =>
        log.debug("create dialog ComponentInfo " + id)
        val container = new ScrollView(this)
        val message = new TextView(this)
        container.addView(message, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        message.setMovementMethod(LinkMovementMethod.getInstance())
        message.setId(Int.MaxValue)
        new AlertDialog.Builder(this).
          setTitle(R.string.dialog_component_info_title).
          setView(container).
          setPositiveButton(android.R.string.ok, null).
          setIcon(R.drawable.ic_launcher).
          create()
      case id if id == SSHDActivity.Dialog.NewConnection =>
        log.debug("create dialog NewConnection " + id)
        new AlertDialog.Builder(this).
          setTitle(Android.getString(this, "ask_ctrl_newconnection_title").
            getOrElse("Allow access?")).
          setMessage(Android.getString(this, "ask_ctrl_newconnection_content").
            getOrElse("DigiSSHD needs your permission to start new session")).
          setPositiveButton(android.R.string.ok, null).
          setNegativeButton(android.R.string.cancel, null).
          create()
      case id =>
        super.onCreateDialog(id, args)
    }
  }
  @Loggable
  override def onPrepareDialog(id: Int, dialog: Dialog, args: Bundle) = {
    id match {
      case id if id == SSHDActivity.Dialog.ComponentInfo =>
        log.debug("prepare dialog ComponentInfo " + id)
        AppComponent.Inner.setDialogSafe(Some("SSHDActivity.Dialog.ComponentInfo"), Some(dialog))
        val message = dialog.findViewById(Int.MaxValue).asInstanceOf[TextView]
        val info = args.getParcelable("info").asInstanceOf[ExecutableInfo]
        val env = info.env.mkString("""<br/>""")
        val s = Android.getString(this, "dialog_component_info_message").get.format(info.name,
          info.description,
          info.project,
          info.license,
          info.version,
          info.state,
          info.port.getOrElse("-"),
          info.commandLine.map(_.mkString(" ")).getOrElse("-"),
          if (env.nonEmpty) env else "-")
        Linkify.addLinks(message, Linkify.ALL)
        message.setText(Html.fromHtml(s))
      case id if id == SSHDActivity.Dialog.NewConnection =>
        log.debug("prepare dialog NewConnection " + id)
        AppComponent.Inner.setDialogSafe(Some("SSHDActivity.Dialog.NewConnection"), Some(dialog))
        val ok = dialog.findViewById(android.R.id.button1).asInstanceOf[Button]
        onPrepareDialogStash.remove(id).map(_.asInstanceOf[Seq[(Uri, Bundle)]]).foreach {
          case requestSeq =>
            val (key, data) = requestSeq.head
            val requestTail = requestSeq.tail
            if (requestTail.nonEmpty)
              onPrepareDialogStash(id) = requestTail
            val component = Option(data.getParcelable[ComponentInfo]("component"))
            val connection = Option(data.getParcelable[DConnection]("connection"))
            val executable = Option(data.getParcelable[ExecutableInfo]("executable"))
            val processID = Option(data.getInt("processID")).asInstanceOf[Option[Int]]
            val total = Option(data.getInt("total")).asInstanceOf[Option[Int]]
            for {
              connection <- connection
              component <- component
              executable <- executable
            } {
              log.debug("ask user opinion about " + key)
              val ip = try {
                Some(InetAddress.getByAddress(BigInt(connection.remoteIP).toByteArray).getHostAddress)
              } catch {
                case e =>
                  log.warn(e.getMessage)
                  None
              }
              dialog.setTitle("Connection from " + ip.getOrElse(Android.getString(this, "unknown_source").getOrElse("unknown source")))
              ok.setOnClickListener(new OnClickListener() {
                def onClick(v: View) = {
                  log.info("permit connection")
                  data.putBoolean("result", true)
                  AppComponent.Inner.giveTheSign(key, data)
                  dialog.dismiss
                }
              })
              val cancel = dialog.findViewById(android.R.id.button2).asInstanceOf[Button]
              cancel.setOnClickListener(new OnClickListener() {
                def onClick(v: View) = {
                  log.info("deny connection")
                  data.putBoolean("result", false)
                  AppComponent.Inner.giveTheSign(key, data)
                  dialog.dismiss
                }
              })
            }
        }
      case _ =>
        if (!onPrepareDialogExt(this, id, dialog, args))
          super.onPrepareDialog(id, dialog, args)
    }
  }
  @Loggable
  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit =
    if (requestCode == session.FilterBlock.FILTER_REQUEST_ALLOW || requestCode == session.FilterBlock.FILTER_REQUEST_DENY)
      session.FilterBlock.onActivityResult(requestCode, resultCode, data)
  /*
   * recommended to start as future
   */
  @Loggable
  private def start(onFinish: (DState.Value, DState.Value, Boolean) => Unit): Boolean = synchronized {
    log.info("starting " + getPackageName)
    val stopFlag = new SyncVar[Boolean]()
    val startFlag = new SyncVar[Boolean]()

    // stop bridge that possibly in running state
    AppControl.Inner.callStop(getPackageName)() match {
      case true =>
        log.info(getPackageName + " stopped")
        stopFlag.set(true)
      case false =>
        // if fail to stop, then maybe there is something already running
        log.warn(getPackageName + " stop failed")
        stopFlag.set(true)
    }
    val result = if (stopFlag.get(DTimeout.longer).getOrElse(false)) {
      // change android service mode
      startService(new Intent(DIntent.HostService))
      AppControl.Inner.callStart(getPackageName)() match {
        case true =>
          log.info(getPackageName + " started")
          startFlag.set(true)
        case false =>
          log.warn(getPackageName + " start failed")
          startFlag.set(false)
      }
      startFlag.get(DTimeout.longer).getOrElse(false)
    } else
      false
    AppComponent.Inner.synchronizeStateWithICtrlHost((componentState, serviceState, serviceBusy) => onFinish(componentState, serviceState, serviceBusy))
    result
  }
  /*
   * recommended to start as future
   */
  @Loggable
  private def stop(onFinish: (DState.Value, DState.Value, Boolean) => Unit): Boolean = synchronized {
    log.info("stopping " + getPackageName)
    val stopFlag = new SyncVar[Boolean]()

    AppControl.Inner.callStop(getPackageName)() match {
      case true =>
        log.info(getPackageName + " stopped")
        stopFlag.set(true)
      case false =>
        // if fail to stop, then maybe there is something already running
        log.warn(getPackageName + " stop failed")
        stopFlag.set(true)
    }
    val result = stopFlag.get(DTimeout.long).getOrElse(false)
    if (result)
      AppComponent.Inner.state.set(AppComponent.State(DState.Passive))
    stopService(new Intent(DIntent.HostService))
    AppComponent.Inner.synchronizeStateWithICtrlHost((componentState, serviceState, serviceBusy) => onFinish(componentState, serviceState, serviceBusy))
    result
  }
  @Loggable
  private def recover(doJob: Boolean) = synchronized {
    if (doJob) {
      IAmBusy(SSHDActivity, Android.getString(this, "recovering").getOrElse("reset all components state"))
      AppComponent.Inner.state.set(AppComponent.State(DState.Initializing, Seq("try_to_recover")))
      AppControl.Inner.callReset(DConstant.controlPackage)()
      AppComponent.Inner.synchronizeStateWithICtrlHost((componentState, serviceState, serviceBusy) => {
        IAmReady(SSHDActivity, Android.getString(this, "recovered").getOrElse("reset completed"))
      })
    }
    log.debug("reset recover in progress flag")
    onAppComponentStateHelper.set(false)
  }
  @Loggable
  private def onSignRequest(intent: Intent): Unit = {
    try {
      val key = Uri.parse(intent.getDataString())
      key.getPath.split("""/""") match {
        case Array("", uuid, "connection") if uuid.matches("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""") =>
          askPermissionSomeoneConnect(key, intent.getExtras())
        case unknown =>
          log.error("unknown subject \"" + unknown + "\" for " + intent.toUri(0))
      }
    } catch {
      case e =>
        log.warn("receive broken sign intent " + intent.toUri(0), e)
    }
  }
  @Loggable
  private def askPermissionSomeoneConnect(key: Uri, data: Bundle): Unit = synchronized {
    val component = Option(data.getParcelable[ComponentInfo]("component"))
    val connection = Option(data.getParcelable[DConnection]("connection"))
    val executable = Option(data.getParcelable[ExecutableInfo]("executable"))
    val processID = Option(data.getInt("processID")).asInstanceOf[Option[Int]]
    val total = Option(data.getInt("total")).asInstanceOf[Option[Int]]
    for {
      connection <- connection
      component <- component
      processID <- processID
      executable <- executable
      total <- total
    } {
      val id = SSHDActivity.Dialog.NewConnection
      val stash = (key: Uri, data: Bundle)
      // add stash to onPrepareDialogStash(ControlActivity.Dialog.NewConnection.id)
      if (onPrepareDialogStash.isDefinedAt(id))
        onPrepareDialogStash(id) = onPrepareDialogStash(id).asInstanceOf[Seq[(Uri, Bundle)]] :+ stash
      else
        onPrepareDialogStash(id) = Seq(stash)
      val ip = try {
        Some(InetAddress.getByAddress(BigInt(connection.remoteIP).toByteArray).getHostAddress)
      } catch {
        case e =>
          log.warn(e.getMessage)
          None
      }
      IAmMumble("someone or something connect to " + component.name +
        " executable " + executable.name + " from " + ip.getOrElse(Android.getString(this, "unknown_source").getOrElse("unknown source")))
      AppComponent.Inner.showDialogSafe(this, "SSHDActivity.Dialog.NewConnection", SSHDActivity.Dialog.NewConnection)
    }
  }
  @Loggable
  private def initializeOnCreate(): Unit = {
    if (!SSHDActivity.initializeOnCreate.compareAndSet(true, false))
      return
    synchronized {
      log.debug("initializeOnCreate")
      IAmBusy(SSHDActivity, Android.getString(this, "state_loading_oncreate").getOrElse("device environment evaluation"))
      if (AppControl.isICtrlHostInstalled(this))
        IAmMumble("hive connection in progress")
      if (AppComponent.Inner.state.get.value == DState.Initializing)
        AppComponent.Inner.state.set(AppComponent.State(DState.Passive))
      SSHDActivity.addLazyInit
      Message.addLazyInit
      /*
       *  LazyInit before service available/unavailable
       */
      AppComponent.LazyInit.init
      /*
       *  LazyInit after service available/unavailable
       */
      info.TabActivity.addLazyInit
      session.TabActivity.addLazyInit
      service.TabActivity.addLazyInit
      IAmReady(SSHDActivity, Android.getString(this, "state_loaded_oncreate").getOrElse("device environment evaluated"))
    }
  }
  @Loggable
  private def initializeOnResume(): Unit = {
    if (!SSHDActivity.initializeOnResume.compareAndSet(true, false))
      return
    synchronized {
      log.debug("initializeOnResume")
      IAmBusy(SSHDActivity, Android.getString(this, "state_loading_onresume").getOrElse("component environment evaluation"))
      if (AppControl.isICtrlHostInstalled(this)) {
        AppControl.Inner.get(DTimeout.normal) match {
          case Some(s) =>
            IAmMumble("hive connection is successful")
          case None =>
            IAmMumble("hive connection is failed")
        }
      }
      /*
       *  LazyInit after service available/unavailable
       */
      SSHDActivity.addLazyInitOnResume
      session.TabActivity.addLazyInitOnResume
      AppComponent.LazyInit.init
      AppComponent.Inner.synchronizeStateWithICtrlHost((componentState, serviceState, serviceBusy) => {
        runOnUiThread(new Runnable {
          def run {
            buttonToggleStartStop1.get.foreach(_.setEnabled(true))
            buttonToggleStartStop2.get.foreach(_.setEnabled(true))
          }
        })
        AppComponent.Inner.enableRotation()
        Report.searchAndSubmit(this)
        if (AppControl.Inner.isAvailable == Some(false) &&
          AppComponent.Inner.state.get.value == DState.Broken &&
          AppComponent.Inner.state.get.onClickCallback != null &&
          this.getWindow.isActive)
          AppComponent.Inner.state.get.onClickCallback(this)
      })
      IAmReady(SSHDActivity, Android.getString(this, "state_loaded_onresume").getOrElse("component environment evaluated"))
    }
  }
  override def registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter): Intent = {
    registerReceiverExt(() => super.registerReceiver(receiver, filter),
      receiver, filter)
  }
  override def registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter, broadcastPermission: String, scheduler: Handler): Intent = {
    registerReceiverExt(() => super.registerReceiver(receiver, filter, broadcastPermission, scheduler),
      receiver, filter, broadcastPermission, scheduler)
  }
  override def unregisterReceiver(receiver: BroadcastReceiver): Unit = {
    unregisterReceiverExt(() => super.unregisterReceiver(receiver), receiver)
  }
}

object SSHDActivity extends Logging {
  @volatile private[sshd] var activity: Option[SSHDActivity] = None
  @volatile var ic_grow: Option[Drawable] = None
  @volatile var ic_shrink: Option[Drawable] = None
  private val initializeOnCreate = new AtomicBoolean(true)
  private val initializeOnResume = new AtomicBoolean(true)
  val locale = Locale.getDefault().getLanguage() + "_" + Locale.getDefault().getCountry()
  val localeLanguage = Locale.getDefault().getLanguage()
  lazy val info = AppComponent.Inner.getCachedComponentInfo(locale, localeLanguage).get
  val collapsed = new AtomicBoolean(false)
  @volatile private var running = false
  @volatile private var focused = false
  @volatile private var consistent = false
  // null - empty, None - dialog upcoming, Some - dialog in progress
  private val busyDialog = new SyncVar[Option[ProgressDialog]]()
  private val busyCounter = new AtomicInteger()
  private val busySize = 5
  @volatile private var busyBuffer = Seq[String]()
  //AppComponent state subscriber
  val stateSubscriber = new Subscriber[AppComponent.State, AppComponent.StateContainer#Pub] {
    def notify(pub: AppComponent.StateContainer#Pub, event: AppComponent.State) =
      activity.foreach(_.activityStateUpdaterActor ! Message.UpdateAppComponentStatus(event))
  }
  private val interfaceFilterUpdateReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = try {
      activity.foreach(activity => activity.onInterfaceFilterUpdateBroadcast(intent))
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  private val connectionFilterUpdateReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = try {
      activity.foreach(activity => activity.onConnectionFilterUpdateBroadcast(intent))
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  private val componentUpdateReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = try {
      val uri = Uri.parse(intent.getDataString())
      if (uri.getPath() == ("/org.digimead.digi.ctrl.sshd"))
        activity.foreach(activity => activity.onComponentUpdateBroadcast(intent))
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  private val sessionUpdateReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = try {
      log.debug("receive session update " + intent.toUri(0))
      session.SessionBlock.updateCursor
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  private val signReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = try {
      if (intent.getBooleanExtra("__private__", false) && isOrderedBroadcast) {
        abortBroadcast()
        activity.foreach(activity => activity.onSignRequest(intent))
      }
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  private val messageReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = try {
      val uri = Uri.parse(intent.getDataString())
      if (uri.getAuthority != "org.digimead.digi.ctrl.sshd")
        for {
          droneName <- Option(intent.getStringExtra(DIntent.DroneName))
          dronePackage <- Option(intent.getStringExtra(DIntent.DronePackage))
        } activity.foreach(activity => activity.onMessageBroadcast(intent, droneName, dronePackage))
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  private val applicationInstallerReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = try {
      val apkPackage = intent.getData.toString()
      log.debug("changed " + apkPackage)
      if (apkPackage == "package:" + DConstant.controlPackage) {
        future {
          val i = AppComponent.Context.foreach {
            case activity: Activity with DActivity =>
              IAmWarn("DigiControl (de)installed, restart DigiSSHD")
              AppComponent.Inner.state.set(AppComponent.State(DState.Initializing))
              Thread.sleep(DTimeout.normal)
              SSHDActivity.initializeOnResume.set(true)
              val i = activity.getBaseContext.getPackageManager.getLaunchIntentForPackage(activity.getPackageName())
              i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
              activity.startActivity(i)
          }
        }
      }
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  private val networkChangedReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = try {
      future { org.digimead.digi.ctrl.sshd.info.TabActivity.updateActiveInterfaces(AppComponent.Inner.state.get.value) }
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  Logging.addLogger(FileLogger)
  /*
   * initialize singletons
   */
  SSHDCommon
  session.SessionAdapter
  session.TabActivity
  service.TabActivity
  log.debug("alive")

  def addLazyInit = AppComponent.LazyInit("SSHDActivity initialize onCreate", 50) {
    activity.foreach {
      activity =>
        future { AppComponent.Inner.getCachedComponentInfo(locale, localeLanguage) }
        // register UpdateInterfaceFilter BroadcastReceiver
        val interfaceFilterUpdateFilter = new IntentFilter(DIntent.UpdateInterfaceFilter)
        interfaceFilterUpdateFilter.addDataScheme("code")
        interfaceFilterUpdateFilter.addDataAuthority(activity.getPackageName, null)
        activity.registerReceiver(interfaceFilterUpdateReceiver, interfaceFilterUpdateFilter)
        // register UpdateConnectionFilter BroadcastReceiver
        val connectionFilterUpdateFilter = new IntentFilter(DIntent.UpdateConnectionFilter)
        connectionFilterUpdateFilter.addDataScheme("code")
        connectionFilterUpdateFilter.addDataAuthority(activity.getPackageName, null)
        activity.registerReceiver(connectionFilterUpdateReceiver, connectionFilterUpdateFilter)
        // register ComponentFilter BroadcastReceiver
        val componentUpdateFilter = new IntentFilter()
        componentUpdateFilter.addAction(DIntent.Update)
        componentUpdateFilter.addDataScheme("code")
        componentUpdateFilter.addDataAuthority(DConstant.ComponentAuthority, null)
        activity.registerReceiver(componentUpdateReceiver, componentUpdateFilter, DPermission.Base, null)
        // register SessionUpdate BroadcastReceiver
        val sessionUpdateFilter = new IntentFilter(DIntent.Update)
        sessionUpdateFilter.addDataScheme("code")
        sessionUpdateFilter.addDataAuthority(DConstant.SessionAuthority, null)
        activity.registerReceiver(sessionUpdateReceiver, sessionUpdateFilter, DPermission.Base, null)
        // register sign BroadcastReceiver
        val signFilter = new IntentFilter(DIntent.SignRequest)
        signFilter.addDataScheme("sign")
        signFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY)
        activity.registerReceiver(signReceiver, signFilter, DPermission.Base, null)
        // register message BroadcastReceiver
        val messageFilter = new IntentFilter(DIntent.Message)
        messageFilter.addDataScheme("code")
        activity.registerReceiver(messageReceiver, messageFilter, DPermission.Base, null)
        // register ApplicationInstaller BroadcastReceiver
        val applicationInstallerFilter = new IntentFilter()
        applicationInstallerFilter.addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
        applicationInstallerFilter.addAction(android.content.Intent.ACTION_PACKAGE_CHANGED)
        applicationInstallerFilter.addAction(android.content.Intent.ACTION_PACKAGE_INSTALL)
        applicationInstallerFilter.addAction(android.content.Intent.ACTION_PACKAGE_REPLACED)
        applicationInstallerFilter.addDataScheme("package")
        activity.registerReceiver(applicationInstallerReceiver, applicationInstallerFilter)
        // register NetworkChanged
        val networkChangedFilter = new IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION)
        activity.registerReceiver(networkChangedReceiver, networkChangedFilter)
    }
  }
  def addLazyInitOnResume = AppComponent.LazyInit("SSHDActivity initialize onResume", 1000) {
    future {
      activity.foreach {
        activity =>
          AppControl.Inner.callListPendingConnections(activity.getPackageName)() match {
            case Some(pendingConnections) =>
              IAmMumble(pendingConnections.size + " pending connection(s)")
              pendingConnections.foreach(connectionIntent => try {
                log.debug("process sign request " + connectionIntent.getDataString)
                val restoredIntent = connectionIntent.cloneFilter
                val data = new Bundle
                data.putInt("processID", connectionIntent.getIntExtra("processID", 0))
                data.putInt("total", connectionIntent.getIntExtra("total", 0))
                (for {
                  component <- Common.unparcelFromArray[ComponentInfo](connectionIntent.getByteArrayExtra("component"))
                  connection <- Common.unparcelFromArray[DConnection](connectionIntent.getByteArrayExtra("connection"))
                  executable <- Common.unparcelFromArray[ExecutableInfo](connectionIntent.getByteArrayExtra("executable"))
                } yield {
                  data.putParcelable("component", component)
                  data.putParcelable("connection", connection)
                  data.putParcelable("executable", executable)
                  restoredIntent.replaceExtras(data)
                  activity.onSignRequest(restoredIntent)
                }) getOrElse (log.fatal("broken ListPendingConnections intent detected: " + connectionIntent))
              } catch {
                case e =>
                  log.error(e.getMessage, e)
              })
            case None =>
          }
      }
    }
  }

  val actor = new Actor {
    def act = {
      loop {
        react {
          case IAmBusy(origin, message, ts) =>
            log.info("receive message IAmBusy from " + origin)
            reply({
              busyCounter.incrementAndGet
              busyBuffer = busyBuffer.takeRight(busySize - 1) :+ message
              activity.foreach(onUpdate)
              AppComponent.Inner.state.set(AppComponent.State(DState.Busy))
              log.debug("return from message IAmBusy from " + origin)
            })
          case IAmMumble(origin, message, callback, ts) =>
            log.info("receive message IAmMumble from " + origin)
            busyBuffer = busyBuffer.takeRight(busySize - 1) :+ message
            activity.foreach(onUpdate)
            log.debug("return from message IAmMumble from " + origin)
          case IAmWarn(origin, message, callback, ts) =>
            log.info("receive message IAmWarn from " + origin)
            busyBuffer = busyBuffer.takeRight(busySize - 1) :+ message
            activity.foreach(onUpdate)
            log.debug("return from message IAmWarn from " + origin)
          case IAmYell(origin, message, stacktrace, callback, ts) =>
            log.info("receive message IAmYell from " + origin)
            busyBuffer = busyBuffer.takeRight(busySize - 1) :+ message
            activity.foreach(onUpdate)
            log.debug("return from message IAmYell from " + origin)
          case IAmReady(origin, message, ts) =>
            log.info("receive message IAmReady from " + origin)
            if (busyCounter.get > 0)
              busyCounter.decrementAndGet
            busyBuffer = busyBuffer.takeRight(busySize - 1) :+ message
            activity.foreach(onUpdate)
            AppComponent.Inner.state.freeBusy
            log.debug("return from message IAmReady from " + origin)
          case Message.GiveMeMoreSpaceIfYouPlease =>
            onMessageCollapse
          case Message.TakeMySpaceIfYouPlease =>
            onMessageExpand
          case message: AnyRef =>
            log.errorWhere("skip unknown message " + message.getClass.getName + ": " + message)
          case message =>
            log.errorWhere("skip unknown message " + message)
        }
      }
    }
  }

  def isRunning() = running
  def isConsistent() = consistent
  @Loggable
  private def onBusy(activity: SSHDActivity): Unit = {
    if (!busyDialog.isSet) {
      AppComponent.Inner.disableRotation()
      busyDialog.set(AppComponent.Inner.showDialogSafeWait[ProgressDialog](activity, "progress_dialog", () =>
        if (busyCounter.get > 0) {
          busyBuffer.lastOption.foreach(msg => busyBuffer = Seq(msg))
          val dialog = new ProgressDialog(activity)
          dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
          dialog.setTitle("Please wait...")
          dialog.setOnShowListener(new DialogInterface.OnShowListener {
            def onShow(dialog: DialogInterface) = future {
              // additional guard
              Thread.sleep(DTimeout.shortest)
              if (busyCounter.get <= 0) try {
                dialog.dismiss
              } catch {
                case e =>
                  log.warn(e.getMessage, e)
              }
            }
          })
          dialog.setMessage(busyBuffer.mkString("\n"))
          dialog.setCancelable(false)
          dialog.show
          dialog
        } else
          null, () => {
        busyDialog.unset()
        busyCounter.set(0)
      }))
    }
  }
  @Loggable
  private def onReady(activity: SSHDActivity): Unit = {
    if (busyDialog.isSet)
      busyDialog.get.foreach {
        dialog =>
          dialog.dismiss
          busyDialog.unset()
          AppComponent.Inner.enableRotation()
      }
  }
  private def onUpdate(activity: SSHDActivity): Unit = busyDialog.get(0).foreach(_.foreach {
    dialog =>
      activity.runOnUiThread(new Runnable {
        def run = {
          dialog.setMessage(busyBuffer.mkString("\n"))
        }
      })
  })
  @Loggable
  private def onMessageCollapse(): Unit = for {
    activity <- activity
    buttonToggleStartStop1 <- activity.buttonToggleStartStop1.get
    buttonToggleStartStop2 <- activity.buttonToggleStartStop2.get
    statusText <- activity.statusText.get
  } {
    log.debug("collapse")
    activity.runOnUiThread(new Runnable {
      def run {
        buttonToggleStartStop1.setVisibility(View.GONE)
        statusText.setVisibility(View.GONE)
        buttonToggleStartStop2.setVisibility(View.VISIBLE)
        SSHDActivity.collapsed.set(true)
      }
    })
  }
  @Loggable
  private def onMessageExpand(): Unit = for {
    activity <- activity
    buttonToggleStartStop1 <- activity.buttonToggleStartStop1.get
    buttonToggleStartStop2 <- activity.buttonToggleStartStop2.get
    statusText <- activity.statusText.get
  } {
    log.debug("expand")
    activity.runOnUiThread(new Runnable {
      def run {
        buttonToggleStartStop2.setVisibility(View.GONE)
        statusText.setVisibility(View.VISIBLE)
        buttonToggleStartStop1.setVisibility(View.VISIBLE)
        SSHDActivity.collapsed.set(false)
      }
    })
  }
  object Dialog {
    lazy val NewConnection = AppComponent.Context.map(a => Android.getId(a, "new_connection")).getOrElse(0)
    lazy val ComponentInfo = AppComponent.Context.map(a => Android.getId(a, "component_info")).getOrElse(0)
  }
  object Message {
    object GiveMeMoreSpaceIfYouPlease
    object TakeMySpaceIfYouPlease
    case class UpdateAppComponentStatus(status: AppComponent.State)
  }
}
