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
import scala.collection.mutable.ArrayBuffer
import scala.ref.WeakReference
import scala.util.Random

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.base.AppService
import org.digimead.digi.ctrl.lib.declaration.DConnection
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DPermission
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.dialog.FailedMarket
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
import android.widget.TabHost.OnTabChangeListener
import android.widget.Button
import android.widget.ScrollView
import android.widget.TabHost
import android.widget.TextView
import android.widget.ToggleButton

class SSHDActivity extends android.app.TabActivity with Activity {
  private lazy val statusText = new WeakReference(findViewById(R.id.status).asInstanceOf[TextView])
  private lazy val buttonToggleStartStop = new WeakReference(findViewById(R.id.toggleStartStop).asInstanceOf[ToggleButton])
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
    super.onResume()
    Android.disableRotation(this)
    runOnUiThread(new Runnable {
      def run {
        buttonToggleStartStop.get.foreach(b => {
          b.setChecked(false)
          b.setEnabled(false)
        })
      }
    })
    AppService.Inner.bind(this)
    SSHDActivity.consistent = true
    if (SSHDActivity.consistent && SSHDActivity.focused)
      future {
        initializeOnCreate
        initializeOnResume
      }
  }

  @Loggable
  override def onPause() {
    super.onPause()
    SSHDActivity.consistent = false
    SSHDActivity.focused = false
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
   * after initialization AppActivity.LazyInit.init do nothing
   */
  @Loggable
  override def onWindowFocusChanged(hasFocus: Boolean) = {
    super.onWindowFocusChanged(hasFocus)
    SSHDActivity.focused = hasFocus
    if (SSHDActivity.consistent && SSHDActivity.focused)
      AppActivity.Inner.enableSafeDialogs
    else
      AppActivity.Inner.disableSafeDialogs
    if (SSHDActivity.consistent && SSHDActivity.focused)
      future {
        initializeOnCreate
        initializeOnResume
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
    IAmMumble("update interface filters")
  @Loggable
  def onConnectionFilterUpdateBroadcast(context: Context, intent: Intent) =
    IAmMumble("update connection filters")
  @Loggable
  def onComponentUpdateBroadcast(context: Context, intent: Intent) = {
    try {
      val uri = Uri.parse(intent.getDataString())
      if (uri.getPath() == ("/" + getPackageName)) {
        log.trace("receive update broadcast " + intent.toUri(0))
        val state = DState(intent.getIntExtra(DState.getClass.getName(), -1))
        service.TabActivity.UpdateComponents(state)
        info.TabActivity.UpdateActiveInterfaces(state)
        state match {
          case DState.Active =>
            AppActivity.Inner.state.set(AppActivity.State(DState.Active))
          case DState.Passive =>
            AppActivity.Inner.state.set(AppActivity.State(DState.Passive))
          case _ =>
        }
      }
    } catch {
      case _ =>
        log.warn("receive broken intent " + intent.toUri(0))
    }
  }
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
        buttonToggleStartStop.get.foreach(_.setChecked(false))
      case AppActivity.State(DState.Active, message, callback) =>
        log.debug("set status text to " + DState.Active)
        statusText.setText(Android.getCapitalized(this, "status_active").getOrElse("Active"))
        buttonToggleStartStop.get.foreach(_.setChecked(true))
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
        runOnUiThread(new Runnable {
          def run {
            buttonToggleStartStop.get.foreach {
              button =>
                button.setChecked(button.isChecked)
            }
          }
        })
      // TODO add IAmWarn Toast.makeText(...
    }
  }
  @Loggable
  def onClickStatus(v: View) = future {
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
        AppActivity.Inner.showDialogSafe[AlertDialog](this, () => {
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
          val intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pname:" + DConstant.marketPackage))
          intent.addCategory(Intent.CATEGORY_BROWSABLE)
          startActivity(intent)
        } catch {
          case _ => AppActivity.Inner.showDialogSafe(this, FailedMarket.getId(this))
        }
        true
      case R.id.menu_report =>
        Report.submit(this)
        true
      case R.id.menu_quit =>
        AppActivity.Inner.showDialogSafe[AlertDialog](this, () => {
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
              val ip = InetAddress.getByAddress(BigInt(connection.remoteIP).toByteArray)
              dialog.setTitle("Connection from " + ip)
              ok.setOnClickListener(new OnClickListener() {
                def onClick(v: View) = {
                  log.info("permit connection")
                  data.putBoolean("result", true)
                  AppActivity.Inner.giveTheSign(key, data)
                  dialog.dismiss
                }
              })
              val cancel = dialog.findViewById(android.R.id.button2).asInstanceOf[Button]
              cancel.setOnClickListener(new OnClickListener() {
                def onClick(v: View) = {
                  log.info("deny connection")
                  data.putBoolean("result", false)
                  AppActivity.Inner.giveTheSign(key, data)
                  dialog.dismiss
                }
              })
            }
        }
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
    AppActivity.Inner.synchronizeStateWithICtrlHost
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
    AppActivity.Inner.synchronizeStateWithICtrlHost
    result
  }
  @Loggable
  private def onIntentSignRequest(intent: Intent): Unit = {
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
      val ip = InetAddress.getByAddress(BigInt(connection.remoteIP).toByteArray)
      IAmMumble("Someone or something connect to " + component.name +
        " executable " + executable.name + " from IP " + ip.getHostAddress)
      AppActivity.Inner.showDialogSafe(this, SSHDActivity.Dialog.NewConnection)
    }
  }
  @Loggable
  private def initializeOnCreate(): Unit = {
    if (!SSHDActivity.initializeOnCreate.compareAndSet(true, false))
      return
    IAmBusy(SSHDActivity, Android.getString(this, "state_loading_oncreate").getOrElse("loading on create logic"))
    if (AppActivity.Inner.state.get.code == DState.Initializing)
      AppActivity.Inner.state.set(AppActivity.State(DState.Passive))
    SSHDActivity.addLazyInit
    info.TabActivity.addLazyInit
    session.TabActivity.addLazyInit
    initializeOnResume
    IAmReady(SSHDActivity, Android.getString(this, "state_loaded_oncreate").getOrElse("loaded on create logic"))
  }
  @Loggable
  private def initializeOnResume(): Unit = {
    if (!SSHDActivity.initializeOnResume.compareAndSet(true, false))
      return
    IAmBusy(SSHDActivity, Android.getString(this, "state_loading_onresume").getOrElse("loading on resume logic"))
    AppActivity.LazyInit.init
    AppActivity.Inner.synchronizeStateWithICtrlHost
    AppService.Inner ! AppService.Message.ListPendingConnections(getPackageName, {
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
            onIntentSignRequest(restoredIntent)
          }) getOrElse (log.fatal("broken ListPendingConnections intent detected: " + connectionIntent))
        } catch {
          case e =>
            log.error(e.getMessage, e)
        })
      case None =>
    })
    runOnUiThread(new Runnable { def run = buttonToggleStartStop.get.foreach(_.setEnabled(true)) })
    Android.enableRotation(this)
    IAmReady(SSHDActivity, Android.getString(this, "state_loaded_onresume").getOrElse("loaded on resume logic"))
  }
}

object SSHDActivity extends Actor with Logging {
  @volatile private[sshd] var activity: Option[SSHDActivity] = None
  private val initializeOnCreate = new AtomicBoolean(true)
  private val initializeOnResume = new AtomicBoolean(true)
  lazy val info = AppActivity.Inner.getCachedComponentInfo(locale, localeLanguage).get
  val locale = Locale.getDefault().getLanguage() + "_" + Locale.getDefault().getCountry()
  val localeLanguage = Locale.getDefault().getLanguage()
  @volatile private var focused = false
  @volatile private var consistent = false
  @volatile private var screenOrientation = 0
  // null - empty, None - dialog upcoming, Some - dialog in progress
  private val busyDialog = new SyncVar[Option[ProgressDialog]]()
  private val busyCounter = new AtomicInteger()
  private val busySize = 5
  private val busyBuffer = ArrayBuffer[String]()
  private val busyBufferSize = 32
  private val busyKickerRate = 100
  // broadcast receivers
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
  private val componentUpdateReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = try {
      activity.foreach(activity => activity.onComponentUpdateBroadcast(context, intent))
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  private val sessionUpdateReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) = {
      log.debug("receive session update " + intent.toUri(0))
      future { session.SessionBlock.updateCursor }
    }
  }
  private val privateSignReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) =
      if (intent.getBooleanExtra("__private__", false) && isOrderedBroadcast) {
        abortBroadcast()
        activity.foreach(activity => activity.onIntentSignRequest(intent))
      }
  }
  /*
   * initialize singletons
   */
  session.SessionAdapter
  session.TabActivity
  service.TabActivity
  // start actor
  start
  AppActivity.LazyInit("SSHDActivity initialize once") {
    activity.foreach {
      activity =>
        AppActivity.Inner ! AppActivity.Message.PrepareEnvironment(activity, true, true, (success) => {
          if (AppActivity.Inner.state.get.code != DState.Broken)
            if (success)
              AppActivity.Inner.synchronizeStateWithICtrlHost
            else
              AppActivity.State(DState.Broken, "environment preparation failed")
        })
    }
  }
  for (i <- 0 to busyBufferSize)
    addDataToBusyBuffer
  log.debug("alive")

  def addLazyInit = AppActivity.LazyInit("SSHDActivity initialize onCreate", 50) {
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
        activity.registerReceiver(privateSignReceiver, signFilter, DPermission.Base, null)
    }
  }
  def act = {
    loop {
      react {
        case msg: IAmBusy =>
          reply({
            busyCounter.incrementAndGet
            activity.foreach(onBusy)
            busyDialog.get(DTimeout.normal)
          })
        case msg: IAmReady =>
          busyCounter.decrementAndGet
          activity.foreach(onReady)
        case message: AnyRef =>
          log.errorWhere("skip unknown message " + message.getClass.getName + ": " + message)
        case message =>
          log.errorWhere("skip unknown message " + message)
      }
    }
  }
  @Loggable
  private def onBusy(activity: SSHDActivity): Unit = {
    AppActivity.Inner.state.set(AppActivity.State(DState.Busy))
    if (!busyDialog.isSet) {
      val message = Android.getString(activity, "decoder_not_implemented").getOrElse("decoder out of future pack ;-)")
      busyDialog.set(AppActivity.Inner.showDialogSafeWait[ProgressDialog](activity, () =>
        if (busyCounter.get > 0) {
          val text = Seq(message) ++ (for (i <- 1 until busySize)
            yield busyBuffer(Random.nextInt(busyBufferSize)))
          ProgressDialog.show(activity, "Please wait...", text.mkString("\n"), true)
        } else
          null))
      future {
        Thread.sleep(busyKickerRate)
        while (busyDialog.get(0).flatMap(d => d) match {
          case Some(dialog) =>
            activity.runOnUiThread(new Runnable {
              def run = dialog.setMessage((for (i <- 1 to busySize)
                yield busyBuffer(Random.nextInt(busyBufferSize))).mkString("\n"))
            })
            true
          case None =>
            false
        }) Thread.sleep(busyKickerRate)
      }
    }
  }
  @Loggable
  private def onReady(activity: SSHDActivity): Unit = {
    if (busyDialog.isSet)
      busyDialog.get.foreach {
        dialog =>
          dialog.dismiss
          busyDialog.unset()
      }
    AppActivity.Inner.state.freeBusy
  }
  private def addDataToBusyBuffer() = {
    var value = Random.nextInt
    val displayMask = 1 << 31;
    var data = (for (i <- 1 to 24) yield {
      var result = if ((value & displayMask) == 0) "0" else "1"
      value <<= 1
      if (i % 8 == 0)
        result += " "
      result
    }).mkString
    busyBuffer.append(data)
  }
  object Dialog {
    lazy val NewConnection = AppActivity.Context.map(a => Android.getId(a, "new_connection")).getOrElse(0)
    lazy val ComponentInfo = AppActivity.Context.map(a => Android.getId(a, "component_info")).getOrElse(0)
  }
}
