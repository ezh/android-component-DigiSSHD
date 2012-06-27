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

import java.util.concurrent.atomic.AtomicInteger

import scala.actors.Futures.future
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.block.Block
import org.digimead.digi.ctrl.lib.block.Level
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDActivity
import org.digimead.digi.ctrl.sshd.SSHDCommon

import com.commonsware.cwac.merge.MergeAdapter

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.Html
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class OptionBlock(val context: Context)(implicit @transient val dispatcher: Dispatcher) extends Block[OptionBlock.Item] with Logging {
  val items = Seq(OptionBlock.asRootItem, OptionBlock.portItem, OptionBlock.rsaItem, OptionBlock.dssItem, OptionBlock.authItem)
  private lazy val header = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].
    inflate(Android.getId(context, "header", "layout"), null).asInstanceOf[TextView]
  private lazy val adapter = new OptionBlock.Adapter(context, items)
  OptionBlock.block = Some(this)
  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    header.setText(Html.fromHtml(Android.getString(context, "block_option_title").getOrElse("options")))
    mergeAdapter.addView(header)
    mergeAdapter.addAdapter(adapter)
  }
  @Loggable
  def onListItemClick(l: ListView, v: View, item: OptionBlock.Item) = item.option match {
    case DOption.AsRoot =>
      onOptionClick(item, item.getState[Boolean](context))
    case DOption.Port =>
      onListItemClickPort(l, v, item)
    case OptionBlock.rsaItemOption =>
      onOptionClick(item, item.getState[Boolean](context))
    case OptionBlock.dssItemOption =>
      onOptionClick(item, item.getState[Boolean](context))
    case OptionBlock.authItemOption =>
      onListItemClickAuth(l, v, item)
    case item =>
      log.fatal("unknown item " + item)
  }
  def onListItemClickPort(l: ListView, v: View, item: OptionBlock.Item) = SSHDActivity.activity.foreach {
    activity =>
      // leave UI thread
      future {
        AppComponent.Inner.showDialogSafe[AlertDialog](activity, "dialog_port", () => {
          val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
          val currentValue = pref.getInt(item.option.tag, 2222)
          val maxLengthFilter = new InputFilter.LengthFilter(5)
          val portLayout = LayoutInflater.from(context).inflate(R.layout.alertdialog_text, null)
          val portField = portLayout.findViewById(android.R.id.edit).asInstanceOf[EditText]
          portField.setInputType(InputType.TYPE_CLASS_NUMBER)
          portField.setText(currentValue.toString)
          portField.setFilters(Array(maxLengthFilter))
          val dialog = new AlertDialog.Builder(activity).
            setTitle(R.string.dialog_port_title).
            setMessage(Html.fromHtml(Android.getString(activity, "dialog_port_message").
              getOrElse("Select new TCP port in range from 1024 to 65535"))).
            setView(portLayout).
            setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
              def onClick(dialog: DialogInterface, whichButton: Int) = try {
                val port = portField.getText.toString.toInt
                log.debug("set port to " + port)
                val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
                val editor = pref.edit()
                editor.putInt(item.option.tag, port)
                editor.commit()
                item.view.get.foreach(view => {
                  val text = view.findViewById(android.R.id.content).asInstanceOf[TextView]
                  text.setText(port.toString)
                })
                context.sendBroadcast(new Intent(DIntent.UpdateOption, Uri.parse("code://" + context.getPackageName + "/" + item.option)))
                SSHDCommon.optionChangedOnRestartNotify(context, item.option, item.getState[Int](context).toString)
              } catch {
                case e =>
                  log.error(e.getMessage, e)
              }
            }).
            setNegativeButton(android.R.string.cancel, null).
            setIcon(android.R.drawable.ic_dialog_info).
            create()
          dialog.show()
          val ok = dialog.findViewById(android.R.id.button1)
          ok.setEnabled(false)
          portField.addTextChangedListener(new TextWatcher {
            override def afterTextChanged(s: Editable) = try {
              val rawPort = s.toString
              if (rawPort.nonEmpty) {
                val port = rawPort.toInt
                if (port > 1023 && port < 65536 && port != currentValue)
                  ok.setEnabled(true)
                else
                  ok.setEnabled(false)
              } else
                ok.setEnabled(false)
            } catch {
              case e =>
                log.warn(e.getMessage, e)
            }
            override def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
          })
          dialog
        })
      }
  }
  def onListItemClickAuth(l: ListView, v: View, item: OptionBlock.Item) = AnyBase.handler.post(new Runnable {
    def run {
      SSHDActivity.activity.foreach {
        activity =>
          AppComponent.Inner.showDialogSafe[AlertDialog](activity, "dialog_auth", () => {
            val authTypeValue = new AtomicInteger(OptionBlock.authItem.getState[Int](context))
            //activity.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE).
            //getInt(, OptionBlock.authItemOption.)
            val dialog = new AlertDialog.Builder(activity).
              setTitle(R.string.dialog_auth_title).
              setSingleChoiceItems(R.array.auth_type, authTypeValue.get - 1, new DialogInterface.OnClickListener() {
                def onClick(dialog: DialogInterface, which: Int) { authTypeValue.set(which + 1) }
              }).
              setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                def onClick(dialog: DialogInterface, whichButton: Int) {
                  log.debug("set new password")
                  val pref = activity.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
                  val editor = pref.edit()
                  editor.putInt(OptionBlock.authItemOption.tag, authTypeValue.get)
                  editor.commit()
                  OptionBlock.authItem.view.get.foreach(view => {
                    val text = view.findViewById(android.R.id.content).asInstanceOf[TextView]
                    val authType = OptionBlock.AuthType(authTypeValue.get).toString
                    Android.getString(context, "option_auth_" + authType.replaceAll(""" """, """_""")) match {
                      case Some(string) =>
                        text.setText(string)
                      case None =>
                        text.setText(authType.toLowerCase.replaceAll(""" """, "\n"))
                    }
                  })
                  context.sendBroadcast(new Intent(DIntent.UpdateOption, Uri.parse("code://" + context.getPackageName + "/" + OptionBlock.authItemOption)))
                  SSHDCommon.optionChangedOnRestartNotify(context, item.option,
                    "\"" + OptionBlock.AuthType(authTypeValue.get).toString.toLowerCase + "\"")
                }
              }).
              setNegativeButton(android.R.string.cancel, null).
              setIcon(android.R.drawable.ic_dialog_alert).
              create()
            dialog.show()
            dialog
          })
      }
    }
  })
  @Loggable
  def onOptionClick(item: OptionBlock.Item, lastState: Boolean) = item.option match {
    case DOption.AsRoot =>
      onOptionClickAsRoot(item, lastState)
    case OptionBlock.rsaItemOption =>
      onOptionClickPublicKey(item, lastState)
    case OptionBlock.dssItemOption =>
      onOptionClickPublicKey(item, lastState)
    case item =>
      log.fatal("unknown item " + item)
  }
  def onOptionClickPublicKey(item: OptionBlock.Item, lastState: Boolean) = {
    val allow = item match {
      case OptionBlock.rsaItem =>
        if (!OptionBlock.dssItem.getState[Boolean](context) && item.getState[Boolean](context))
          false // prevent RSA and DSS simultaneous shutdown
        else
          true
      case OptionBlock.dssItem =>
        if (!OptionBlock.rsaItem.getState[Boolean](context) && item.getState[Boolean](context))
          false // prevent RSA and DSS simultaneous shutdown
        else
          true
    }
    if (allow) {
      item.view.get.foreach {
        view =>
          val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
          AnyBase.handler.post(new Runnable { def run = { checkbox.setChecked(!lastState) } })
      }
      val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
      val editor = pref.edit()
      editor.putBoolean(item.option.tag, !lastState)
      editor.commit()
      context.sendBroadcast(new Intent(DIntent.UpdateOption, Uri.parse("code://" + context.getPackageName + "/" + item.option)))
      SSHDCommon.optionChangedOnRestartNotify(context, item.option, item.getState[Boolean](context).toString)
    } else {
      for {
        rsaView <- OptionBlock.rsaItem.view.get
        dssView <- OptionBlock.dssItem.view.get
      } {
        val rsaCheckbox = rsaView.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
        val dssCheckbox = dssView.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
        val message = Android.getString(context, "option_rsa_dss_at_least_one").getOrElse("at least one of the encription type must be selected from either RSA or DSA")
        AnyBase.handler.post(new Runnable {
          def run = {
            rsaCheckbox.setChecked(OptionBlock.rsaItem.getState[Boolean](context))
            dssCheckbox.setChecked(OptionBlock.dssItem.getState[Boolean](context))
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
          }
        })
      }
    }
  }
  def onOptionClickAsRoot(item: OptionBlock.Item, lastState: Boolean) = if (lastState) {
    item.view.get.foreach {
      view =>
        val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
        AnyBase.handler.post(new Runnable { def run = { checkbox.setChecked(!lastState) } })
    }
    val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
    val editor = pref.edit()
    editor.putBoolean(item.option.tag, !lastState)
    editor.commit()
    SSHDCommon.optionChangedOnRestartNotify(context, item.option, item.getState[Boolean](context).toString)
  } else {
    // leave UI thread
    future {
      SSHDActivity.activity.foreach {
        activity =>
          AppComponent.Inner.showDialogSafe[AlertDialog](activity, "dialog_root", () => {
            val dialog = new AlertDialog.Builder(activity).
              setTitle(R.string.dialog_root_title).
              setMessage(R.string.dialog_root_message).
              setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                def onClick(dialog: DialogInterface, whichButton: Int) {
                  item.view.get.foreach {
                    view =>
                      val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
                      checkbox.setChecked(!lastState)
                  }
                  val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
                  val editor = pref.edit()
                  editor.putBoolean(item.option.tag, !lastState)
                  editor.commit()
                  context.sendBroadcast(new Intent(DIntent.UpdateOption, Uri.parse("code://" + context.getPackageName + "/" + item.option)))
                  SSHDCommon.optionChangedOnRestartNotify(context, item.option, item.getState[Boolean](context).toString)
                }
              }).
              setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                def onClick(dialog: DialogInterface, whichButton: Int) {
                  item.view.get.foreach {
                    view =>
                      val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
                      checkbox.setChecked(lastState)
                  }
                }
              }).
              setIcon(R.drawable.ic_danger).
              create()
            dialog.show()
            dialog
          })
      }
    }
  }
}

object OptionBlock extends Logging {
  @volatile private var block: Option[OptionBlock] = None
  val asRootItem = Item(DOption.AsRoot.tag, DOption.AsRoot)
  val portItem = Item(DOption.Port.tag, DOption.Port)
  private val rsaItemOption = DOption.Value("rsa", classOf[Boolean], true: java.lang.Boolean)
  val rsaItem = Item("rsa", rsaItemOption)
  private val dssItemOption = DOption.Value("dss", classOf[Boolean], false: java.lang.Boolean)
  val dssItem = Item("dss", dssItemOption)
  private val authItemOption = DOption.Value("auth", classOf[Int], 1: java.lang.Integer)
  val authItem = Item("auth", authItemOption)
  log.debug("define custom rsaItem with id " + rsaItemOption.id)
  log.debug("define custom dssItem with id " + dssItemOption.id)
  log.debug("define custom authItemOption with id " + authItemOption.id)
  case class Item(val value: String, val option: DOption.OptVal) extends Block.Item {
    override def toString() = value
    def getState[T](context: Context)(implicit m: Manifest[T]): T = {
      assert(m.erasure == option.kind)
      val pref = context.getSharedPreferences(DPreference.Main, Context.MODE_PRIVATE)
      option.kind.getName match {
        case "boolean" =>
          pref.getBoolean(option.tag, option.default.asInstanceOf[Boolean]).asInstanceOf[T]
        case "int" =>
          pref.getInt(option.tag, option.default.asInstanceOf[Int]).asInstanceOf[T]
        case "java.lang.String" =>
          pref.getString(option.tag, option.default.asInstanceOf[String]).asInstanceOf[T]
        case k =>
          log.fatal("unknown option kind " + k)
          null.asInstanceOf[T]
      }
    }
  }
  class Adapter(context: Context, data: Seq[Item])
    extends ArrayAdapter[Item](context, android.R.layout.simple_list_item_1, android.R.id.text1, data.toArray) {
    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val item = data(position)
      item.view.get match {
        case None =>
          val view = item.option.kind match {
            case c if c == classOf[Boolean] =>
              // show root | RSA | DSA options
              val view = inflater.inflate(Android.getId(context, "option_list_item_multiple_choice", "layout"), null)
              val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
              checkbox.setOnTouchListener(new View.OnTouchListener {
                def onTouch(v: View, event: MotionEvent): Boolean = {
                  // don't want check for tap or TOOL_TYPE_
                  val box = v.asInstanceOf[CheckBox]
                  val lastState = box.isChecked()
                  if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    box.setPressed(true)
                    box.invalidate()
                    box.refreshDrawableState()
                    v.getRootView().postInvalidate()
                    // apply immediately
                    future { block.foreach(_.onOptionClick(item, lastState)) }
                  } else {
                    box.setPressed(false)
                    box.invalidate()
                    box.refreshDrawableState()
                    v.getRootView().postInvalidate()
                  }
                  true // yes, it is
                }
              })
              checkbox.setFocusable(false)
              checkbox.setFocusableInTouchMode(false)
              checkbox.setChecked(item.getState[Boolean](context))
              view
            case c if c == classOf[Int] =>
              val view = inflater.inflate(Android.getId(context, "option_list_item_value", "layout"), null)
              item match {
                // show port
                case OptionBlock.portItem =>
                  val value = view.findViewById(android.R.id.content).asInstanceOf[TextView]
                  value.setText(item.getState[Int](context).toString)
                // show auth type
                case OptionBlock.authItem =>
                  val value = view.findViewById(android.R.id.content).asInstanceOf[TextView]
                  val authType = AuthType(item.getState[Int](context)).toString
                  Android.getString(context, "option_auth_" + authType.replaceAll(""" """, """_""")) match {
                    case Some(string) =>
                      value.setText(string)
                    case None =>
                      value.setText(authType.toLowerCase.replaceAll(""" """, "\n"))
                  }
              }
              view
          }
          val text1 = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
          val text2 = view.findViewById(android.R.id.text2).asInstanceOf[TextView]
          text2.setVisibility(View.VISIBLE)
          text1.setText(Html.fromHtml(item.option.name(context)))
          text2.setText(Html.fromHtml(item.option.description(context)))
          item.view = new WeakReference(view)
          Level.intermediate(view)
          view
        case Some(view) =>
          view
      }
    }
  }
  object AuthType extends Enumeration {
    val None = Value("none")
    val SingleUser = Value("single user")
    val MultiUser = Value("multi user")
    val Public = Value("public key")
  }
}
