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

package org.digimead.digi.ctrl.sshd.info

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.block.Block
import org.digimead.digi.ctrl.lib.block.Support
import org.digimead.digi.ctrl.lib.declaration.DMessage.Dispatcher
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.R

import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
import android.text.Html
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView

class Interface(val context: Activity)(implicit @transient val dispatcher: Dispatcher) extends Block[Interface.Item] with Logging {
  private lazy val header = context.getLayoutInflater.inflate(Android.getId(context, "header", "layout"), null).asInstanceOf[TextView]
  protected val items = Seq(Interface.Item(null, null)) // null is "pending..." item, handled at InterfaceAdapter
  private lazy val adapter = new Interface.Adapter(context, () => { items })
  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    header.setText(Html.fromHtml(Android.getString(context, "block_interface_title").getOrElse("interfaces")))
    mergeAdapter.addView(header)
    mergeAdapter.addAdapter(adapter)
  }
  @Loggable
  def onListItemClick(l: ListView, v: View, item: Interface.Item) = {
    /*    item match {
      case this.itemProject => // jump to project
        log.debug("open project web site at " + projectUri)
        try {
          val intent = new Intent(Intent.ACTION_VIEW, projectUri)
          intent.addCategory(Intent.CATEGORY_BROWSABLE)
          context.startActivity(intent)
        } catch {
          case e =>
            DMessage.IAmYell("Unable to open project link: " + projectUri, e)
        }
      case this.itemIssues => // jump to issues
        log.debug("open issues web page at " + projectUri)
        try {
          val intent = new Intent(Intent.ACTION_VIEW, issuesUri)
          intent.addCategory(Intent.CATEGORY_BROWSABLE)
          context.startActivity(intent)
        } catch {
          case e =>
            DMessage.IAmYell("Unable to open project link: " + issuesUri, e)
        }
      case this.itemEmail => // create email
        // TODO simple email vs complex with log
        log.debug("send email to " + emailTo)
        try {
          val intent = new Intent(Intent.ACTION_SEND)
          intent.putExtra(Intent.EXTRA_EMAIL, Array[String](emailTo, ""))
          intent.putExtra(Intent.EXTRA_SUBJECT, emailSubject)
          intent.putExtra(android.content.Intent.EXTRA_TEXT, "")
          intent.setType("text/plain");
          context.startActivity(Intent.createChooser(intent, Android.getString(context, "share").getOrElse("share")))
        } catch {
          case e =>
            DMessage.IAmYell("Unable 'send to' email: " + emailTo + " / " + emailSubject, e)
        }
      case this.itemChat => // show context menu with call/skype
        log.debug("open context menu for voice call")
        l.showContextMenuForChild(v)
      case item =>
        log.fatal("unsupported context menu item " + item)
    }*/
  }
  @Loggable
  def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo, item: Support.Item) {
    log.debug("create context menu for " + item.name)
    /*    menu.setHeaderTitle(Android.getString(context, "context_menu").getOrElse("Context Menu"))
    //inner.icon(this).map(menu.setHeaderIcon(_))
    menu.add(Menu.NONE, Android.getId(context, "block_support_voice_call"), 1,
      Android.getString(context, "block_support_voice_call").getOrElse("Voice call"))
    menu.add(Menu.NONE, Android.getId(context, "block_support_skype_call"), 1,
      Android.getString(context, "block_support_skype_call").getOrElse("Skype call"))*/
  }
  @Loggable
  def onContextItemSelected(menuItem: MenuItem, item: Support.Item): Boolean = {
    /*    menuItem.getItemId match {
      case id if id == Android.getId(context, "block_support_voice_call") =>
        log.debug("start voice call to " + voicePhone)
        try {
          val intent = new Intent(Intent.ACTION_CALL)
          intent.setData(Uri.parse("tel:" + voicePhone))
          context.startActivity(intent)
          true
        } catch {
          case e =>
            DMessage.IAmYell("Unable start voice call to " + voicePhone, e)
            false
        }
      case id if id == Android.getId(context, "block_support_skype_call") =>
        log.debug("start skype call to " + skypeUser)
        try {
          val intent = new Intent("android.intent.action.VIEW")
          intent.setData(Uri.parse("skype:" + skypeUser))
          context.startActivity(intent)
          true
        } catch {
          case e =>
            DMessage.IAmYell("Unable start skype call to " + skypeUser, e)
            false
        }
      case id =>
        log.fatal("unknown context menu id " + id)
        false
    }*/
    false
  }
}

object Interface {
  /*  private val name = "name"
  private val description = "description"
  sealed case class Item(id: Int)(val name: String, val description: String, val icon: String = "") extends Block.Item
  object Item {
    private val counter = new AtomicInteger(0)
    def apply(name: String, description: String) = new Item(counter.getAndIncrement)(name, description)
    def apply(name: String, description: String, icon: String) = new Item(counter.getAndIncrement)(name, description, icon)
  }
  class Adapter(context: Activity, textViewResourceId: Int, data: Seq[Item])
    extends ArrayAdapter(context, textViewResourceId, android.R.id.text1, data.toArray) {
    private var inflater: LayoutInflater = context.getLayoutInflater
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val item = data(position)
      item.view.get match {
        case None =>
          val view = inflater.inflate(textViewResourceId, null)
          val text1 = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
          val text2 = view.findViewById(android.R.id.text2).asInstanceOf[TextView]
          val icon = view.findViewById(android.R.id.icon1).asInstanceOf[ImageView]
          text2.setVisibility(View.VISIBLE)
          text1.setText(Html.fromHtml(item.name))
          text2.setText(Html.fromHtml(item.description))
          if (item.icon.nonEmpty)
            Android.getId(context, item.icon, "drawable") match {
              case i if i != 0 =>
                icon.setVisibility(View.VISIBLE)
                icon.setImageDrawable(context.getResources.getDrawable(i))
              case _ =>
            }
          item.view = new WeakReference(view)
          view
        case Some(view) =>
          view
      }
    }
  }*/
  /*
   * status:
   * None - unused
   * Some(false) - passive
   * Some(true) - active
   */
  case class Item(val value: String, val status: Option[Boolean]) extends Block.Item {
    override def toString() = value
  }
  class Adapter(context: Activity, values: () => Seq[Interface.Item],
    private val resource: Int = android.R.layout.simple_list_item_1,
    private val fieldId: Int = android.R.id.text1)
    extends ArrayAdapter[Interface.Item](context, resource, fieldId) {
    private lazy val icActive = context.getResources().getDrawable(R.drawable.ic_button_plus)
    private lazy val icPassive = context.getResources().getDrawable(R.drawable.ic_tab_session_selected)
    private lazy val icUnused = context.getResources().getDrawable(R.drawable.ic_tab_info_unselected)
    values().foreach(add(_))
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val view = super.getView(position, convertView, parent)
      val text = view.asInstanceOf[TextView]
      val item = getItem(position)
      text.setCompoundDrawablePadding(10)
      item match {
        case Interface.Item(null, null) =>
          text.setText(context.getString(R.string.pending))
        case Interface.Item(_, Some(true)) =>
          text.setCompoundDrawablesWithIntrinsicBounds(icActive, null, null, null)
        case Interface.Item(_, Some(false)) =>
          text.setCompoundDrawablesWithIntrinsicBounds(icPassive, null, null, null)
        case Interface.Item(_, None) =>
          text.setCompoundDrawablesWithIntrinsicBounds(icUnused, null, null, null)
      }
      view
    }
    override def notifyDataSetChanged() = notifyDataSetChanged(false)
    def notifyDataSetChanged(updateValues: Boolean) = synchronized {
      if (updateValues) {
        setNotifyOnChange(false)
        clear()
        values().foreach(add(_))
        setNotifyOnChange(true)
      }
      super.notifyDataSetChanged()
    }
  }
}
