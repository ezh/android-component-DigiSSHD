/*
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

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

import scala.actors.Futures.future
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.base.AppControl
import org.digimead.digi.ctrl.lib.block.Block
import org.digimead.digi.ctrl.lib.block.Level
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.declaration.DState
import org.digimead.digi.ctrl.lib.info.ExecutableInfo
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.message.IAmYell
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.SSHDActivity
import org.digimead.digi.ctrl.sshd.SSHDService

import com.commonsware.cwac.merge.MergeAdapter

import android.content.Context
import android.content.Intent
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.ClipboardManager
import android.text.Html
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class ComponentBlock(val context: Context)(implicit @transient val dispatcher: Dispatcher) extends Block[ComponentBlock.Item] with Logging {
  lazy val items = getAppSeq
  private lazy val header = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].
    inflate(Android.getId(context, "header", "layout"), null).asInstanceOf[TextView]
  private lazy val adapter = new ComponentBlock.Adapter(context, Android.getId(context, "component_list_item", "layout"), items)
  ComponentBlock.block = Some(this)
  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    header.setText(Html.fromHtml(Android.getString(context, "block_components_title").getOrElse("components")))
    mergeAdapter.addView(header)
    mergeAdapter.addAdapter(adapter)
  }
  @Loggable
  def onListItemClick(l: ListView, v: View, item: ComponentBlock.Item) = future {
    val bundle = new Bundle()
    bundle.putParcelable("info", item.executableInfo())
    SSHDActivity.activity.foreach(AppComponent.Inner.showDialogSafe(_, "SSHDActivity.Dialog.ComponentInfo", SSHDActivity.Dialog.ComponentInfo, bundle))
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo, item: ComponentBlock.Item) {
    log.debug("create context menu for " + item.value)
    menu.setHeaderTitle(item.value)
    menu.setHeaderIcon(Android.getId(context, "ic_launcher", "drawable"))
    menu.add(Menu.NONE, Android.getId(context, "block_component_jump_to_project"), 1,
      Android.getString(context, "block_component_jump_to_project").getOrElse("Jump to project"))
    menu.add(Menu.NONE, Android.getId(context, "block_component_copy_command_line"), 2,
      Android.getString(context, "block_component_copy_command_line").getOrElse("Copy command line"))
    menu.add(Menu.NONE, Android.getId(context, "block_component_copy_info"), 3,
      Android.getString(context, "block_component_copy_info").getOrElse("Copy information"))
  }
  @Loggable
  override def onContextItemSelected(menuItem: MenuItem, item: ComponentBlock.Item): Boolean = {
    menuItem.getItemId match {
      case id if id == Android.getId(context, "block_component_jump_to_project") =>
        future {
          try {
            val execInfo = item.executableInfo()
            val intent = new Intent(Intent.ACTION_VIEW, Uri.parse(execInfo.project))
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            context.startActivity(intent)
          } catch {
            case e =>
              IAmYell("Unable to open link: " + item.value, e)
          }
        }
        true
      case id if id == Android.getId(context, "block_component_copy_command_line") =>
        future {
          try {
            val execInfo = item.executableInfo()
            val message = Android.getString(context, "block_component_copy_command_line").
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
      case id if id == Android.getId(context, "block_component_copy_info") =>
        future {
          try {
            val execInfo = item.executableInfo()
            val env = execInfo.env.mkString("""<br/>""")
            val string = Android.getString(context, "dialog_component_info_message").get.format(execInfo.name,
              execInfo.description,
              execInfo.project,
              execInfo.license,
              execInfo.version,
              execInfo.state,
              execInfo.port.getOrElse("-"),
              execInfo.commandLine.map(_.mkString(" ")).getOrElse("-"),
              if (env.nonEmpty) env else "-")
            val message = Android.getString(context, "block_component_copy_info").
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
  def getAppSeq(): Seq[ComponentBlock.Item] =
    SSHDService.getExecutableInfo(".").map(ei => ComponentBlock.Item(ei.name, ei.executableID))
  @Loggable
  def updateComponentsState(state: DState.Value) = synchronized {
    state match {
      case DState.Active =>
        items.foreach(i => i.view.get.foreach(view => i.state(true)))
      case DState.Passive =>
        items.foreach(i => i.view.get.foreach(view => i.state(false)))
      case _ =>
    }
  }
}

object ComponentBlock extends Logging {
  @volatile private var block: Option[ComponentBlock] = None
  case class Item(value: String, id: Int) extends Block.Item with Logging {
    @volatile private var activeDrawable: Option[Drawable] = None
    @volatile private var passiveDrawable: Option[Drawable] = None
    @volatile private var active: Option[Boolean] = None
    @volatile private var icon: WeakReference[ImageView] = new WeakReference(null)
    @volatile private var context: WeakReference[Context] = new WeakReference(null)
    private val lock = new ReentrantLock
    def executableInfo(allowCallFromUI: Boolean = false): ExecutableInfo =
      SSHDService.getExecutableInfo(".", allowCallFromUI).filter(_.executableID == id).head
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
          activeDrawable = Some(_context.getResources.getDrawable(Android.getId(_context, "ic_executable_work", "anim")))
        if (passiveDrawable.isEmpty)
          passiveDrawable = Some(_context.getResources.getDrawable(Android.getId(_context, "ic_executable_wait", "anim")))
        future { state(_active) }
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
  class Adapter(context: Context, textViewResourceId: Int, data: Seq[Item])
    extends ArrayAdapter[Item](context, textViewResourceId, android.R.id.text1, data.toArray) {
    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val item = data(position)
      item.view.get match {
        case None =>
          val execInfo = item.executableInfo(true)
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
          icon.setBackgroundDrawable(context.getResources.getDrawable(Android.getId(context, "ic_executable_wait", "anim")))
          if (AppControl.Inner.isAvailable.getOrElse(false)) {
            AppControl.Inner.callStatus(context.getPackageName, true)() match {
              case Right(componentState) =>
                if (componentState.state == DState.Active)
                  item.init(context, icon, true)
                else
                  item.init(context, icon, false)
              case Left(error) =>
                item.init(context, icon, false)
            }
          } else
            item.init(context, icon, false)
          Level.novice(view)
          view
        case Some(view) =>
          view
      }
    }
  }
}
