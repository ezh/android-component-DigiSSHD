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

import java.nio.ByteBuffer
import java.nio.ByteOrder
import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.WeakHashMap
import scala.ref.WeakReference
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.log.Logging
import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.opengl.GLU
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import java.util.zip.ZipException
import java.io.IOException
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.Button

class SSHDHelp(context: Context, attrs: AttributeSet) extends GLSurfaceView(context, attrs) with Logging {
  lazy val renderer = new SSHDHelp.Renderer(new WeakReference(this))

  override def onTouchEvent(event: MotionEvent): Boolean = {
    if (event.getAction() == MotionEvent.ACTION_DOWN)
      renderer.findHelpID(event).foreach(area => SSHDHelp.showHelp(area.id))
    //queueEvent(new Runnable() { def run() {} })
    true
  }
  @Loggable
  def toggle() = if (isShown()) {
    setVisibility(View.GONE)
    renderer.deinit()
  } else {
    setVisibility(View.VISIBLE)
    requestFocus()
  }
  @Loggable
  def hide() = if (isShown()) toggle
  @Loggable
  def show() = if (!isShown()) toggle
  override def onPause() {
    super.onPause()
    renderer.deinit()
  }
  @Loggable
  private def init() {
    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
    getHolder.setFormat(PixelFormat.RGBA_8888)
    getHolder.setFormat(PixelFormat.TRANSLUCENT)
    setZOrderOnTop(true)
    setRenderer(renderer)
    setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)
  }
}

object SSHDHelp extends Logging {
  @volatile private var controls: Option[Controls] = None
  private val elements = new WeakHashMap[View, String] with SynchronizedMap[View, String]

  def register(view: View, helpID: String) {
    if (view == null) {
      AppComponent.Context.foreach(context => log.warn("view not found for help id \"" + helpID + "\""))
      return
    }
    elements(view) = helpID
  }
  @Loggable
  def showHelp(helpID: String) = for {
    controls <- controls
    control <- controls.control.get
    content <- controls.content.get
    contentDisplay <- controls.contentDisplay.get
    contentOk <- controls.contentOk.get
  } {
    control.hide()
    content.setVisibility(View.VISIBLE)
    contentDisplay.loadUrl("content://org.digimead.digi.ctrl.sshd.help/help_" + helpID + ".html")
  }
  @Loggable
  def toggle(activity: SSHDActivity) {
    controls match {
      case Some(controls) =>
        if (!isShown) {
          controls.control.get.foreach(_.show)
        } else {
          controls.control.get.foreach(_.hide)
          controls.content.get.foreach(_.setVisibility(View.GONE))
        }
      case None =>
        val control = activity.findViewById(R.id.main_help).asInstanceOf[SSHDHelp]
        val content = activity.findViewById(R.id.main_help_content).asInstanceOf[LinearLayout]
        val contentDisplay = activity.findViewById(R.id.main_help_content_wv).asInstanceOf[WebView]
        val contentOk = activity.findViewById(R.id.main_help_content_ok).asInstanceOf[Button]
        controls = Some(Controls(new WeakReference(control), new WeakReference(content),
          new WeakReference(contentDisplay), new WeakReference(contentOk)))
        controls.foreach(_.init)
        control.toggle
    }
  }
  @Loggable
  def show(activity: SSHDActivity) = if (!isShown) toggle(activity)
  @Loggable
  def hide(activity: SSHDActivity) = if (isShown) toggle(activity)
  @Loggable
  def isShown() = {
    for {
      controls <- controls
      control <- controls.control.get
      content <- controls.content.get
    } yield (control.isShown() || content.isShown())
  } getOrElse false
  @Loggable
  def onResume() = controls.flatMap(_.control.get.map(_.onResume))
  @Loggable
  def onPause() = controls.flatMap(_.control.get.map(_.onPause))
  @Loggable
  def onDestroy() = controls = None
  @Loggable
  def onClickContentOk() = for {
    controls <- controls
    control <- controls.control.get
    content <- controls.content.get
  } {
    content.setVisibility(View.GONE)
    control.show
  }
  private[SSHDHelp] class Renderer(val container: WeakReference[View]) extends GLSurfaceView.Renderer with Logging {
    private val indicesArray = Array[Short](0, 1, 2)
    private val nrOfVertices = 3
    private val (indexBuffer, vertexBuffer) = {
      // float has 4 bytes
      val vbb = ByteBuffer.allocateDirect(nrOfVertices * 3 * 4)
      vbb.order(ByteOrder.nativeOrder())
      val vertexBuffer = vbb.asFloatBuffer()
      // short has 2 bytes
      val ibb = ByteBuffer.allocateDirect(nrOfVertices * 2)
      ibb.order(ByteOrder.nativeOrder())
      val indexBuffer = ibb.asShortBuffer()
      val coords = Array[Float](
        -10f, -10f, 0f, // (x1, y1, z1)
        10f, -10f, 0f, // (x2, y2, z2)
        0f, 10f, 0f // (x3, y3, z3)
        )
      vertexBuffer.put(coords)
      indexBuffer.put(indicesArray)
      (indexBuffer.position(0), vertexBuffer.position(0))
    }
    private val location = new Array[Int](2)
    @volatile private var areas: Seq[Area] = Seq()
    private var viewScreenX = 0
    private var viewScreenY = 0
    private var viewWidth = 0
    private var viewHeight = 0

    def onSurfaceCreated(gl: GL10, config: EGLConfig) {
      gl.glClearColor(0, 0, 0, 0)
      gl.glDisable(GL10.GL_DEPTH_TEST)
      gl.glDisable(GL10.GL_DITHER)
      gl.glShadeModel(GL10.GL_SMOOTH)
      gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
      gl.glEnableClientState(GL10.GL_COLOR_ARRAY)
    }
    def onSurfaceChanged(gl: GL10, w: Int, h: Int) {
      container.get.foreach {
        container =>
          container.getLocationOnScreen(location)
          viewScreenX = location(0)
          viewScreenY = location(1)
      }
      gl.glViewport(0, 0, w, h)
      viewWidth = w
      viewHeight = h
      deinit()
      init()
    }
    def onDrawFrame(gl: GL10) {
      Thread.sleep(20)
      gl.glViewport(0, 0, viewWidth, viewHeight)
      gl.glMatrixMode(GL10.GL_PROJECTION)
      gl.glLoadIdentity()
      GLU.gluOrtho2D(gl, 0f, viewWidth, viewHeight, 0f)
      gl.glMatrixMode(GL10.GL_MODELVIEW)
      gl.glLoadIdentity()
      gl.glClear(GL10.GL_COLOR_BUFFER_BIT)
      // set the color of our element
      gl.glColor4f(0.5f, 0f, 0f, 0.5f)
      // define the vertices we want to draw
      gl.glVertexPointer(3, GL10.GL_FLOAT, 0, vertexBuffer)
      // draw
      var shiftx = 0
      var shifty = 0
      areas.foreach {
        area =>
          // relative shift from last xC.yC
          shiftx = 0 - shiftx + area.xC
          shifty = 0 - shifty + area.yC
          // finally draw the vertices
          gl.glTranslatef(shiftx, shifty, 0)
          gl.glDrawElements(GL10.GL_TRIANGLES, nrOfVertices, GL10.GL_UNSIGNED_SHORT, indexBuffer)
          // absolute shift from 0.0
          shiftx = area.xC
          shifty = area.yC
      }
    }
    @Loggable
    def findHelpID(event: MotionEvent): Option[Area] = areas.find {
      case Area(x1: Int, x2: Int, xC: Int, y1: Int, y2: Int, yC: Int, id: String) =>
        val clickX = event.getX()
        val clickY = event.getY()
        if (clickX < x1 || clickX > x2)
          false
        else if (clickY < y1 || clickY > y2)
          false
        else
          true
    }
    @Loggable
    def init() = synchronized {
      if (areas.isEmpty) {
        SSHDHelp.elements.foreach {
          case (element, id) =>
            if (element.isShown()) {
              element.getLocationOnScreen(location)
              val elementX1 = location(0) - viewScreenX
              val elementX2 = elementX1 + element.getWidth()
              val elementY1 = location(1) - viewScreenY
              val elementY2 = elementY1 + element.getHeight()
              if (((elementX1 >= 0 && elementX1 <= viewWidth && elementY1 >= 0 && elementY1 <= viewHeight) ||
                (elementX2 >= 0 && elementX2 <= viewWidth && elementY2 >= 0 && elementY2 <= viewHeight)) &&
                elementX1 != elementX2 && elementY1 != elementY2) {
                //log.___glance("!" + element.getId() + " X1 " + elementX1 + " X2 " + elementX2 + " Y1 " + elementY1 + " Y2 " + elementY2)
                val xC = (elementX1 + elementX2) / 2
                val yC = (elementY1 + elementY2) / 2
                areas = areas :+ Area(elementX1, elementX2, xC, elementY1, elementY2, yC, id)
              }
            }
        }
      }
    }
    @Loggable
    def deinit() = synchronized {
      areas = Seq()
    }
  }
  /**
   * area in GLSurfaceView
   * x1 - left top
   * y1 - left top
   * x2 - right bottom
   * x2 - right bottom
   */
  case class Area(val x1: Int, val x2: Int, val xC: Int, val y1: Int, val y2: Int, val yC: Int, val id: String)
  case class Controls(val control: WeakReference[SSHDHelp], val content: WeakReference[LinearLayout],
    val contentDisplay: WeakReference[WebView], contentOk: WeakReference[Button]) {
    def init() = for {
      control <- control.get
      content <- content.get
      contentDisplay <- contentDisplay.get
      contentOk <- contentOk.get
    } {
      control.init
      contentOk.setOnClickListener(new View.OnClickListener {
        def onClick(v: View) = SSHDHelp.onClickContentOk
      })
    }
  }
}