// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.http.HttpConfigResponse;
import com.yahoo.vespa.config.server.metrics.Metrics;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * @author olaa
 */
public class MetricsRespone extends HttpResponse {

    private final Slime slime = new Slime();

    public MetricsRespone(int status, Map<ApplicationId, Map<String, Metrics>> aggregatedMetrics) {
        super(status);

        Cursor array = slime.setArray();
        for (Map.Entry<ApplicationId, Map<String, Metrics>> entry : aggregatedMetrics.entrySet()) {
            Cursor object = array.addObject();
            object.setString("applicationId", entry.getKey().serializedForm());
            Cursor clusterArray = object.setArray("clusters");
            for (Map.Entry<String, Metrics> clusterMetrics : entry.getValue().entrySet()) {
                Cursor clusterCursor = clusterArray.addObject();
                Metrics metrics = clusterMetrics.getValue();
                clusterCursor.setString("clusterName", clusterMetrics.getKey());
                clusterCursor.setDouble("queriesPerSecond", metrics.getQueriesPerSecond());
                clusterCursor.setDouble("writesPerSecond", metrics.getWritesPerSecond());
                clusterCursor.setDouble("documentCount", metrics.getDocumentCount());
                clusterCursor.setDouble("queryLatencyMillis", metrics.getQueryLatencyMillis());
                clusterCursor.setDouble("writeLatencyMillis", metrics.getWriteLatencyMills());
            }
        }
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        new JsonFormat(false).encode(outputStream, slime);
    }

    @Override
    public String getContentType() {
        return HttpConfigResponse.JSON_CONTENT_TYPE;
    }
}
