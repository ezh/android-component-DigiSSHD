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

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

import scala.actors.Futures
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.block.Block
import org.digimead.digi.ctrl.lib.block.Level
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.service.option.DSAPublicKeyEncription
import org.digimead.digi.ctrl.sshd.service.option.DefaultUser
import org.digimead.digi.ctrl.sshd.service.option.GrantSuperuserPermission
import org.digimead.digi.ctrl.sshd.service.option.NetworkPort
import org.digimead.digi.ctrl.sshd.service.option.RSAPublicKeyEncription
import org.digimead.digi.ctrl.sshd.service.option.SSHAuthentificationMode

import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.text.Html
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView

class OptionBlock(val context: Context)(implicit @transient val dispatcher: Dispatcher) extends Block[OptionBlock.Item] with Logging {
  val items: Seq[OptionBlock.Item] = Seq(DefaultUser, GrantSuperuserPermission, NetworkPort,
    RSAPublicKeyEncription, DSAPublicKeyEncription, SSHAuthentificationMode)
  private lazy val header = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].
    inflate(Android.getId(context, "header", "layout"), null).asInstanceOf[TextView]
  private lazy val adapter = new OptionBlock.Adapter(context, items.toArray)
  OptionBlock.block = Some(this)
  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    header.setText(Html.fromHtml(Android.getString(context, "block_option_title").getOrElse("options")))
    mergeAdapter.addView(header)
    mergeAdapter.addAdapter(adapter)
  }
  @Loggable
  def onListItemClick(l: ListView, v: View, item: OptionBlock.Item) =
    item.onListItemClick(l, v)
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo, item: OptionBlock.Item) =
    item.onCreateContextMenu(menu, v, menuInfo)
  override def onContextItemSelected(menuItem: MenuItem, item: OptionBlock.Item): Boolean =
    item.onContextItemSelected(menuItem)
}

object OptionBlock extends Logging {
  @volatile private var block: Option[OptionBlock] = None

  def checkKeyAlreadyExists(activity: Activity, keyName: String, key: File, callback: (Activity) => Any) {
    if (!key.exists || key.length == 0)
      callback(activity)
    else {
      val affirmative = new AtomicBoolean(false)
      AppComponent.Inner.showDialogSafe(activity, "android_check_key", () => {
        val dialog = new AlertDialog.Builder(activity).
          setTitle(Android.getString(activity, "key_already_exists_title").getOrElse("Key already exists")).
          setMessage(Android.getString(activity, "key_already_exists_message").getOrElse("%s key already exists. Do you want to replace it?").format(keyName)).
          setIcon(android.R.drawable.ic_dialog_alert).
          setPositiveButton(_root_.android.R.string.ok, new DialogInterface.OnClickListener() {
            def onClick(dialog: DialogInterface, whichButton: Int) = affirmative.set(true)
          }).
          setNegativeButton(_root_.android.R.string.cancel, null).
          create
        dialog.show
        dialog
      }, () => Futures.future { if (affirmative.get) callback(activity) })
    }
  }

  trait Item extends Block.Item {
    val option: DOption.OptVal
    override def toString() = option.tag
    def getState[T](context: Context)(implicit m: Manifest[T]): T
    def onListItemClick(l: ListView, v: View)
    def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {}
    def onContextItemSelected(menuItem: MenuItem): Boolean = false
  }
  class Adapter(context: Context, data: Array[Item])
    extends ArrayAdapter[Item](context, android.R.layout.simple_list_item_1, android.R.id.text1, data) {
    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val item = data(position)
      item.view.get match {
        case None =>
          val view =
            item match {
              case item @ DefaultUser =>
                val view = item.getView(context, inflater)
                Level.novice(view)
                view
              case item @ GrantSuperuserPermission =>
                val view = item.getView(context, inflater)
                Level.intermediate(view)
                view
              case item @ NetworkPort =>
                val view = item.getView(context, inflater)
                Level.intermediate(view)
                view
              case item @ RSAPublicKeyEncription =>
                val view = item.getView(context, inflater)
                Level.intermediate(view)
                view
              case item @ DSAPublicKeyEncription =>
                val view = item.getView(context, inflater)
                Level.intermediate(view)
                view
              case item @ SSHAuthentificationMode =>
                val view = item.getView(context, inflater)
                Level.intermediate(view)
                view
              case _ =>
                inflater.inflate(Android.getId(context, "option_list_item_multiple_choice", "layout"), null)
            }
          val text1 = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
          val text2 = view.findViewById(android.R.id.text2).asInstanceOf[TextView]
          text2.setVisibility(View.VISIBLE)
          text1.setText(Html.fromHtml(item.option.name(context)))
          text2.setText(Html.fromHtml(item.option.description(context)))
          item.view = new WeakReference(view)
          view
        case Some(view) =>
          view
      }
    }
  }
}
