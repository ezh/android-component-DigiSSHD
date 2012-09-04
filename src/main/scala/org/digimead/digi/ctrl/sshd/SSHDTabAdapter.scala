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

import java.util.concurrent.atomic.AtomicInteger

import scala.ref.WeakReference

import org.digimead.digi.lib.ctrl.AnyBase
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.ctrl.sshd.ext.SupportFragment
import org.digimead.digi.ctrl.sshd.ext.TabInterface

import com.actionbarsherlock.app.ActionBar
import com.actionbarsherlock.app.ActionBar.Tab

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.app.FragmentTransaction
import android.support.v4.view.ViewPager

class SSHDTabAdapter(activity: FragmentActivity, val pager: WeakReference[ViewPager])
  extends FragmentPagerAdapter(activity.getSupportFragmentManager) with Logging {
  /** profiling support */
  private val ppLoading = SSHDActivity.ppGroup.start("SSHDTabAdapter")
  pager.get.foreach(pager => AnyBase.runOnUiThread {
    pager.setAdapter(this)
    pager.setOnPageChangeListener(SSHDTabAdapter.pageListener)
    pager.setCurrentItem(SSHDActivity.StateStash.initialTab)
  })
  log.debug("alive")
  ppLoading.stop

  def getCount() =
    SSHDTabAdapter.tabs.size
  def getItem(position: Int): Fragment =
    (SSHDActivity.fragments.get(SSHDTabAdapter.tabs(position).clazz.getName)).flatMap(_()).
      getOrElse(Fragment.instantiate(activity, classOf[ext.SupportFragment].getName, null))
  def onTabSelected(tab: Tab, ft: FragmentTransaction): Unit = {
    val tag = tab.getTag
    for { index <- 0 until SSHDTabAdapter.tabs.size }
      if (SSHDTabAdapter.tabs(index).clazz == tag) {
        // hide help
        SSHDHelp.hide()
        // clear backstack
        Option(getItem(SSHDTabAdapter.selected.get).getFragmentManager).foreach(
          _.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE))
        // select new tab
        SSHDTabAdapter.selected.set(index)
        pager.get.foreach(_.setCurrentItem(index))
        return
      }
    log.fatal("tab not found " + tab.getTag)
  }
  def onTabUnselected(tab: Tab, ft: FragmentTransaction) {}
  def onTabReselected(tab: Tab, ft: FragmentTransaction) {}
}

object SSHDTabAdapter extends Logging {
  /** profiling support */
  private val ppLoading = SSHDActivity.ppGroup.start("SSHDTabAdapter")
  /** SSHDTabAdapter depended on SSHDActivity.activity */
  @volatile private var adapter: Option[SSHDTabAdapter] = None
  /** tab pool */
  private var tabs = Seq[Tab]()
  /** current tab */
  private val selected = new AtomicInteger(0)
  /**
   * update ViewPager
   * @see android.app.ActionBar.TabListener
   */
  private lazy val tabListener = new ActionBar.TabListener {
    def onTabReselected(tab: ActionBar.Tab, ft: FragmentTransaction) {}
    override def onTabSelected(tab: ActionBar.Tab, ft: FragmentTransaction) =
      adapter.foreach(_.onTabSelected(tab, ft))
    def onTabUnselected(tab: ActionBar.Tab, ft: FragmentTransaction) {}
  }
  /**
   * update ActionBar
   * @see android.support.v4.view.ViewPager.OnPageChangeListener
   */
  private lazy val pageListener = new ViewPager.OnPageChangeListener {
    def onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
    def onPageSelected(position: Int) = {
      adapter.foreach {
        adapter =>
          if (adapter.getCount > position)
            adapter.getItem(position).asInstanceOf[TabInterface].onTabSelected
      }
      SSHDActivity.activity.foreach {
        activity =>
          if (activity.getSupportActionBar().getTabCount() > position)
            activity.getSupportActionBar.setSelectedNavigationItem(position)
      }
    }
    def onPageScrollStateChanged(state: Int) {}
  }
  log.debug("alive")
  ppLoading.stop

  def addTab(bar: ActionBar, tabTitleResource: Int, clazz: Class[_ <: TabInterface], args: Bundle) = synchronized {
    tabs = tabs :+ new Tab(tabTitleResource, clazz, args)(new WeakReference(bar))
    AnyBase.runOnUiThread { adapter.foreach(_.notifyDataSetChanged) }
  }
  def getSelected() = selected.get
  def getSelectedTab() = tabs(selected.get)
  def getSelectedFragment(): Option[Fragment with TabInterface] =
    adapter.map(_.getItem(selected.get).asInstanceOf[Fragment with TabInterface])
  def setSelected(n: Int) = adapter.foreach(_.pager.get.foreach(_.setCurrentItem(n)))
  /**
   * called on SSHDActivity.onCreate
   */
  def onCreate(activity: SSHDActivity) = synchronized {
    adapter = Some(new SSHDTabAdapter(activity, new WeakReference(activity.findViewById(R.id.main_viewpager).asInstanceOf[ViewPager])))
    AnyBase.runOnUiThread {
      var tabCounter = 0
      val bar = activity.getSupportActionBar
      tabs.foreach { item =>
        val tab = item.acquire(bar)
        if (tabCounter == SSHDActivity.StateStash.initialTab)
          bar.selectTab(tab)
        tabCounter += 1
      }
    }
  }
  case class Tab(titleResource: Int, clazz: Class[_ <: TabInterface], args: Bundle)(private var cachedBar: WeakReference[ActionBar]) {
    @volatile private var cachedTab = new WeakReference[ActionBar.Tab](null)
    AnyBase.runOnUiThread { cachedBar.get.foreach(acquire) }

    def acquire(bar: ActionBar): ActionBar.Tab = synchronized {
      cachedTab.get match {
        case Some(tab) if cachedBar.get == Some(bar) => tab
        case _ =>
          val newTab = bar.newTab.setText(titleResource)
          cachedTab = new WeakReference(newTab)
          cachedBar = new WeakReference(bar)
          newTab.setTag(clazz)
          newTab.setTabListener(tabListener)
          bar.addTab(newTab)
          newTab
      }
    }
  }
}

