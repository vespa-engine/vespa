// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedhandler;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.metrics.MetricManager;
import com.yahoo.metrics.MetricSnapshot;
import com.yahoo.text.Utf8String;
import com.yahoo.text.XMLWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

@Deprecated
public class StatusResponse extends HttpResponse {

    MetricManager manager;
    int verbosity;
    int snapshotTime;

    StatusResponse(MetricManager manager, int verbosity, int snapshotTime) {
        super(com.yahoo.jdisc.http.HttpResponse.Status.OK);
        this.manager = manager;
        this.snapshotTime = snapshotTime;
        this.verbosity = verbosity;
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        XMLWriter writer = new XMLWriter(new OutputStreamWriter(stream));
        writer.openTag("status");
        if (verbosity >= 2) {
            writer.attribute(new Utf8String("description"), "Metrics since start");
        }

        if (snapshotTime == 0) {
            MetricSnapshot snapshot = (new MetricSnapshot(
                        "Total metrics from start until current time", 0,
                        manager.getActiveMetrics().getMetrics(), false));
            manager.getTotalMetricSnapshot().addToSnapshot(snapshot, (int)(System.currentTimeMillis() / 1000), false);
            snapshot.printXml(manager, "", verbosity, writer);
        } else {
            try {
                manager.getMetricSnapshotSet(snapshotTime).getSnapshot().printXml(manager, "", verbosity, writer);
            } catch (Exception e) {
                writer.openTag("error");
                writer.attribute(new Utf8String("details"), "No metric snapshot with period " + snapshotTime +
                                                            " was found. Legal snapshot periods are: " + manager.getSnapshotPeriods());
                writer.closeTag();
            }
        }
        writer.closeTag();
        writer.flush();
    }

    @Override
    public java.lang.String getContentType() {
        return "application/xml";
    }

}
