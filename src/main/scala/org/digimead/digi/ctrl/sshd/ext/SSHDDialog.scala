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

package org.digimead.digi.ctrl.sshd.ext

import org.digimead.digi.ctrl.lib.androidext.SafeDialog
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.R
import com.actionbarsherlock.app.SherlockDialogFragment
import android.content.DialogInterface
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.view.View
import scala.ref.WeakReference
import android.content.Context
import org.digimead.digi.ctrl.lib.info.UserInfo
import android.app.AlertDialog
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.ImageView
import android.widget.Button
import org.digimead.digi.ctrl.lib.androidext.XResource
import android.view.ViewGroup
import android.os.Bundle
import android.app.Dialog

abstract class SSHDDialog extends SherlockDialogFragment with SafeDialog with Logging {
  protected lazy val defaultNegativeButtonCallback: (SSHDDialog => Any) =
    (dialog) => if (!dialog.getShowsDialog)
      dialog.getSherlockActivity.getSupportFragmentManager.
        popBackStackImmediate(dialog.toString, FragmentManager.POP_BACK_STACK_INCLUSIVE)

  @Loggable
  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val view = super.onCreateView(inflater, container, savedInstanceState)
    notifyBefore
    view
  }
  @Loggable
  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val dialog = super.onCreateDialog(savedInstanceState)
    notifyBefore
    dialog
  }
  @Loggable
  override def onDestroyView() {
    notifyAfter
    super.onDestroyView
  }
  @Loggable
  override def onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    notifySafeDialogDismissed(dialog)
  }
  @Loggable
  override def onResume() = {
    if (!getShowsDialog) {
      Option(getSherlockActivity.findViewById(R.id.main_bottomPanel)).foreach {
        case panel if panel.isShown =>
          panel.setVisibility(View.GONE)
        case _ =>
      }
    }
    super.onResume
  }
  @Loggable
  override def onPause() = {
    /*
     * android fragment transaction architecture is incomplete
     * backstack persistency is unfinished
     * reimplement it by hands is unreasonable
     * deadlines, beer, other stuff... we must to forgive android framework coders
     * in the hope of android 5.x
     * 30.07.2012 Ezh
     */
    // main activity unavailable (configuration change are in progress for example)
    if (!SafeDialog.isEnabled)
      getSherlockActivity.getSupportFragmentManager.
        popBackStack(this.toString, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    super.onPause
    if (!getShowsDialog) {
      Option(getSherlockActivity.findViewById(R.id.main_bottomPanel)).foreach {
        case panel if !panel.isShown =>
          panel.setVisibility(View.VISIBLE)
        case _ =>
      }
    }
  }
  @Loggable
  def show(fragment: TabInterface) {
    val context = fragment.getSherlockActivity
    val manager = context.getSupportFragmentManager
    if (isInBackStack(manager) && fragment.isTopPanelAvailable) {
      log.debug("restore " + tag)
      manager.popBackStack(tag, 0)
    } else {
      log.debug("show " + tag)
      SafeDialog(context, tag, () => this).transaction((ft, fragment, target) => {
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        ft.addToBackStack(tag)
      }).target(R.id.main_topPanel).show()
    }
  }
  @Loggable
  def isInBackStack(manager: FragmentManager): Boolean = {
    val tag = toString
    if (manager.findFragmentByTag(tag) == null)
      return false
    for (i <- 0 until manager.getBackStackEntryCount)
      if (manager.getBackStackEntryAt(i).getName == tag)
        return true
    false
  }
}

object SSHDDialog {
  implicit def dialog2string(d: SSHDDialog) = d.tag

  abstract class Alert(icon: Option[Int]) extends SSHDDialog with Logging {
    def title: String
    def message: String
    protected lazy val cachedDialog = {
      val builder = new AlertDialog.Builder(getSherlockActivity).
        setTitle(title).
        setMessage(message)
      icon.foreach(builder.setIcon)
      negative.foreach(t => builder.setNegativeButton(t._1, t._2))
      neutral.foreach(t => builder.setNeutralButton(t._1, t._2))
      positive.foreach(t => builder.setPositiveButton(t._1, t._2))
      val dialog = builder.create()
      dialog.show
      dialog
    }
    protected lazy val cachedEmbedded = {
      def setButtonListener(bView: Button, title: Int, callback: ButtonListener[_ <: Alert]) {
        bView.setVisibility(View.VISIBLE)
        bView.setText(title)
        bView.setOnClickListener(new View.OnClickListener { def onClick(v: View) = callback })
      }
      val context = getSherlockActivity
      val view = LayoutInflater.from(context).inflate(R.layout.fragment_dialog, null)
      val content = view.findViewById(android.R.id.custom).asInstanceOf[TextView]
      val title = view.findViewById(android.R.id.title).asInstanceOf[TextView]
      title.setText(R.string.dialog_port_title)
      icon.foreach {
        icon =>
          val iconContainer = view.findViewById(android.R.id.icon).asInstanceOf[ImageView]
          iconContainer.setImageResource(icon)
          iconContainer.setVisibility(View.VISIBLE)
      }
      content.setText(message)
      view.findViewById(android.R.id.summary).setVisibility(View.VISIBLE)
      negative.foreach(t => setButtonListener(view.findViewById(android.R.id.button1).asInstanceOf[Button], t._1, t._2))
      neutral.foreach(t => setButtonListener(view.findViewById(android.R.id.button2).asInstanceOf[Button], t._1, t._2))
      positive.foreach(t => setButtonListener(view.findViewById(android.R.id.button3).asInstanceOf[Button], t._1, t._2))
      view
    }
    protected lazy val cachedEmbeddedAttr = XResource.getAttributeSet(getSherlockActivity, R.layout.fragment_dialog)
    protected lazy val positive: Option[(Int, ButtonListener[_ <: Alert])] = None
    protected lazy val neutral: Option[(Int, ButtonListener[_ <: Alert])] = None
    protected lazy val negative: Option[(Int, ButtonListener[_ <: Alert])] = None

    @Loggable
    override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
      super.onCreateView(inflater, container, savedInstanceState)
      if (getShowsDialog) {
        null
      } else {
        val context = getSherlockActivity
        Option(cachedEmbedded.getParent).foreach(_.asInstanceOf[ViewGroup].removeView(cachedEmbedded))
        cachedEmbeddedAttr.foreach(attr => cachedEmbedded.setLayoutParams(container.generateLayoutParams(attr)))
        cachedEmbedded
      }
    }
    @Loggable
    override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
      super.onCreateDialog(savedInstanceState)
      cachedDialog.show
      cachedDialog
    }
  }
  class ButtonListener[T <: SSHDDialog](dialog: WeakReference[T],
    callback: Option[(T) => Any]) extends DialogInterface.OnClickListener() with Logging {
    @Loggable
    def onClick(dialogInterface: DialogInterface, whichButton: Int) = try {
      for {
        dialog <- dialog.get
        callback <- callback
      } callback(dialog)
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
}
