package org.digimead.digi.ctrl.sshd.info

import scala.actors.Futures

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.block.CommunityBlock
import org.digimead.digi.ctrl.lib.block.LegalBlock
import org.digimead.digi.ctrl.lib.block.SupportBlock
import org.digimead.digi.ctrl.lib.block.ThanksBlock
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.sshd.Message.dispatcher
import org.digimead.digi.ctrl.sshd.R
import org.digimead.digi.ctrl.sshd.SSHDActivity

import com.actionbarsherlock.app.SherlockListFragment
import com.commonsware.cwac.merge.MergeAdapter

import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView

class TabActivity extends SherlockListFragment with Logging {
  @Loggable
  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = SSHDActivity.ppGroup("info.TabActivity.onCreateView") {
    val view = inflater.inflate(R.layout.tab_info, null)
    val context = view.getContext
    // prepare empty view
    // interfaces
    val interfacesHeader = view.findViewById(Android.getId(context, "nodata_header_interface")).asInstanceOf[TextView]
    interfacesHeader.setText(Html.fromHtml(Android.getString(context, "block_interface_title").getOrElse("interfaces")))
    // community
    val communityHeader = view.findViewById(Android.getId(context, "nodata_header_community")).asInstanceOf[TextView]
    communityHeader.setText(Html.fromHtml(Android.getString(context, "block_community_title").getOrElse("community")))
    // support
    val supportHeader = view.findViewById(Android.getId(context, "nodata_header_support")).asInstanceOf[TextView]
    supportHeader.setText(Html.fromHtml(Android.getString(context, "block_support_title").getOrElse("support")))
    // legal
    val legalHeader = view.findViewById(Android.getId(context, "nodata_header_legal")).asInstanceOf[TextView]
    legalHeader.setText(Html.fromHtml(Android.getString(context, "block_legal_title").getOrElse("legal")))
    view
  }
  @Loggable
  override def onActivityCreated(savedInstanceState: Bundle) = SSHDActivity.ppGroup("info.TabActivity.onActivityCreated") {
    super.onActivityCreated(savedInstanceState);
    setListAdapter(TabActivity.adapter)
  }
  @Loggable
  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) {
    //   val frag = getFragmentManager().findFragmentById(R.id.frag_details_webview).asInstanceOf[DetailsWebFragment]
    // frag.updateDetails(mListItemsUrls[position]);
  }
}

object TabActivity {
  /** profiling support */
  private val ppLoading = SSHDActivity.ppGroup.start("info.TabActivity$")
  val legal = """<img src="ic_launcher">The DigiSSHD Project is licensed to you under the terms of the
GNU General Public License (GPL) version 3 or later, a copy of which has been included in the LICENSE file.
Please check the individual source files for details. <br/>
Copyright © 2011-2012 Alexey B. Aksenov/Ezh. All rights reserved."""
  val CoreutilsURL = "http://www.gnu.org/software/coreutils/"
  val GrepURL = "http://www.gnu.org/software/grep/"
  @volatile private[info] var activity: Option[TabActivity] = None
  @volatile private[info] lazy val adapter: MergeAdapter = {
    val adapter = new MergeAdapter()
    //interfaceBlock appendTo (adapter)
    communityBlock.foreach(_ appendTo (adapter))
    supportBlock.foreach(_ appendTo (adapter))
    //thanksBlock appendTo (adapter)
    legalBlock.foreach(_ appendTo (adapter))
    adapter
  }
  //@volatile private var interfaceBlock: Option[InterfaceBlock] = None
  @volatile private lazy val supportBlock: Option[SupportBlock] = AppComponent.Context.map(context =>
    new SupportBlock(context.getApplicationContext,
      Futures.future[Uri] { Uri.parse(SSHDActivity.info.project) },
      Futures.future[Uri] { Uri.parse(SSHDActivity.info.project + "/issues") },
      Futures.future[String] { SSHDActivity.info.email },
      Futures.future[String] { SSHDActivity.info.name },
      Futures.future[String] { "+18008505240" },
      Futures.future[String] { "ezhariur" },
      Futures.future[String] { "413030952" }))
  @volatile private lazy val communityBlock: Option[CommunityBlock] = AppComponent.Context.map(context =>
    new CommunityBlock(context.getApplicationContext,
      Futures.future[Uri] { Uri.parse("http://forum.xda-developers.com/showthread.php?t=1612044") },
      Futures.future[Uri] { Uri.parse(SSHDActivity.info.project + "/wiki") },
      Futures.future[Uri] { Uri.parse(SSHDActivity.info.translation) },
      Futures.future[Uri] { Uri.parse(SSHDActivity.info.translationCommon) }))
  @volatile private lazy val thanksBlock: Option[ThanksBlock] = AppComponent.Context.map(context =>
    new ThanksBlock(context.getApplicationContext))
  @volatile private lazy val legalBlock: Option[LegalBlock] = AppComponent.Context.map(context =>
    new LegalBlock(context.getApplicationContext,
      List(Futures.future[LegalBlock.Item] { LegalBlock.Item(legal)("https://github.com/ezh/android-component-DigiSSHD/blob/master/LICENSE") })))
  ppLoading.stop
}