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

package org.digimead.digi.ctrl.sshd.session

import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import scala.Option.option2Iterable
import scala.actors.Actor
import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.declaration.DConnection
import org.digimead.digi.ctrl.lib.declaration.DProvider
import org.digimead.digi.ctrl.lib.info.ComponentInfo
import org.digimead.digi.ctrl.lib.info.ExecutableInfo
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.sshd.R

import android.app.Activity
import android.database.Cursor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SimpleCursorAdapter
import android.widget.TextView

class SessionAdapter(context: Activity, layout: Int)
  extends SimpleCursorAdapter(context, layout, null, Array[String](), Array[Int]()) with Logging {
  private var inflater: LayoutInflater = context.getLayoutInflater
  private[session] val item = new HashMap[Int, SessionBlock.Item] with SynchronizedMap[Int, SessionBlock.Item]
  private val v = android.os.Build.VERSION.SDK.toInt
  SessionAdapter.adapter.set(new WeakReference(this))

  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    val cursor = getCursor()
    if (!cursor.moveToPosition(position))
      throw new IllegalStateException("couldn't move cursor to position " + position)
    val id = cursor.getInt(DProvider.Field.ID.id)
    item.get(id).flatMap(_.view.get) match {
      case None =>
        val view = inflater.inflate(layout, null)
        getItem(cursor).foreach {
          item =>
            val title = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
            val description = view.findViewById(android.R.id.text2).asInstanceOf[TextView]
            val subinfo = view.findViewById(android.R.id.message).asInstanceOf[TextView]
            val kind = view.findViewById(android.R.id.icon1).asInstanceOf[ImageView]
            val state = view.findViewById(android.R.id.button1).asInstanceOf[ImageButton]
            val ip = InetAddress.getByAddress(BigInt(item.connection.remoteIP).toByteArray)
            kind.setBackgroundDrawable(context.getResources.getDrawable(R.drawable.ic_launcher))
            title.setText(Android.getString(context, "session_title").getOrElse("%1$s to %2$s").
              format(ip.getHostAddress, item.executable.name))
            description.setText(Android.getString(context, "session_description").getOrElse("%1$s").
              format(item.component.name))
            state.setFocusable(false)
            state.setFocusableInTouchMode(false)
            subinfo.setText(Android.getString(context, "time_minutes").getOrElse("%02dm %02ds").format(0, 0))
            val processID = cursor.getInt(DProvider.Field.ProcessID.id)
            item.durationField = new WeakReference(subinfo)
            item.view = new WeakReference(view)
            item.position = Some(position)
        }
        view
      case Some(view) =>
        view
    }
  }
  override def getItem(position: Int): AnyRef = {
    val c = super.getItem(position).asInstanceOf[Cursor]
    getItem(c).getOrElse({ log.fatal("session item lost"); c })
  }
  def getItem(cursor: Cursor): Option[SessionBlock.Item] = {
    val key = cursor.getInt(DProvider.Field.ID.id)
    item.get(key) match {
      case Some(item) =>
        Option(item)
      case None =>
        val processID = cursor.getInt(DProvider.Field.ProcessID.id)
        (for {
          component <- Option(if (v < 11)
            cursor.getString(DProvider.Field.Component.id).getBytes("ISO-8859-1")
          else
            cursor.getBlob(DProvider.Field.Component.id)).flatMap(p => Common.unparcelFromArray[ComponentInfo](p))
          executable <- Option(if (v < 11)
            cursor.getString(DProvider.Field.Executable.id).getBytes("ISO-8859-1")
          else
            cursor.getBlob(DProvider.Field.Executable.id)).flatMap(p => Common.unparcelFromArray[ExecutableInfo](p))
          connection <- Option(if (v < 11)
            cursor.getString(DProvider.Field.Connection.id).getBytes("ISO-8859-1")
          else
            cursor.getBlob(DProvider.Field.Connection.id)).flatMap(p => Common.unparcelFromArray[DConnection](p))
        } yield {
          item(key) = new SessionBlock.Item(key, processID, component, executable, connection)
          item(key)
        })
    }
  }
  @Loggable
  def items: Seq[SessionBlock.Item] = synchronized {
    val cursor = getCursor
    val pos = cursor.getPosition
    val result = if (cursor.getCount != 0 && cursor.moveToFirst) {
      var result = Seq(getItem(cursor))
      while (cursor.moveToNext)
        result = result :+ getItem(cursor)
      result.flatten
    } else Seq()
    cursor.moveToPosition(pos)
    result
  }
  @Loggable
  override def changeCursor(cursor: Cursor): Unit = synchronized {
    try {
      super.changeCursor(cursor)
      if (cursor.getCount != 0) {
        val pos = cursor.getPosition
        if (cursor.moveToFirst) {
          val existsIDs = HashSet[Int](item.keys.toSeq: _*)
          do { existsIDs.remove(cursor.getInt(DProvider.Field.ID.id)) } while (cursor.moveToNext)
          existsIDs.foreach(n => {
            log.debug("remove item " + n)
            item.remove(n)
          })
          cursor.moveToPosition(pos)
        }
      } else
        item.clear
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
}

object SessionAdapter extends Actor with Logging {
  private val adapter = new AtomicReference(new WeakReference[SessionAdapter](null))
  private val jscheduler = Executors.newSingleThreadScheduledExecutor()

  AppActivity.LazyInit("start session.SessionAdapter actor and heartbeat") {
    start
    schedule(1000)
  }
  log.debug("alive")
  private def schedule(duration: Int) =
    jscheduler.scheduleAtFixedRate(new Runnable { def run { SessionAdapter.this ! () } }, 0, duration, TimeUnit.MILLISECONDS)
  def act = {
    loop {
      react {
        case _ =>
          adapter.get.get match {
            case Some(adapter) =>
              val waiting = adapter.item.values.flatMap(updateDurationText)
              if (waiting.nonEmpty) {
                waiting.head._1.getRootView.post(new Runnable() {
                  def run() = waiting.foreach(t => {
                    t._1.setText(t._2)
                    t._1.requestLayout
                  })
                })
                waiting.head._1.postInvalidate
              }
            case None =>
          }
      }
    }
  }
  private def updateDurationText(item: SessionBlock.Item): Option[(TextView, String)] = item.durationField.get.flatMap {
    durationField =>
      if (durationField.getVisibility == View.VISIBLE) {
        val time = System.currentTimeMillis - item.connection.timestamp
        val seconds = ((time / 1000) % 60).toInt
        val minutes = ((time / (1000 * 60)) % 60).toInt
        val hours = ((time / (1000 * 60 * 60))).toInt
        val text = if (time < 1000 * 60 * 60)
          Android.getString(durationField.getContext, "time_minutes").getOrElse("%02dm %02ds").format(minutes, seconds)
        else
          Android.getString(durationField.getContext, "time_hours").getOrElse("%dh %02dm %02ds").format(hours, minutes, seconds)
        Some((durationField, text))
      } else
        None
  }
}
