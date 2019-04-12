// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.clientmetrics;

import com.yahoo.messagebus.Reply;
import com.yahoo.metrics.Metric;
import com.yahoo.metrics.MetricSet;
import com.yahoo.metrics.SumMetric;

import java.util.HashMap;
import java.util.Map;

/**
* @author thomasg
*/
public class RouteMetricSet extends MetricSet {

    private final SumMetric sum;
    private final ProgressCallback callback;
    private final Map<Integer, MessageTypeMetricSet> typeMap = new HashMap<>();

    public interface ProgressCallback {
        void onProgress(RouteMetricSet route);
        void done(RouteMetricSet route);
    }

    public RouteMetricSet(String route, ProgressCallback callback) {
        super(route, "", "Messages sent to the named route", null);
        sum = new SumMetric("total", "", "All kinds of messages sent to the given route", this);
        this.callback = callback;
    }

    @Override
    public String getXMLTag() {
        return "route";
    }

    private RouteMetricSet(RouteMetricSet source, CopyType copyType, MetricSet owner, boolean includeUnused) {
        super(source, copyType, owner, includeUnused);
        sum = null;
        callback = null;
    }

    public void addReply(Reply r) {
        MessageTypeMetricSet type = typeMap.get(r.getMessage().getType());
        if (type == null) {
            String msgName = r.getMessage().getClass().getSimpleName().replace("Message", "");
            type = new MessageTypeMetricSet(msgName, this);
            sum.addMetricToSum(type);
            typeMap.put(r.getMessage().getType(), type);
        }

        type.addReply(r);
        if (callback != null) {
            callback.onProgress(this);
        }
    }

    public void done() {
        if (callback != null) {
            callback.done(this);
        }
    }

    @Override
    public Metric clone(CopyType type, MetricSet owner, boolean includeUnused) {
        return new RouteMetricSet(this, type, owner, includeUnused);
    }

    String getRoute() {
        return getName();
    }
}
