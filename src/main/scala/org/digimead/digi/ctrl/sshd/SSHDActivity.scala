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

import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.Locale

import scala.actors.Futures.future
import scala.actors.Actor
import scala.collection.mutable.Subscriber
import scala.ref.WeakReference

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
import org.digimead.digi.ctrl.lib.dialog.Report
import org.digimead.digi.ctrl.lib.info.ComponentInfo
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
import org.digimead.digi.ctrl.lib.message.IAmYell
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.util.SyncVar
import org.digimead.digi.ctrl.lib.Activity
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.info.TabActivity
import org.digimead.digi.ctrl.sshd.service.TabActivity
import org.digimead.digi.ctrl.sshd.session.TabActivity

import android.app.AlertDialog
import android.app.Dialog
import android.app.ProgressDialog
import android.content.pm.ActivityInfo
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
import android.view.View.OnClickListener
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.widget.TabHost.OnTabChangeListener
import android.widget.Button
import android.widget.ScrollView
import android.widget.TabHost
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton

class SSHDActivity extends android.app.TabActivity with Activity {
  implicit val dispatcher = org.digimead.digi.ctrl.sshd.Message.dispatcher
  private lazy val statusText = new WeakReference(findViewById(R.id.status).asInstanceOf[TextView])
  private lazy val buttonToggleStartStop = new WeakReference(findViewById(R.id.toggleStartStop).asInstanceOf[ToggleButton])
  if (SSHDActivity.DEBUG)
    Logging.addLogger(Seq(AndroidLogger, FileLogger))
  else
    Logging.addLogger(FileLogger)
  SSHDActivity.focused = false
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
    spec = tabHost.newTabSpec(classOf[org.digimead.digi.ctrl.sshd.service.TabActivity].getName()).setIndicator(getString(R.string.app_name_service),
      res.getDrawable(R.drawable.ic_tab_service))
      .setContent(intent)
    tabHost.addTab(spec)

    intent = new Intent().setClass(this, classOf[session.TabActivity])
    spec = tabHost.newTabSpec(classOf[session.TabActivity].getName()).setIndicator(getString(R.string.app_name_session),
      res.getDrawable(R.drawable.ic_tab_session))
      .setContent(intent)
    tabHost.addTab(spec)

    intent = new Intent().setClass(this, classOf[info.TabActivity])
    spec = tabHost.newTabSpec(classOf[info.TabActivity].getName()).setIndicator(getString(R.string.app_name_information),
      res.getDrawable(R.drawable.ic_tab_info))
      .setContent(intent)
    tabHost.addTab(spec)

    tabHost.setOnTabChangedListener(new OnTabChangeListener() {
      def onTabChanged(tab: String) = tab match {
        case id if id == classOf[org.digimead.digi.ctrl.sshd.service.TabActivity].getName() =>
          log.info("activate tab " + getString(R.string.app_name_service))
          setTitle("%s: %s".format(getString(R.string.app_name), getString(R.string.app_name_service)))
        case id if id == classOf[session.TabActivity].getName() =>
          log.info("activate tab " + getString(R.string.app_name_session))
          setTitle("%s: %s".format(getString(R.string.app_name), getString(R.string.app_name_session)))
        case id if id == classOf[info.TabActivity].getName() =>
          log.info("activate tab " + getString(R.string.app_name_information))
          setTitle("%s: %s".format(getString(R.string.app_name), getString(R.string.app_name_information)))
        case id =>
          log.error("unknown tab " + tab)
      }
    })

    buttonToggleStartStop.get.foreach(b => {
      b.setChecked(false)
      b.setEnabled(false)
    })

    tabHost.setCurrentTab(2)
  }
  @Loggable
  override def onStart() {
    super.onStart()
  }
  @Loggable
  override def onResume() {
    AppComponent.Inner.disableRotation()
    super.onResume()
    runOnUiThread(new Runnable {
      def run {
        buttonToggleStartStop.get.foreach(b => {
          b.setChecked(false)
          b.setEnabled(false)
        })
      }
    })
    SSHDActivity.consistent = true
    AppComponent.Inner.state.subscribe(SSHDActivity.stateSubscriber)
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
    super.onPause()
    AppComponent.Inner.state.removeSubscription(SSHDActivity.stateSubscriber)
    SSHDActivity.consistent = false
    SSHDActivity.initializeOnResume.set(true)
  }
  @Loggable
  override def onDestroy() {
    SSHDActivity.initializeOnCreate.set(true)
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
      buttonToggleStartStop.get.foreach { button =>
        if (!SSHDActivity.initializeOnResume.get) {
          log.debug("enable toggleStartStop button")
          button.setEnabled(true)
        }
      }
      future {
        // screen may occasionally rotate, delay in 1 second prevent to lock on transient orientation
        Thread.sleep(1000)
        if (SSHDActivity.consistent) {
          initializeOnCreate
          initializeOnResume
        }
      }
    } else {
      AppComponent.Inner.disableSafeDialogs
      buttonToggleStartStop.get.foreach { button => button.setEnabled(false) }
    }
  }
  @Loggable
  private def onPrivateBroadcast(intent: Intent) = {
    intent.getAction() match {
      case DIntent.Update =>
      // we don't interested in different AppComponent DIntent.Update events
      case _ =>
        log.error("unknown private broadcast intent " + intent + " with action " + intent.getAction)
    }
  }
  @Loggable
  private def onPublicBroadcast(intent: Intent) = {
    intent.getAction match {
      case DIntent.Update =>
      // we don't interested in different AppComponent DIntent.Update events
      case _ =>
        log.error("unknown public broadcast intent " + intent + " with action " + intent.getAction)
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
    val state = DState(intent.getIntExtra(DState.getClass.getName(), -1))
    service.TabActivity.UpdateComponents(state)
    info.TabActivity.updateActiveInterfaces(state)
    state match {
      case DState.Active =>
        AppComponent.Inner.state.set(AppComponent.State(DState.Active))
      case DState.Passive =>
        AppComponent.Inner.state.set(AppComponent.State(DState.Passive))
      case _ =>
    }
  }
  @Loggable
  private def onMessageBroadcast(intent: Intent) = future {
    val message = intent.getParcelableExtra[DMessage](DIntent.Message)
    val logger = Logging.getLogger(message.origin.name)
    logger.info(message.getClass.getName.split("""\.""").last + " " + message.message + " <- " + message.origin.packageName)
    SSHDActivity.busyBuffer = SSHDActivity.busyBuffer.takeRight(SSHDActivity.busySize - 1) :+ message.message
    SSHDActivity.onUpdate(this)
  }
  @Loggable
  private def onAppComponentStateChanged(state: AppComponent.State): Unit = for {
    statusText <- statusText.get
  } {
    if (SSHDActivity.busyCounter.get > 0 && !SSHDActivity.busyDialog.isSet)
      SSHDActivity.onBusy(this)
    state match {
      case AppComponent.State(DState.Initializing, message, callback) =>
        log.debug("set status text to " + DState.Initializing)
        val text = Android.getCapitalized(SSHDActivity.this, "status_initializing").getOrElse("Initializing")
        runOnUiThread(new Runnable { def run = statusText.setText(text) })
      case AppComponent.State(DState.Passive, message, callback) =>
        log.debug("set status text to " + DState.Passive)
        val text = Android.getCapitalized(SSHDActivity.this, "status_ready").getOrElse("Ready")
        SSHDActivity.running = false
        runOnUiThread(new Runnable { def run = statusText.setText(text) })
      case AppComponent.State(DState.Active, message, callback) =>
        log.debug("set status text to " + DState.Active)
        val text = Android.getCapitalized(SSHDActivity.this, "status_active").getOrElse("Active")
        SSHDActivity.running = true
        runOnUiThread(new Runnable { def run = statusText.setText(text) })
      case AppComponent.State(DState.Broken, rawMessage, callback) =>
        log.debug("set status text to " + DState.Broken)
        val message = if (rawMessage != null)
          Android.getString(SSHDActivity.this, rawMessage).getOrElse(rawMessage)
        else
          Android.getString(SSHDActivity.this, "unknown").getOrElse("unknown")
        val text = Android.getCapitalized(SSHDActivity.this, "status_error").getOrElse("Error %s").format(message)
        runOnUiThread(new Runnable { def run = statusText.setText(text) })
      case AppComponent.State(DState.Busy, message, callback) =>
        log.debug("set status text to " + DState.Busy)
        val text = Android.getCapitalized(SSHDActivity.this, "status_busy").getOrElse("Busy")
        runOnUiThread(new Runnable { def run = statusText.setText(text) })
      case AppComponent.State(DState.Unknown, message, callback) =>
        log.debug("skip notification with state DState.Unknown")
      case state =>
        log.fatal("unknown state " + state)
    }
    runOnUiThread(new Runnable { def run = buttonToggleStartStop.get.foreach(_.setChecked(SSHDActivity.running)) })
    if (SSHDActivity.busyCounter.get == 0 && SSHDActivity.busyDialog.isSet)
      SSHDActivity.onReady(this)
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
    AppComponent.Inner.state.get.code match {
      case DState.Active =>
        IAmBusy(SSHDActivity, Android.getString(this, "state_stopping_services").getOrElse("stopping services"))
        future {
          stop((s) => {
            IAmReady(SSHDActivity, Android.getString(this, "state_stopped_services").getOrElse("stopped services"))
          })
        }
      case DState.Passive =>
        IAmBusy(SSHDActivity, Android.getString(this, "state_starting_services").getOrElse("starting services"))
        future {
          start((s) => {
            IAmReady(SSHDActivity, Android.getString(this, "state_started_services").getOrElse("started services"))
          })
        }
      case state =>
        val message = "Unable to move component to next finite state while is on an indeterminate position '" + state + "'"
        IAmWarn(message)
        runOnUiThread(new Runnable {
          def run = {
            buttonToggleStartStop.get.foreach(_.setChecked(SSHDActivity.running))
            Toast.makeText(SSHDActivity.this, message, Toast.LENGTH_SHORT).show()
          }
        })
    }
  }
  @Loggable
  def onClickStatus(v: View) = future {
    AppComponent.Inner.state.get.onClickCallback(this)
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
        AppComponent.Inner.showDialogSafe[AlertDialog](this, () => {
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
        true
      case R.id.menu_gplay =>
        try {
          val intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pname:" + DConstant.controlPackage))
          intent.addCategory(Intent.CATEGORY_BROWSABLE)
          startActivity(intent)
        } catch {
          case _ => AppComponent.Inner.showDialogSafe(this, FailedMarket.getId(this))
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
          case _ => AppComponent.Inner.showDialogSafe(this, InstallControl.getId(this))
        }
        true
      case _ =>
        super.onOptionsItemSelected(item)
    }
  @Loggable
  override def onCreateDialog(id: Int, data: Bundle): Dialog = {
    Option(Common.onCreateDialog(id, this)).foreach(dialog => return dialog)
    id match {
      case id if id == SSHDActivity.Dialog.ComponentInfo =>
        log.debug("create dialog ComponentInfo " + SSHDActivity.Dialog.NewConnection)
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
        log.debug("create dialog NewConnection " + SSHDActivity.Dialog.NewConnection)
        new AlertDialog.Builder(this).
          setTitle(Android.getString(this, "ask_ctrl_newconnection_title").
            getOrElse("Allow access?")).
          setMessage(Android.getString(this, "ask_ctrl_newconnection_content").
            getOrElse("DigiControl needs your permission to start new session")).
          setPositiveButton(android.R.string.ok, null).
          setNegativeButton(android.R.string.cancel, null).
          create()
      case id =>
        super.onCreateDialog(id, data)
    }
  }
  @Loggable
  override def onPrepareDialog(id: Int, dialog: Dialog, args: Bundle) = {
    super.onPrepareDialog(id, dialog)
    id match {
      case id if id == SSHDActivity.Dialog.ComponentInfo =>
        log.debug("prepare dialog ComponentInfo " + SSHDActivity.Dialog.NewConnection)
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
      case id =>
    }
    super.onPrepareDialog(id, dialog, args)
  }
  @Loggable
  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit =
    if (requestCode == session.FilterBlock.FILTER_REQUEST_ALLOW || requestCode == session.FilterBlock.FILTER_REQUEST_DENY)
      session.FilterBlock.onActivityResult(requestCode, resultCode, data)
  /*
   * recommended to start as future
   */
  @Loggable
  private def start(onFinish: (DState.Value) => Unit): Boolean = synchronized {
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
    AppComponent.Inner.synchronizeStateWithICtrlHost((s) => onFinish(s))
    result
  }
  /*
   * recommended to start as future
   */
  @Loggable
  private def stop(onFinish: (DState.Value) => Unit): Boolean = synchronized {
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
    AppComponent.Inner.synchronizeStateWithICtrlHost((s) => onFinish(s))
    result
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
      IAmMumble("Someone or something connect to " + component.name +
        " executable " + executable.name + " from " + ip.getOrElse(Android.getString(this, "unknown_source").getOrElse("unknown source")))
      AppComponent.Inner.showDialogSafe(this, SSHDActivity.Dialog.NewConnection)
    }
  }
  @Loggable
  private def initializeOnCreate(): Unit = {
    if (!SSHDActivity.initializeOnCreate.compareAndSet(true, false))
      return
    IAmBusy(SSHDActivity, Android.getString(this, "state_loading_oncreate").getOrElse("device environment evaluation"))
    AppControl.Inner.bind(this)
    if (AppComponent.Inner.state.get.code == DState.Initializing)
      AppComponent.Inner.state.set(AppComponent.State(DState.Passive))
    SSHDActivity.addLazyInit
    info.TabActivity.addLazyInit
    session.TabActivity.addLazyInit
    service.TabActivity.addLazyInit
    initializeOnResume
    IAmReady(SSHDActivity, Android.getString(this, "state_loaded_oncreate").getOrElse("device environment evaluated"))
  }
  @Loggable
  private def initializeOnResume(): Unit = {
    if (!SSHDActivity.initializeOnResume.compareAndSet(true, false))
      return
    IAmBusy(SSHDActivity, Android.getString(this, "state_loading_onresume").getOrElse("component environment evaluation"))
    SSHDActivity.addLazyInitOnResume
    session.TabActivity.addLazyInitOnResume
    AppComponent.LazyInit.init
    AppComponent.Inner.synchronizeStateWithICtrlHost((s) => {
      runOnUiThread(new Runnable {
        def run = {
          log.debug("enable toggleStartStop button")
          findViewById(R.id.toggleStartStop).asInstanceOf[ToggleButton].setEnabled(true)
        }
      })
      AppComponent.Inner.enableRotation()
    })
    IAmReady(SSHDActivity, Android.getString(this, "state_loaded_onresume").getOrElse("component environment evaluated"))
    future { Report.searchAndSubmit(this) }
  }
}

object SSHDActivity extends Actor with Logging {
  val DEBUG = false
  @volatile private[sshd] var activity: Option[SSHDActivity] = None
  private val initializeOnCreate = new AtomicBoolean(true)
  private val initializeOnResume = new AtomicBoolean(true)
  val locale = Locale.getDefault().getLanguage() + "_" + Locale.getDefault().getCountry()
  val localeLanguage = Locale.getDefault().getLanguage()
  lazy val info = AppComponent.Inner.getCachedComponentInfo(locale, localeLanguage).get
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
      activity.foreach(_.onAppComponentStateChanged(event))
  }
  // broadcast receivers
  private val privateReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = try {
      if (intent.getBooleanExtra("__private__", false))
        activity.foreach(activity => activity.onPrivateBroadcast(intent))
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  private val publicReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = try {
      if (!intent.getBooleanExtra("__private__", false))
        activity.foreach(activity => activity.onPublicBroadcast(intent))
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
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
        activity.foreach(activity => activity.onMessageBroadcast(intent))
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
            case activity: Activity =>
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
      future { org.digimead.digi.ctrl.sshd.info.TabActivity.updateActiveInterfaces(AppComponent.Inner.state.get.code) }
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  if (SSHDActivity.DEBUG) {
    Logging.isTraceEnabled = false
  } else {
    Logging.isTraceEnabled = false
    Logging.isDebugEnabled = false
  }
  /*
   * initialize singletons
   */
  session.SessionAdapter
  session.TabActivity
  service.TabActivity
  // start actor
  start
  log.debug("alive")

  def addLazyInit = AppComponent.LazyInit("SSHDActivity initialize onCreate", 50) {
    activity.foreach {
      activity =>
        future { AppComponent.Inner.getCachedComponentInfo(locale, localeLanguage) }
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
  def act = {
    loop {
      react {
        case IAmBusy(origin, message) =>
          reply({
            busyCounter.incrementAndGet
            busyBuffer = busyBuffer.takeRight(busySize - 1) :+ message
            activity.foreach(onUpdate)
            AppComponent.Inner.state.set(AppComponent.State(DState.Busy))
            busyDialog.get(DTimeout.long)
          })
        case IAmMumble(origin, message, callback) =>
          busyBuffer = busyBuffer.takeRight(busySize - 1) :+ message
          activity.foreach(onUpdate)
        case IAmWarn(origin, message, callback) =>
          busyBuffer = busyBuffer.takeRight(busySize - 1) :+ message
          activity.foreach(onUpdate)
        case IAmYell(origin, message, stacktrace, callback) =>
          busyBuffer = busyBuffer.takeRight(busySize - 1) :+ message
          activity.foreach(onUpdate)
        case IAmReady(origin, message) =>
          busyCounter.decrementAndGet
          busyBuffer = busyBuffer.takeRight(busySize - 1) :+ message
          activity.foreach(onUpdate)
          AppComponent.Inner.state.freeBusy
          if (!AppComponent.Inner.state.isBusy) {
            busyDialog.put(null)
            busyDialog.unset()
          }
        case message: AnyRef =>
          log.errorWhere("skip unknown message " + message.getClass.getName + ": " + message)
        case message =>
          log.errorWhere("skip unknown message " + message)
      }
    }
  }
  def isRunning() = running
  @Loggable
  private def onBusy(activity: SSHDActivity): Unit = {
    if (!busyDialog.isSet) {
      AppComponent.Inner.disableRotation()
      busyDialog.set(AppComponent.Inner.showDialogSafeWait[ProgressDialog](activity, () =>
        if (busyCounter.get > 0) {
          busyBuffer.lastOption.foreach(msg => busyBuffer = Seq(msg))
          ProgressDialog.show(activity, "Please wait...", busyBuffer.mkString("\n"), true)
        } else
          null))
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
  object Dialog {
    lazy val NewConnection = AppComponent.Context.map(a => Android.getId(a, "new_connection")).getOrElse(0)
    lazy val ComponentInfo = AppComponent.Context.map(a => Android.getId(a, "component_info")).getOrElse(0)
  }
}
