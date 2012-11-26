/**
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.hadoop.yarn.server.resourcemanager.webapp;

import static org.apache.hadoop.yarn.util.StringHelper.join;
import static org.apache.hadoop.yarn.webapp.YarnWebParams.APP_STATE;
import static org.apache.hadoop.yarn.webapp.view.JQueryUI.C_PROGRESSBAR;
import static org.apache.hadoop.yarn.webapp.view.JQueryUI.C_PROGRESSBAR_VALUE;

import java.util.Collection;
import java.util.HashSet;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.RMApp;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.RMAppState;
import org.apache.hadoop.yarn.server.resourcemanager.webapp.dao.AppInfo;
import org.apache.hadoop.yarn.util.Times;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet.TABLE;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet.TBODY;
import org.apache.hadoop.yarn.webapp.view.HtmlBlock;
import org.apache.hadoop.yarn.webapp.view.JQueryUI.Render;

import com.google.inject.Inject;

class AppsBlock extends HtmlBlock {
  final AppsList list;

  @Inject AppsBlock(AppsList list, ViewContext ctx) {
    super(ctx);
    this.list = list;
  }

  @Override public void render(Block html) {
    TBODY<TABLE<Hamlet>> tbody = html.
      table("#apps").
        thead().
          tr().
            th(".id", "ID").
            th(".user", "User").
            th(".name", "Name").
            th(".queue", "Queue").
            th(".starttime", "StartTime").
            th(".finishtime", "FinishTime").
            th(".state", "State").
            th(".finalstatus", "FinalStatus").
            th(".progress", "Progress").
            th(".ui", "Tracking UI")._()._().
        tbody();
    int i = 0;
    Collection<RMAppState> reqAppStates = null;
    String reqStateString = $(APP_STATE);
    if (reqStateString != null && !reqStateString.isEmpty()) {
      String[] appStateStrings = reqStateString.split(",");
      reqAppStates = new HashSet<RMAppState>(appStateStrings.length);
      for(String stateString : appStateStrings) {
        reqAppStates.add(RMAppState.valueOf(stateString));
      }
    }
    StringBuilder appsTableData = new StringBuilder("[\n");
    for (RMApp app : list.apps.values()) {
      if (reqAppStates != null && !reqAppStates.contains(app.getState())) {
        continue;
      }
      AppInfo appInfo = new AppInfo(app, true);
      String percent = String.format("%.1f", appInfo.getProgress());
      //AppID numerical value parsed by parseHadoopID in yarn.dt.plugins.js
      appsTableData.append("[\"<a href='")
      .append(url("app", appInfo.getAppId())).append("'>")
      .append(appInfo.getAppId()).append("</a>\",\"")
      .append(StringEscapeUtils.escapeHtml(appInfo.getUser()))
      .append("\",\"")
      .append(StringEscapeUtils.escapeHtml(appInfo.getName()))
      .append("\",\"")
      .append(StringEscapeUtils.escapeHtml(appInfo.getQueue()))
      .append("\",\"")
      .append(appInfo.getStartTime()).append("\",\"")
      .append(appInfo.getFinishTime()).append("\",\"")
      .append(appInfo.getState()).append("\",\"")
      .append(appInfo.getFinalStatus()).append("\",\"")
      // Progress bar
      .append("<br title='").append(percent)
      .append("'> <div class='").append(C_PROGRESSBAR).append("' title='")
      .append(join(percent, '%')).append("'> ").append("<div class='")
      .append(C_PROGRESSBAR_VALUE).append("' style='")
      .append(join("width:", percent, '%')).append("'> </div> </div>")
      .append("\",\"<a href='");

      String trackingURL =
        !appInfo.isTrackingUrlReady()? "#" : appInfo.getTrackingUrlPretty();
      
      appsTableData.append(trackingURL).append("'>")
      .append(appInfo.getTrackingUI()).append("</a>\"],\n");

      if (list.rendering != Render.HTML && ++i >= 20) break;
    }
    if(appsTableData.charAt(appsTableData.length() - 2) == ',') {
      appsTableData.delete(appsTableData.length()-2, appsTableData.length()-1);
    }
    appsTableData.append("]");
    html.script().$type("text/javascript").
    _("var appsTableData=" + appsTableData)._();

    tbody._()._();

    if (list.rendering == Render.JS_ARRAY) {
      echo("<script type='text/javascript'>\n",
           "var appsData=");
      list.toDataTableArrays(reqAppStates, writer());
      echo("\n</script>\n");
    }
  }
}
