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

import java.net.URL

import scala.annotation.implicitNotFound
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.androidext.SafeDialog
import org.digimead.digi.ctrl.lib.androidext.XDialog
import org.digimead.digi.ctrl.lib.androidext.XDialog.dialog2string
import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.base.AppControl
import org.digimead.digi.ctrl.lib.block.Block
import org.digimead.digi.ctrl.lib.block.Level
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.message.IAmYell
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDResource
import org.digimead.digi.ctrl.sshd.ext.SSHDAlertDialog
import org.digimead.digi.ctrl.sshd.user.UserFragment

import com.commonsware.cwac.merge.MergeAdapter

import android.content.Context
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentTransaction
import android.text.Html
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

// buy or not

class EnvironmentBlock(val context: Context)(implicit @transient val dispatcher: Dispatcher) extends Block[EnvironmentBlock.Item] with Logging {
  EnvironmentBlock.block = Some(this)

  def items = for (i <- 0 until EnvironmentBlock.adapter.getCount) yield EnvironmentBlock.adapter.getItem(i)
  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    Option(EnvironmentBlock.header).foreach(mergeAdapter.addView)
    Option(EnvironmentBlock.adapter).foreach(mergeAdapter.addAdapter)
  }
  @Loggable
  def onListItemClick(l: ListView, v: View, item: EnvironmentBlock.Item) = {}
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo, item: EnvironmentBlock.Item) {}
  @Loggable
  override def onContextItemSelected(menuItem: MenuItem, item: EnvironmentBlock.Item): Boolean = false
  @Loggable
  def onClickReinstall(v: View) = {
    val context = v.getContext
    if (AppControl.isBound) {
      for {
        fragment <- TabContent.fragment
        dialog <- SSHDResource.serviceReinstall
      } if (!dialog.isShowing)
        EnvironmentBlock.Dialog.showReinstall(fragment.getSherlockActivity)
      // TODO dialog
      //        IAmMumble(Html.fromHtml(XResource.getString(context, "service_reinstall_files").
      //          getOrElse("reinstall service files")).toString)
      //Toast.makeText(this, XResource.getString(getActivity, "reinstall").getOrElse("reinstall"), DConstant.toastTimeout).show()
      /*    AppComponent.Inner ! AppComponent.Message.PrepareEnvironment(this, false, true, (success) =>
      runOnUiThread(new Runnable() {
        def run = if (success)
          Toast.makeText(TabActivity.this, XResource.getString(TabActivity.this,
            "reinstall_complete").getOrElse("reinstall complete"), DConstant.toastTimeout).show()
      }))*/
    } else {
      IAmYell(Html.fromHtml(XResource.getString(context, "service_reinstall_unavailable").
        getOrElse("service reinstall unavailable")).toString)
      Toast.makeText(context, Html.fromHtml("<font color='red'>%s</font>".format(XResource.getString(context, "unavailable").
        getOrElse("unavailable"))), Toast.LENGTH_SHORT).show
    }
  }
  @Loggable
  def onClickFactoryDefault(v: View) = for {
    fragment <- TabContent.fragment
    dialog <- SSHDResource.serviceFactoryDefault
  } if (!dialog.isShowing)
    EnvironmentBlock.Dialog.showFactoryDefault(fragment.getSherlockActivity)
  @Loggable
  def onClickUsers(v: View) = UserFragment.show
}

object EnvironmentBlock extends Logging {
  @volatile private var block: Option[EnvironmentBlock] = None
  /** OptionBlock adapter */
  private[service] lazy val adapter = AppComponent.Context match {
    case Some(context) =>
      new EnvironmentBlock.Adapter(context, android.R.layout.simple_list_item_1, Seq())
    case None =>
      log.fatal("lost ApplicationContext")
      null
  }
  /** OptionBlock header view */
  private lazy val header = AppComponent.Context match {
    case Some(context) =>
      val view = context.getApplicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].
        inflate(XResource.getId(context.getApplicationContext, "element_service_environment_header", "layout"), null).asInstanceOf[LinearLayout]
      val headerTitle = view.findViewById(android.R.id.title).asInstanceOf[TextView]
      headerTitle.setText(Html.fromHtml(XResource.getString(context, "block_environment_title").getOrElse("environment")))
      val onClickUsersButton = view.findViewById(R.id.service_environment_user_button).asInstanceOf[Button]
      onClickUsersButton.setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View) = EnvironmentBlock.block.foreach(_.onClickUsers(v))
      })
      Level.intermediate(onClickUsersButton)
      val onClickServiceReinstallButton = view.findViewById(R.id.service_environment_reinstall_button)
      onClickServiceReinstallButton.setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View) = EnvironmentBlock.block.foreach(_.onClickReinstall(v))
      })
      Level.professional(onClickServiceReinstallButton)
      val onClickServiceResetButton = view.findViewById(R.id.service_environment_reset_button)
      onClickServiceResetButton.setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View) = EnvironmentBlock.block.foreach(_.onClickFactoryDefault(v))
      })
      Level.professional(onClickServiceResetButton)
      view
    case None =>
      log.fatal("lost ApplicationContext")
      null
  }
  case class Item(value: String, version: String, description: String, link: URL) extends Block.Item {
    override def toString() = value
  }
  class Adapter(context: Context, textViewResourceId: Int, data: Seq[Item])
    extends ArrayAdapter[Item](context, textViewResourceId, android.R.id.text1, data.toArray) {
    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val item = data(position)
      item.view.get match {
        case None =>
          val view = inflater.inflate(textViewResourceId, null)
          view
        case Some(view) =>
          view
      }
    }
  }
  object Dialog {
    @Loggable
    def showReinstall(activity: FragmentActivity) =
      SSHDResource.serviceReinstall.foreach(dialog =>
        SafeDialog(activity, dialog, () => dialog).transaction((ft, fragment, target) => {
          ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
          ft.addToBackStack(dialog)
        }).show())
    def showFactoryDefault(activity: FragmentActivity) =
      SSHDResource.serviceFactoryDefault.foreach(dialog =>
        SafeDialog(activity, dialog, () => dialog).transaction((ft, fragment, target) => {
          ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
          ft.addToBackStack(dialog)
        }).show())

    class Reinstall
      extends SSHDAlertDialog(AppComponent.Context.map(c => (XResource.getId(c, "ic_danger", "drawable")))) {
      override protected lazy val positive = Some((android.R.string.ok, new XDialog.ButtonListener(new WeakReference(Reinstall.this),
        Some((dialog: Reinstall) => { log.g_a_s_e("AAAA") }))))
      override protected lazy val negative = Some((android.R.string.cancel, new XDialog.ButtonListener(new WeakReference(Reinstall.this),
        Some(defaultButtonCallback))))

      def tag = "dialog_service_reinstall"
      def title = Html.fromHtml(XResource.getString(getSherlockActivity, "service_reinstall_title").
        getOrElse("Reinstall resources"))
      def message = Some(Html.fromHtml(XResource.getString(getSherlockActivity, "service_reinstall_message").
        getOrElse("Do you want to reinstall service resources?")))
    }
    class FactoryDefault
      extends SSHDAlertDialog(AppComponent.Context.map(c => (XResource.getId(c, "ic_danger", "drawable")))) {
      override protected lazy val positive = Some((android.R.string.ok, new XDialog.ButtonListener(new WeakReference(FactoryDefault.this),
        Some((dialog: FactoryDefault) => { log.g_a_s_e("AAAA") }))))
      override protected lazy val negative = Some((android.R.string.cancel, new XDialog.ButtonListener(new WeakReference(FactoryDefault.this),
        Some(defaultButtonCallback))))

      def tag = "dialog_service_factorydefault"
      def title = Html.fromHtml(XResource.getString(getSherlockActivity, "service_factorydefault_title").
        getOrElse("Factory default reset"))
      def message = Some(Html.fromHtml(XResource.getString(getSherlockActivity, "service_factorydefault_message").
        getOrElse("Do you want to reset DigiSSHD to the factory default?")))
    }
  }
}
