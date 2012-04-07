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
import java.util.Locale

import scala.actors.Futures.future
import scala.actors.Actor
import scala.concurrent.SyncVar
import scala.ref.WeakReference
import scala.util.Random

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.base.AppService
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DPermission
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.dialog.FailedMarket
import org.digimead.digi.ctrl.lib.dialog.Report
import org.digimead.digi.ctrl.lib.info.ExecutableInfo
import org.digimead.digi.ctrl.lib.log.AndroidLogger
import org.digimead.digi.ctrl.lib.log.FileLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.Origin.anyRefToOrigin
import org.digimead.digi.ctrl.lib.message.DMessage
import org.digimead.digi.ctrl.lib.message.IAmBusy
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.lib.message.IAmReady
import org.digimead.digi.ctrl.lib.message.IAmWarn
import org.digimead.digi.ctrl.lib.util.Android
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
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.text.Html
import android.view.ViewGroup.LayoutParams
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TabHost.OnTabChangeListener
import android.widget.ScrollView
import android.widget.TabHost
import android.widget.TextView
import android.widget.ToggleButton

class SSHDActivity extends android.app.TabActivity with Activity {
  private lazy val statusText = new WeakReference(findViewById(R.id.status).asInstanceOf[TextView])
  private lazy val toggleStartStop = findViewById(R.id.toggleStartStop).asInstanceOf[ToggleButton]
  if (true)
    Logging.addLogger(Seq(AndroidLogger, FileLogger))
  else
    Logging.addLogger(FileLogger)
  log.debug("alive")

  /** Called when the activity is first created. */
  @Loggable
  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)
    SSHDActivity.activity = Some(this)

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
    SSHDActivity.addLazyInit
    info.TabActivity.addLazyInit
    session.TabActivity.addLazyInit
    service.TabActivity.addLazyInit
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
        AppActivity.Inner.state.set(AppActivity.State(DState.Passive))
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
    SSHDActivity.focused = false
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
        AppActivity.Inner.state.set(AppActivity.State(DState.Passive))
        AppActivity.LazyInit.init
        System.gc
        Thread.sleep(500)
        IAmReady(SSHDActivity, Android.getString(this, "state_loaded_internal_routines").getOrElse("loaded internals"))
      }
  }
  @Loggable
  def onPrivateBroadcast(context: Context, intent: Intent) = {
    intent.getAction() match {
      case DIntent.Update =>
        onAppActivityStateChanged()
      case DIntent.Message =>
        val message = intent.getParcelableExtra[DMessage](DIntent.Message)
        val logger = Logging.getLogger(message.origin.name)
        logger.info(message.getClass.getName.split("""\.""").last + " " + message.message + " from " + message.origin.packageName)
      case _ =>
        log.error("unknown private broadcast intent " + intent + " with action " + intent.getAction)
    }
  }
  @Loggable
  def onPublicBroadcast(context: Context, intent: Intent) = {
    intent.getAction match {
      case DIntent.Update =>
        onAppActivityStateChanged()
      case DIntent.Message =>
        val message = intent.getParcelableExtra[DMessage](DIntent.Message)
        val logger = Logging.getLogger(message.origin.name)
        logger.info(message.getClass.getName.split("""\.""").last + " " + message.message + " from " + message.origin.packageName)
      case _ =>
        log.error("unknown public broadcast intent " + intent + " with action " + intent.getAction)
    }
  }
  @Loggable
  def onInterfaceFilterUpdateBroadcast(context: Context, intent: Intent) =
    if (intent.getData.getAuthority == getPackageName)
      IAmMumble("update interface filters")
  @Loggable
  def onConnectionFilterUpdateBroadcast(context: Context, intent: Intent) =
    if (intent.getData.getAuthority == getPackageName)
      IAmMumble("update connection filters")
  @Loggable
  private def onAppActivityStateChanged(): Unit = for {
    statusText <- statusText.get
  } {
    AppActivity.Inner.state.get match {
      case AppActivity.State(DState.Initializing, message, callback) =>
        log.debug("set status text to " + DState.Initializing)
        statusText.setText(Android.getCapitalized(this, "status_initializing").getOrElse("Initializing"))
      case AppActivity.State(DState.Passive, message, callback) =>
        log.debug("set status text to " + DState.Passive)
        statusText.setText(Android.getCapitalized(this, "status_ready").getOrElse("Ready"))
        toggleStartStop.setChecked(false)
      case AppActivity.State(DState.Active, message, callback) =>
        log.debug("set status text to " + DState.Active)
        statusText.setText(Android.getCapitalized(this, "status_active").getOrElse("Active"))
        toggleStartStop.setChecked(true)
      case AppActivity.State(DState.Broken, rawMessage, callback) =>
        log.debug("set status text to " + DState.Broken)
        val message = if (rawMessage != null)
          Android.getString(this, rawMessage).getOrElse(rawMessage)
        else
          Android.getString(this, "unknown").getOrElse("unknown")
        statusText.setText(Android.getCapitalized(this, "status_error").getOrElse("Error: %s").format(message))
        if (message != null)
          statusText.setText(Android.getCapitalized(this, "status_error").getOrElse("Error: %s").format(message))
      case AppActivity.State(DState.Busy, message, callback) =>
        log.debug("set status text to " + DState.Busy)
        statusText.setText(Android.getCapitalized(this, "status_busy").getOrElse("Busy"))
      case AppActivity.State(DState.Unknown, message, callback) =>
        log.debug("skip notification with state DState.Unknown")
      case state =>
        log.fatal("unknown state " + state)
    }
  }
  @Loggable
  def onClickServiceFilterAdd(v: View) =
    service.TabActivity.getActivity.foreach(_.onClickServiceFilterAdd(v))
  @Loggable
  def onClickServiceFilterRemove(v: View) =
    service.TabActivity.getActivity.foreach(_.onClickServiceFilterRemove(v))
  @Loggable
  def onClickServiceReinstall(v: View) =
    service.TabActivity.getActivity.foreach(_.onClickServiceReinstall(v))
  @Loggable
  def onClickServiceReset(v: View) =
    service.TabActivity.getActivity.foreach(_.onClickServiceReset(v))
  @Loggable
  def onClickStartStop(v: View): Unit = {
    val button = v.asInstanceOf[ToggleButton]
    AppActivity.Inner.state.get.code match {
      case DState.Active =>
        IAmBusy(SSHDActivity, Android.getString(this, "state_stopping_services").getOrElse("stopping services"))
        future {
          stop()
          IAmReady(SSHDActivity, Android.getString(this, "state_stopped_services").getOrElse("stopped services"))
        }
      case DState.Passive =>
        IAmBusy(SSHDActivity, Android.getString(this, "state_starting_services").getOrElse("starting services"))
        future {
          start()
          IAmReady(SSHDActivity, Android.getString(this, "state_started_services").getOrElse("started services"))
        }
      case state =>
        IAmWarn("unable to change component state while in " + state)
      // TODO add IAmWarn Toast.makeText(...
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
  override def onCreateDialog(id: Int, args: Bundle): Dialog = {
    id match {
      case id if id == Android.getId(this, "dialog_ComponentInfo") =>
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
      case id =>
        super.onCreateDialog(id)
    }
  }
  @Loggable
  override def onPrepareDialog(id: Int, dialog: Dialog, args: Bundle) = {
    id match {
      case id if id == Android.getId(this, "dialog_ComponentInfo") =>
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
      case id =>
    }
    super.onPrepareDialog(id, dialog, args)
  }
  /*
   * recommended to start as future
   */
  @Loggable
  private def start(): Boolean = synchronized {
    log.info("starting " + getPackageName)
    val stopFlag = new SyncVar[Boolean]()
    val startFlag = new SyncVar[Boolean]()

    // stop bridge that possibly in running state
    AppService.Inner ! AppService.Message.Stop(getPackageName, {
      case true =>
        log.info(getPackageName + " stopped")
        stopFlag.set(true)
      case false =>
        // if fail to stop, then maybe there is something already running
        log.warn(getPackageName + " stop failed")
        stopFlag.set(true)
    })
    val result = if (stopFlag.get(DTimeout.normal).getOrElse(false)) {
      // change android service mode
      startService(new Intent(DIntent.HostService))
      AppService.Inner ! AppService.Message.Start(getPackageName, {
        case true =>
          log.info(getPackageName + " started")
          startFlag.set(true)
        case false =>
          log.warn(getPackageName + " start failed")
          startFlag.set(false)
      })
      startFlag.get(DTimeout.normal).getOrElse(false)
    } else
      false
    // update AppState
    if (result)
      AppActivity.Inner.state.set(AppActivity.State(DState.Active))
    result
  }
  /*
   * recommended to start as future
   */
  @Loggable
  private def stop(): Boolean = synchronized {
    log.info("stopping " + getPackageName)
    val stopFlag = new SyncVar[Boolean]()

    AppService.Inner ! AppService.Message.Stop(getPackageName, {
      case true =>
        log.info(getPackageName + " stopped")
        stopFlag.set(true)
      case false =>
        // if fail to stop, then maybe there is something already running
        log.warn(getPackageName + " stop failed")
        stopFlag.set(true)
    })
    val result = stopFlag.get(DTimeout.normal).getOrElse(false)
    if (result)
      AppActivity.Inner.state.set(AppActivity.State(DState.Passive))
    stopService(new Intent(DIntent.HostService))
    result
  }
}

object SSHDActivity extends Actor with Logging {
  @volatile private[sshd] var activity: Option[SSHDActivity] = None
  lazy val info = AppActivity.Inner.getCachedComponentInfo(locale, localeLanguage).get
  val locale = Locale.getDefault().getLanguage() + "_" + Locale.getDefault().getCountry()
  val localeLanguage = Locale.getDefault().getLanguage()
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
  private val privateReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = try {
      if (intent.getBooleanExtra("__private__", false))
        activity.foreach(activity => activity.onPrivateBroadcast(context, intent))
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  private val publicReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = try {
      if (!intent.getBooleanExtra("__private__", false))
        activity.foreach(activity => activity.onPublicBroadcast(context, intent))
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  private val interfaceFilterUpdateReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = try {
      activity.foreach(activity => activity.onInterfaceFilterUpdateBroadcast(context, intent))
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  private val connectionFilterUpdateReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = try {
      activity.foreach(activity => activity.onConnectionFilterUpdateBroadcast(context, intent))
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  start
  def addLazyInit = AppActivity.LazyInit("main activity onCreate logic") {
    activity.foreach {
      activity =>
        // register BroadcastReceiver
        val genericFilter = new IntentFilter()
        genericFilter.addAction(DIntent.Message)
        genericFilter.addAction(DIntent.Update)
        genericFilter.addAction(DIntent.UpdateInterfaceFilter)
        activity.registerReceiver(privateReceiver, genericFilter, DPermission.Base, null)
        activity.registerReceiver(publicReceiver, genericFilter)
        // register UpdateInterfaceFilter BroadcastReceiver
        val interfaceFilterUpdateFilter = new IntentFilter(DIntent.UpdateInterfaceFilter)
        interfaceFilterUpdateFilter.addDataScheme("code")
        activity.registerReceiver(interfaceFilterUpdateReceiver, interfaceFilterUpdateFilter)
        // register UpdateConnectionFilter BroadcastReceiver
        val connectionFilterUpdateFilter = new IntentFilter(DIntent.UpdateConnectionFilter)
        connectionFilterUpdateFilter.addDataScheme("code")
        activity.registerReceiver(connectionFilterUpdateReceiver, connectionFilterUpdateFilter)
        // prepare environment
        AppActivity.Inner ! AppActivity.Message.PrepareEnvironment(activity, true, true, (success) => {
          if (AppActivity.Inner.state.get.code != DState.Broken)
            if (success) {
              AppService.Inner ! AppService.Message.Status(activity.getPackageName, {
                case Right(componentState) =>
                  val appState = componentState.state match {
                    case DState.Active =>
                      AppActivity.State(DState.Active)
                    case DState.Broken =>
                      AppActivity.State(DState.Broken, "native failed")
                    case _ =>
                      AppActivity.State(DState.Passive)
                  }
                  AppActivity.Inner.state.set(appState)
                case Left(error) =>
                  val appState = AppActivity.State(DState.Broken, error)
                  AppActivity.Inner.state.set(appState)
              })
            } else
              AppActivity.State(DState.Broken, "environment preparation failed")
        })
    }
  }
  log.debug("alive")

  def act = {
    loop {
      react {
        case msg: IAmBusy =>
          busyCounter.incrementAndGet
          activity.foreach(onBusy)
        case msg: IAmReady =>
          busyCounter.decrementAndGet
          onReady
        case null => // kick it
          activity.foreach(onKick)
        case message: AnyRef =>
          log.error("skip unknown message " + message.getClass.getName + ": " + message)
        case message =>
          log.error("skip unknown message " + message)
      }
    }
  }
  @Loggable
  private def onBusy(activity: SSHDActivity) = synchronized {
    AppActivity.Inner.state.set(AppActivity.State(DState.Busy))
    busyDialog.get() match {
      case Some(dialog) =>
      case None =>
        future {
          val message = Android.getString(activity, "decoder_not_implemented").getOrElse("decoder out of future pack ;-)")
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
    AppActivity.Inner.state.freeBusy
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
