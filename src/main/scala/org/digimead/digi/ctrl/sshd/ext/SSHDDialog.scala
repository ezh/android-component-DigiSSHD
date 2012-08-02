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

import scala.Option.option2Iterable
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.androidext.SafeDialog
import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.sshd.R

import com.actionbarsherlock.app.SherlockDialogFragment

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

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

  abstract class Alert(icon: Option[Int] = None, extContent: Option[Int] = None) extends SSHDDialog with Logging {
    @volatile protected var customContent: Option[View] = None
    def title: CharSequence
    def message: Option[CharSequence]
    protected lazy val (cachedModal,
      modalContent,
      modalCustomContent,
      modalNegative,
      modalNeutral,
      modalPositive) = {
      val context = getSherlockActivity
      val scale = context.getResources().getDisplayMetrics().density
      val padding = (10 * scale).toInt
      val builder = new AlertDialog.Builder(context).setTitle(title)
      val customContentView = extContent.map(extContent => {
        val inflater = getSherlockActivity.getApplicationContext.
          getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
        val extView = inflater.inflate(extContent, null)
        extView.setPadding(padding, padding, padding, padding)
        builder.setView(extView)
        extView
      })
      icon.foreach(builder.setIcon)
      negative.foreach(t => builder.setNegativeButton(t._1, t._2))
      neutral.foreach(t => builder.setNeutralButton(t._1, t._2))
      positive.foreach(t => builder.setPositiveButton(t._1, t._2))

      val contentView = customContentView match {
        case Some(customContentView) =>
          val extViewContent = customContentView.findViewById(android.R.id.custom).asInstanceOf[ViewGroup]
          assert(extViewContent != null, { "android.R.id.custom not found in external dialog viewgroup" })
          val contentView = new TextView(context)
          contentView.setTextAppearance(context, android.R.style.TextAppearance_Medium)
          contentView.setPadding(0, 0, 0, padding)
          extViewContent.addView(contentView, 0, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
          Some(contentView)
        case None =>
          message.foreach(builder.setMessage)
          None
      }
      val dialog = builder.create()
      dialog.show
      val negativeView = negative.flatMap(n => Option(dialog.getButton(0)))
      val neutralView = neutral.flatMap(n => Option(dialog.getButton(1)))
      val positiveView = positive.flatMap(n => Option(dialog.getButton(2)))
      (dialog, contentView, customContentView, negativeView, neutralView, positiveView)
    }
    protected lazy val (cachedEmbedded,
      embeddedContent,
      embeddedCustomContent,
      embeddedNegative,
      embeddedNeutral,
      embeddedPositive) = {
      def setButtonListener(bView: Button, title: Int, callback: ButtonListener[_ <: Alert]) {
        bView.setVisibility(View.VISIBLE)
        bView.setText(title)
        bView.setOnClickListener(new View.OnClickListener { def onClick(v: View) = callback })
      }
      val context = getSherlockActivity
      val view = LayoutInflater.from(context).inflate(R.layout.fragment_dialog, null)
      val contentView = view.findViewById(android.R.id.custom).asInstanceOf[TextView]
      val titleView = view.findViewById(android.R.id.title).asInstanceOf[TextView]
      titleView.setText(title)
      icon.foreach {
        icon =>
          val iconContainer = view.findViewById(android.R.id.icon).asInstanceOf[ImageView]
          iconContainer.setImageResource(icon)
          iconContainer.setVisibility(View.VISIBLE)
      }
      val customContentView = extContent.flatMap(extContent => {
        val inflater = getSherlockActivity.getApplicationContext.
          getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
        Option(inflater.inflate(extContent, contentView.getParent.asInstanceOf[ViewGroup], true))
      })
      message match {
        case Some(message) =>
          contentView.setText(message)
        case None =>
          contentView.setVisibility(View.GONE)
      }
      if (negative.nonEmpty || neutral.nonEmpty || positive.nonEmpty)
        view.findViewById(android.R.id.summary).setVisibility(View.VISIBLE)
      val negativeView = negative.map(t => {
        val buttonView = view.findViewById(android.R.id.button1).asInstanceOf[Button]
        setButtonListener(buttonView, t._1, t._2)
        buttonView
      })
      val neutralView = neutral.map(t => {
        val buttonView = view.findViewById(android.R.id.button2).asInstanceOf[Button]
        setButtonListener(buttonView, t._1, t._2)
        buttonView
      })
      val positiveView = positive.map(t => {
        val buttonView = view.findViewById(android.R.id.button3).asInstanceOf[Button]
        setButtonListener(buttonView, t._1, t._2)
        buttonView
      })
      (view, contentView, customContentView, negativeView, neutralView, positiveView)
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
        customContent = embeddedCustomContent
        cachedEmbedded
      }
    }
    @Loggable
    override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
      super.onCreateDialog(savedInstanceState)
      cachedModal.show
      (modalContent, message) match {
        case (Some(content), Some(message)) =>
          content.setVisibility(View.VISIBLE)
          content.setText(message)
        case (Some(content), None) =>
          content.setVisibility(View.GONE)
        case (None, Some(message)) =>
          cachedModal.setMessage(message)
        case (None, None) =>
          try {
            cachedModal.setMessage(null)
          } catch {
            case e =>
              log.warn("unable to reset dialog content")
              cachedModal.setMessage("")
          }
      }
      cachedModal.setTitle(title)
      customContent = modalCustomContent
      cachedModal
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
