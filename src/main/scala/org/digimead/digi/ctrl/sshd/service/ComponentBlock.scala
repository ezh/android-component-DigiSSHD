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

import java.util.ArrayList
import java.util.Arrays
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

import scala.actors.Futures
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.androidext.SafeDialog
import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.block.Block
import org.digimead.digi.ctrl.lib.block.Level
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.info.ExecutableInfo
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.message.IAmYell
import org.digimead.digi.ctrl.lib.util.SyncVar
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDService
import org.digimead.digi.ctrl.sshd.ext.SSHDDialog

import com.commonsware.cwac.merge.MergeAdapter

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.text.ClipboardManager
import android.text.Html
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class ComponentBlock(val context: Context)(implicit @transient val dispatcher: Dispatcher) extends Block[ComponentBlock.Item] with Logging {
  ComponentBlock.block = Some(this)
  Futures.future { ComponentBlock.updateItems }

  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    Option(ComponentBlock.header).foreach(mergeAdapter.addView)
    Option(ComponentBlock.adapter).foreach(mergeAdapter.addAdapter)
  }
  @Loggable
  def items = for (i <- 0 until ComponentBlock.adapter.getCount) yield ComponentBlock.adapter.getItem(i)
  @Loggable
  def onListItemClick(l: ListView, v: View, item: ComponentBlock.Item) = for {
    fragment <- TabContent.fragment
    dialog <- ComponentBlock.Dialog.info
  } item.updatedExecutableInfo {
    info =>
      if (dialog.isShowing)
        AnyBase.runOnUiThread { dialog.updateContent(info) }
      else {
//        val bundle = new Bundle
//        bundle.putParcelable("info", info)
        SafeDialog(fragment.getSherlockActivity, dialog, () => dialog).target(R.id.main_topPanel).show()
      }
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo, item: ComponentBlock.Item) {
    log.debug("create context menu for " + item.value)
    menu.setHeaderTitle(item.value)
    menu.setHeaderIcon(XResource.getId(context, "ic_launcher", "drawable"))
    menu.add(Menu.NONE, XResource.getId(context, "block_component_jump_to_project"), 1,
      XResource.getString(context, "block_component_jump_to_project").getOrElse("Jump to project"))
    menu.add(Menu.NONE, XResource.getId(context, "block_component_copy_command_line"), 2,
      XResource.getString(context, "block_component_copy_command_line").getOrElse("Copy command line"))
    menu.add(Menu.NONE, XResource.getId(context, "block_component_copy_info"), 3,
      XResource.getString(context, "block_component_copy_info").getOrElse("Copy information"))
  }
  @Loggable
  override def onContextItemSelected(menuItem: MenuItem, item: ComponentBlock.Item): Boolean = {
    menuItem.getItemId match {
      case id if id == XResource.getId(context, "block_component_jump_to_project") =>
        try {
          val execInfo = item.executableInfo()
          val intent = new Intent(Intent.ACTION_VIEW, Uri.parse(execInfo.project))
          intent.addCategory(Intent.CATEGORY_BROWSABLE)
          context.startActivity(intent)
        } catch {
          case e =>
            IAmYell("Unable to open link: " + item.value, e)
        }
        true
      case id if id == XResource.getId(context, "block_component_copy_command_line") =>
        Futures.future {
          try {
            val execInfo = item.executableInfo()
            val message = XResource.getString(context, "block_component_copy_command_line").
              getOrElse("Copy command line to clipboard")
            AnyBase.runOnUiThread {
              try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
                clipboard.setText(execInfo.commandLine.map(_.mkString(" ")).getOrElse("-"))
                Toast.makeText(context, message, DConstant.toastTimeout).show()
              } catch {
                case e =>
                  IAmYell("Unable to copy to clipboard command line for: " + item.value, e)
              }
            }
          } catch {
            case e =>
              IAmYell("Unable to copy to clipboard command line for: " + item.value, e)
          }
        }
        true
      case id if id == XResource.getId(context, "block_component_copy_info") =>
        Futures.future {
          try {
            val execInfo = item.executableInfo()
            val env = execInfo.env.mkString("""<br/>""")
            val string = XResource.getString(context, "dialog_component_info_message").get.format(execInfo.name,
              execInfo.description,
              execInfo.project,
              execInfo.license,
              execInfo.version,
              execInfo.state,
              execInfo.port.getOrElse("-"),
              execInfo.commandLine.map(_.mkString(" ")).getOrElse("-"),
              if (env.nonEmpty) env else "-")
            val message = XResource.getString(context, "block_component_copy_info").
              getOrElse("Copy information to clipboard")
            AnyBase.runOnUiThread {
              try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
                clipboard.setText(Html.fromHtml(string).toString)
                Toast.makeText(context, message, DConstant.toastTimeout).show()
              } catch {
                case e =>
                  IAmYell("Unable to copy to clipboard command line for: " + item.value, e)
              }
            }
          } catch {
            case e =>
              IAmYell("Unable to copy to clipboard command line for: " + item.value, e)
          }
        }
        true
      case item =>
        log.fatal("unknown item " + item)
        false
    }
  }

  @Loggable
  def updateComponentsState(state: DState.Value) = synchronized {
    /*   state match {
      case DState.Active =>
        items.foreach(i => i.view.get.foreach(view => i.state(true)))
      case DState.Passive =>
        items.foreach(i => i.view.get.foreach(view => i.state(false)))
      case _ =>
    }*/
  }
}

object ComponentBlock extends Logging {
  /** ComponentBlock instance */
  @volatile private var block: Option[ComponentBlock] = None
  /** ComponentBlock adapter */
  private[service] lazy val adapter = AppComponent.Context match {
    case Some(context) =>
      new ComponentBlock.Adapter(context.getApplicationContext, XResource.getId(context, "component_list_item", "layout"))
    case None =>
      log.fatal("lost ApplicationContext")
      null
  }
  /** ComponentBlock header view */
  private lazy val header = AppComponent.Context match {
    case Some(context) =>
      val view = context.getApplicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].
        inflate(XResource.getId(context.getApplicationContext, "header", "layout"), null).asInstanceOf[TextView]
      view.setText(Html.fromHtml(XResource.getString(context, "block_components_title").getOrElse("components")))
      view
    case None =>
      log.fatal("lost ApplicationContext")
      null
  }

  @Loggable
  private def items(partial: Boolean = false): Seq[ComponentBlock.Item] =
    SSHDService.getExecutableInfo(".", partial).map(ei => ComponentBlock.Item(ei.name, ei.executableID)(ei))
  private def updateItems(): Unit = {
    val partial = adapter.getItem(0) == null
    val newItems = SyncVar(items(partial))
    AnyBase.runOnUiThread {
      adapter.setNotifyOnChange(false)
      adapter.clear
      newItems.get.foreach(adapter.add)
      adapter.setNotifyOnChange(true)
      adapter.notifyDataSetChanged
      newItems.unset()
    }
    if (!newItems.waitUnset(DTimeout.normal))
      log.fatal("UI thread hang")
    else if (partial)
      updateItems
  }

  case class Item(value: String, id: Int)(_executableInfo: ExecutableInfo) extends Block.Item with Logging {
    @volatile private var activeDrawable: Option[Drawable] = None
    @volatile private var passiveDrawable: Option[Drawable] = None
    @volatile private var active: Option[Boolean] = None
    @volatile private var icon: WeakReference[ImageView] = new WeakReference(null)
    @volatile private var context: WeakReference[Context] = new WeakReference(null)
    @volatile private var info = _executableInfo
    private val lock = new ReentrantLock
    def executableInfo(): ExecutableInfo = info
    def updatedExecutableInfo(callback: (ExecutableInfo) => Any) = Futures.future {
      Item.this.synchronized {
        info = SSHDService.getExecutableInfo(".").filter(_.executableID == id).head
        callback(info)
      }
    }
    override def toString() = value
    @Loggable
    def init(_context: Context, _icon: ImageView, _active: Boolean) = synchronized {
      (for {
        view <- view.get
      } yield {
        log.debug("initialize background for " + this)
        icon = new WeakReference(_icon)
        context = new WeakReference(_context)
        if (activeDrawable.isEmpty)
          activeDrawable = Some(_context.getResources.getDrawable(XResource.getId(_context, "ic_executable_work", "anim")))
        if (passiveDrawable.isEmpty)
          passiveDrawable = Some(_context.getResources.getDrawable(XResource.getId(_context, "ic_executable_wait", "anim")))
        Futures.future { state(_active) }
      }) getOrElse {
        log.fatal("unable to init() for " + this)
      }
    }
    @Loggable
    def state(_active: Boolean): Unit = synchronized {
      icon.get.foreach {
        icon =>
          if (Some(_active) == active)
            return
          active = Some(_active)
          AnyBase.runOnUiThread { doUpdateOnUI(icon) }
        // Android is too buggy, reimplement it in proper way
        /* val anim = icon.getBackground().asInstanceOf[AnimationDrawable]
          context.get.foreach(_.runOnUiThread(new Runnable { def run = anim.start }))
          icon.invalidate
          view.get.foreach(_.getRootView.postInvalidate)*/
        //restartAnimation()
      }
    }
    private def doUpdateOnUI(icon: ImageView) = {
      active match {
        case Some(true) =>
          log.debug(this.toString + " state: active")
          activeDrawable.foreach(icon.setBackgroundDrawable)
        case _ =>
          log.debug(this.toString + " state: passive")
          passiveDrawable.foreach(icon.setBackgroundDrawable)
      }
      //val anim = icon.getBackground().asInstanceOf[AnimationDrawable]
      //context.get.foreach(_.runOnUiThread(new Runnable { def run = doUpdateOnUI(icon) }))
      //restartAnimation()
    }
    @Loggable
    def restartAnimation() = synchronized {
      icon.get.foreach {
        icon =>
          if (icon.getVisibility == View.VISIBLE) {
            val isRunning = new AtomicBoolean(false)
            val anim = icon.getBackground().asInstanceOf[AnimationDrawable]
            icon.requestLayout
            log.debug("wait animation for " + this)
            val animSeq = checkAnimation(anim)
            if (!animSeq.exists(_.hashCode != animSeq(0).hashCode)) {
              log.debug(" = " + animSeq.mkString(","))
              icon.getRootView.post(new Runnable { def run = startAnimation(icon, anim) })
            }
          }
      }
    }
    private def checkAnimation(anim: AnimationDrawable) = {
      while (lock.isLocked)
        Thread.sleep(anim.getDuration(0))
      for (i <- 0 until 4) yield { Thread.sleep(anim.getDuration(i)); anim.getCurrent() }
    }
    @Loggable
    private def startAnimation(icon: ImageView, anim: AnimationDrawable) {
      lock.lock
      log.debug("start animation for " + this)
      val root = icon.getRootView
      anim.stop()
      icon.requestLayout
      icon.getRootView.postInvalidate
      anim.start()
      lock.unlock
    }
  }
  class Adapter(context: Context, textViewResourceId: Int)
    extends ArrayAdapter[Item](context, textViewResourceId, android.R.id.text1, new ArrayList[Item](Arrays.asList(null))) {
    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val item = getItem(position)
      if (item == null) {
        val view = new TextView(parent.getContext)
        view.setText(XResource.getString(context, "loading").getOrElse("loading..."))
        view
      } else
        item.view.get match {
          case None =>
            val execInfo = item.executableInfo()
            val view = inflater.inflate(textViewResourceId, null)
            val name = view.findViewById(android.R.id.title).asInstanceOf[TextView]
            val description = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
            val subinfo = view.findViewById(android.R.id.text2).asInstanceOf[TextView]
            val icon = view.findViewById(android.R.id.icon1).asInstanceOf[ImageView]
            icon.setFocusable(false)
            icon.setFocusableInTouchMode(false)
            name.setText(execInfo.name)
            description.setText(execInfo.description)
            subinfo.setText(execInfo.version + " / " + execInfo.license)
            item.view = new WeakReference(view)
            icon.setBackgroundDrawable(context.getResources.getDrawable(XResource.getId(context, "ic_executable_wait", "anim")))
            item.init(context, icon, false)
            Level.novice(view)
            view
          case Some(view) =>
            view
        }
    }
  }
  object Dialog {
    lazy val info = AppComponent.Context.map(context =>
      Fragment.instantiate(context.getApplicationContext, classOf[Info].getName, null).asInstanceOf[Info])
    class Info extends SSHDDialog with Logging {
      @volatile private var content = new WeakReference[TextView](null)
      @volatile private var dirtyHackForDirtyFramework = false

      def tag = "dialog_service_components"
      @Loggable
      override def onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
      }
      @Loggable
      override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
        super.onCreateView(inflater, container, savedInstanceState)
        if (dirtyHackForDirtyFramework && inflater != null) {
          log.warn("workaround for \"requestFeature() must be called before adding content\"")
          dirtyHackForDirtyFramework = false
          return super.onCreateView(inflater, container, savedInstanceState)
        } else if (inflater == null)
          dirtyHackForDirtyFramework = true
        val context = getSherlockActivity
        val view = new ScrollView(context)
        val message = new TextView(context)
        view.addView(message, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        message.setMovementMethod(LinkMovementMethod.getInstance())
        message.setId(Int.MaxValue)
        content = new WeakReference(message)
        updateContent()
        view
      }
      @Loggable
      override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
        super.onCreateDialog(savedInstanceState)
        new AlertDialog.Builder(getSherlockActivity).
          setIcon(R.drawable.ic_launcher).
          setTitle(R.string.dialog_component_info_title).
          setPositiveButton(android.R.string.ok, null).
          setView(onCreateView(null, null, null)).
          create()
      }
      def updateContent(info: ExecutableInfo = getArguments.getParcelable[ExecutableInfo]("info")) =
        content.get.foreach {
          content =>
            val context = getSherlockActivity
            val env = info.env.mkString("""<br/>""")
            val s = XResource.getString(context, "dialog_component_info_message").get.format(info.name,
              info.description,
              info.project,
              info.license,
              info.version,
              info.state,
              info.port.getOrElse("-"),
              info.commandLine.map(_.mkString(" ")).getOrElse("-"),
              if (env.nonEmpty) env else "-")
            Linkify.addLinks(content, Linkify.ALL)
            content.setText(Html.fromHtml(s))
        }
    }
  }
}
