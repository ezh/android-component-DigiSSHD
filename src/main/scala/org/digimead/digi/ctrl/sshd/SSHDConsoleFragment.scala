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

import java.util.concurrent.locks.ReentrantLock

import scala.Option.option2Iterable
import scala.actors.Futures
import scala.collection.mutable.SynchronizedQueue
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.DMessage
import org.digimead.digi.ctrl.lib.message.IAmBusy
import org.digimead.digi.ctrl.lib.message.IAmReady
import org.digimead.digi.ctrl.lib.message.IAmWarn
import org.digimead.digi.ctrl.lib.message.IAmYell
import org.digimead.digi.ctrl.sshd.ext.TabInterface

import com.actionbarsherlock.app.SherlockFragment

import android.app.Activity
import android.os.Bundle
import android.support.v4.app.Fragment
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView

class SSHDConsoleFragment extends SherlockFragment with Logging {
  @volatile private var console = new WeakReference[TextView](null)
  @volatile private var consoleLines = 0
  SSHDConsoleFragment.fragment = Some(this)
  log.debug("alive")

  def tag = "fragment_console"
  @Loggable
  override def onAttach(activity: Activity) {
    super.onAttach(activity)
    SSHDConsoleFragment.fragment = Some(this)
  }
  @Loggable
  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val view = new TextView(getSherlockActivity())
    view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT))
    val vto = view.getViewTreeObserver()
    vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
      def onGlobalLayout() = {
        consoleLines = view.getHeight() / view.getLineHeight()
        Futures.future { SSHDConsoleFragment.update }
      }
    })
    console = new WeakReference(view)
    view
  }
  @Loggable
  override def onResume() {
    super.onResume()
    Futures.future { SSHDConsoleFragment.update }
  }
  @Loggable
  override def onDetach() {
    SSHDConsoleFragment.fragment = None
    super.onDetach()
  }
}

object SSHDConsoleFragment extends Logging {
  @volatile private var fragment: Option[SSHDConsoleFragment] = None
  private val reentrantLock = new ReentrantLock
  val maximumQueueLength = 50
  val queue = new SynchronizedQueue[DMessage] {
    override def +=(elem: DMessage): this.type = {
      Futures.future { SSHDConsoleFragment.update() }
      for { i <- 0 to (length - maximumQueueLength) }
        this.dequeue()
      super.+=(elem)
    }
  }
  log.debug("alive")

  @Loggable
  def show(tab: TabInterface) = AnyBase.runOnUiThread {
    val manager = tab.getSherlockActivity.getSupportFragmentManager
    val fragmentInstance = Fragment.instantiate(tab.getSherlockActivity, classOf[SSHDConsoleFragment].getName, null)
    val tag = fragmentInstance.asInstanceOf[SSHDConsoleFragment].tag
    val ft = manager.beginTransaction()
    val target = if (tab.isTopPanelAvailable) {
      log.debug("show embedded fragment " + tag)
      R.id.main_bottomPanel
    } else {
      log.debug("show modal fragment " + tag)
      ft.addToBackStack(tag)
      R.id.main_secondary
    }
    ft.replace(target, fragmentInstance, tag)
    ft.commit()
  }
  private def update(): Unit = for {
    fragment <- fragment
    console <- fragment.console.get
  } if (reentrantLock.tryLock())
    try {
      Thread.sleep(100)
      val total = queue.length
      val content = for (i <- 1 to scala.math.min(fragment.consoleLines, queue.length))
        yield queue.get(total - i).map(_ match {
        case message: IAmYell => "<font color=\"red\">" + message.message + "</font>"
        case message: IAmWarn => "<font color=\"yellow\">" + message.message + "</font>"
        case message: IAmReady => "<font color=\"white\"><b>" + message.message + "</b></font>"
        case message: IAmBusy => "<font color=\"white\"><b>" + message.message + "</b></font>"
        case message => message.message
      })
      val text = Html.fromHtml(content.flatten.mkString("<br>"))
      AnyBase.runOnUiThread { console.setText(text) }
    } finally {
      reentrantLock.unlock()
    }
}
