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
 * version 2 for more details (a copy is included in the LICENSE file that
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
import scala.annotation.elidable
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.base.AppService
import org.digimead.digi.ctrl.lib.block.Block
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

import android.app.Activity
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
import annotation.elidable.ASSERTION

class ComponentBlock(val context: Activity)(implicit @transient val dispatcher: Dispatcher) extends Block[ComponentBlock.Item] with Logging {
  val items = getAppSeq
  private lazy val header = context.getLayoutInflater.inflate(Android.getId(context, "header", "layout"), null).asInstanceOf[TextView]
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
  def onListItemClick(l: ListView, v: View, item: ComponentBlock.Item) = {
    val bundle = new Bundle()
    bundle.putParcelable("info", item.executableInfo)
    SSHDActivity.activity.foreach(AppActivity.Inner.showDialogSafe(_, Android.getId(context, "dialog_ComponentInfo"), bundle))
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
        try {
          val intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.executableInfo.project))
          intent.addCategory(Intent.CATEGORY_BROWSABLE)
          context.startActivity(intent)
        } catch {
          case e =>
            IAmYell("Unable to open link: " + item.value, e)
        }
        true
      case id if id == Android.getId(context, "block_component_copy_command_line") =>
        try {
          val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
          clipboard.setText(item.executableInfo.commandLine.map(_.mkString(" ")).getOrElse("-"))
          val message = Android.getString(context, "block_component_copy_command_line").
            getOrElse("Copy command line to clipboard")
          context.runOnUiThread(new Runnable {
            def run = Toast.makeText(context, message, DConstant.toastTimeout).show()
          })
        } catch {
          case e =>
            IAmYell("Unable to copy to clipboard command line for: " + item.value, e)
        }
        true
      case id if id == Android.getId(context, "block_component_copy_info") =>
        try {
          val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
          val env = item.executableInfo.env.mkString("""<br/>""")
          val string = Android.getString(context, "dialog_component_info_message").get.format(item.executableInfo.name,
            item.executableInfo.description,
            item.executableInfo.project,
            item.executableInfo.license,
            item.executableInfo.version,
            item.executableInfo.state,
            item.executableInfo.port.getOrElse("-"),
            item.executableInfo.commandLine.map(_.mkString(" ")).getOrElse("-"),
            if (env.nonEmpty) env else "-")
          clipboard.setText(Html.fromHtml(string).toString)
          val message = Android.getString(context, "block_component_copy_info").
            getOrElse("Copy information to clipboard")
          context.runOnUiThread(new Runnable {
            def run = Toast.makeText(context, message, DConstant.toastTimeout).show()
          })
        } catch {
          case e =>
            IAmYell("Unable to copy to clipboard command line for: " + item.value, e)
        }
        true
      case message =>
        log.fatal("skip unknown message " + message)
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
  case class Item(value: String, id: Int) extends Block.Item {
    private var activeDrawable: Option[Drawable] = None
    private var passiveDrawable: Option[Drawable] = None
    private var active: Option[Boolean] = None
    private var icon: WeakReference[ImageView] = new WeakReference(null)
    private var context: WeakReference[Activity] = new WeakReference(null)
    private val lock = new ReentrantLock
    def executableInfo: ExecutableInfo = SSHDService.getExecutableInfo(".").filter(_.executableID == id).head
    override def toString() = value
    @Loggable
    def init(_context: Activity, _icon: ImageView, _active: Boolean) = synchronized {
      (for {
        view <- view.get
      } yield {
        log.debug("initialize background for " + this)
        assert(activeDrawable == None && passiveDrawable == None)
        icon = new WeakReference(_icon)
        context = new WeakReference(_context)
        activeDrawable = Some(_context.getResources.getDrawable(Android.getId(_context, "ic_executable_work", "anim")))
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
          context.get.foreach(_.runOnUiThread(new Runnable { def run = doUpdateOnUI(icon) }))
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
  class Adapter(context: Activity, textViewResourceId: Int, data: Seq[Item])
    extends ArrayAdapter[Item](context, textViewResourceId, android.R.id.text1, data.toArray) {
    private var inflater: LayoutInflater = context.getLayoutInflater
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val item = data(position)
      item.view.get match {
        case None =>
          val view = inflater.inflate(textViewResourceId, null)
          val name = view.findViewById(android.R.id.title).asInstanceOf[TextView]
          val description = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
          val subinfo = view.findViewById(android.R.id.text2).asInstanceOf[TextView]
          val icon = view.findViewById(android.R.id.icon1).asInstanceOf[ImageView]
          icon.setFocusable(false)
          icon.setFocusableInTouchMode(false)
          name.setText(item.executableInfo.name)
          description.setText(item.executableInfo.description)
          subinfo.setText(item.executableInfo.version + " / " + item.executableInfo.license)
          item.view = new WeakReference(view)
          icon.setBackgroundDrawable(context.getResources.getDrawable(Android.getId(context, "ic_executable_wait", "anim")))
          AppService.Inner ! AppService.Message.Status(context.getPackageName, {
            case Right(componentState) =>
              if (componentState.state == DState.Active)
                item.init(context, icon, true)
              else
                item.init(context, icon, false)
            case Left(error) =>
              item.init(context, icon, false)
          })
          view
        case Some(view) =>
          view
      }
    }
  }
}
