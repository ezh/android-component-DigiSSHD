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

import scala.actors.Futures.future
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.block.Block
import org.digimead.digi.ctrl.lib.declaration.DOption.OptVal.value2string_id
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DOption
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDActivity

import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.text.method.LinkMovementMethod
import android.text.Editable
import android.text.Html
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.ViewGroup.LayoutParams
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView

class OptionBlock(val context: Activity)(implicit @transient val dispatcher: Dispatcher) extends Block[OptionBlock.Item] with Logging {
  val items = Seq(OptionBlock.Item(DOption.AsRoot, DOption.AsRoot), OptionBlock.Item(DOption.Port, DOption.Port))
  private lazy val header = context.getLayoutInflater.inflate(Android.getId(context, "header", "layout"), null).asInstanceOf[TextView]
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
      item.view.get.foreach {
        view =>
          val checkbox = view.findViewById(android.R.id.checkbox).asInstanceOf[CheckBox]
          val lastState = checkbox.isChecked
          context.runOnUiThread(new Runnable { def run = checkbox.setChecked(!lastState) })
          onOptionClick(item, lastState)
      }
    case DOption.Port =>
      context.runOnUiThread(new Runnable {
        def run {
          SSHDActivity.activity.foreach {
            activity =>
              val container = new ScrollView(activity)
              val layout = new LinearLayout(activity)
              layout.setOrientation(LinearLayout.VERTICAL)
              val padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, activity.getResources.getDisplayMetrics).toInt
              layout.setPadding(padding, padding, padding, padding)
              val message = new TextView(activity)
              message.setMovementMethod(LinkMovementMethod.getInstance())
              message.setPadding(0, 0, 0, padding)
              message.setText(Html.fromHtml(Android.getString(activity, "dialog_port_message").getOrElse("Select new TCP port in range from 1024 to 32767")))
              layout.addView(message, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
              val pref = context.getSharedPreferences(item.option, Context.MODE_WORLD_READABLE)
              val input = new EditText(activity)
              val maxLengthFilter = new InputFilter.LengthFilter(5)
              val currentValue = pref.getInt(item.option, 2222)
              input.setPadding(0, padding, 0, padding)
              input.setId(Int.MaxValue)
              input.setInputType(InputType.TYPE_CLASS_NUMBER)
              input.setText(currentValue.toString)
              input.setFilters(Array(maxLengthFilter))
              layout.addView(input, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
              container.addView(layout)
              // leave UI thread
              future {
                AppActivity.Inner.showDialogSafe[AlertDialog](activity, () => {
                  val dialog = new AlertDialog.Builder(activity).
                    setTitle(R.string.dialog_port_title).
                    setView(container).
                    setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                      def onClick(dialog: DialogInterface, whichButton: Int) = try {
                        val port = input.getText.toString.toInt
                        log.debug("set port to " + port)
                        val pref = context.getSharedPreferences(item.option, Context.MODE_WORLD_READABLE)
                        val editor = pref.edit()
                        editor.putInt(item.option, port)
                        editor.commit()
                        item.view.get.foreach(view => {
                          val text = view.findViewById(android.R.id.content).asInstanceOf[TextView]
                          text.setText(port.toString)
                        })
                        context.sendBroadcast(new Intent(DIntent.UpdateOption, Uri.parse("code://" + context.getPackageName + "/" + item.option)))
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
                  input.addTextChangedListener(new TextWatcher {
                    override def afterTextChanged(s: Editable) = try {
                      val rawPort = s.toString
                      if (rawPort.nonEmpty) {
                        val port = rawPort.toInt
                        if (port > 1023 && port < 32768 && port != currentValue)
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
        }
      })
  }
  @Loggable
  def onOptionClick(item: OptionBlock.Item, lastState: Boolean) = item.option match {
    case DOption.AsRoot =>
      if (lastState) {
        val pref = context.getSharedPreferences(item.option, Context.MODE_WORLD_READABLE)
        val editor = pref.edit()
        editor.putBoolean(item.option, !lastState)
        editor.commit()
      } else {
        // leave UI thread
        future {
          SSHDActivity.activity.foreach {
            activity =>
              AppActivity.Inner.showDialogSafe[AlertDialog](activity, () => {
                val dialog = new AlertDialog.Builder(activity).
                  setTitle(R.string.dialog_root_title).
                  setMessage(R.string.dialog_root_message).
                  setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    def onClick(dialog: DialogInterface, whichButton: Int) {
                      val pref = context.getSharedPreferences(item.option, Context.MODE_WORLD_READABLE)
                      val editor = pref.edit()
                      editor.putBoolean(item.option, !lastState)
                      editor.commit()
                      context.sendBroadcast(new Intent(DIntent.UpdateOption, Uri.parse("code://" + context.getPackageName + "/" + item.option)))
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
    case _ =>
      val pref = context.getSharedPreferences(item.option, Context.MODE_WORLD_READABLE)
      val editor = pref.edit()
      editor.putBoolean(item.option, !lastState)
      editor.commit()
      context.sendBroadcast(new Intent(DIntent.UpdateOption, Uri.parse("code://" + context.getPackageName + "/" + item.option)))
  }
}

object OptionBlock extends Logging {
  @volatile private var block: Option[OptionBlock] = None
  case class Item(val value: String, val option: DOption.OptVal) extends Block.Item {
    override def toString() = value
    def getState(context: Context): Boolean = {
      val pref = context.getSharedPreferences(option, Context.MODE_WORLD_READABLE)
      pref.getBoolean(option, false)
    }
  }
  class Adapter(context: Activity, data: Seq[Item])
    extends ArrayAdapter[Item](context, android.R.layout.simple_list_item_1, android.R.id.text1, data.toArray) {
    private var inflater: LayoutInflater = context.getLayoutInflater
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val item = data(position)
      item.view.get match {
        case None =>
          val view = item.option.kind match {
            case c if c == classOf[Boolean] =>
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
                    box.setChecked(!lastState)
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
              checkbox.setChecked(item.getState(context))
              view
            case c if c == classOf[Int] =>
              val view = inflater.inflate(Android.getId(context, "option_list_item_value", "layout"), null)
              val value = view.findViewById(android.R.id.content).asInstanceOf[TextView]
              val pref = context.getSharedPreferences(item.option, Context.MODE_WORLD_READABLE)
              value.setText(pref.getInt(item.option, 2222).toString)
              view
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
