// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.vespa.config.SlimeUtils;

import java.time.Instant;

/**
 * A {@code Report} contains information about a node, typically found and published by host admin.
 *
 * <p>May serve as a base class for reports with richer information, e.g. values.
 *
 * @author hakonhall
 */
public class Report {
    /** The time the report was created, in milliseconds since Epoch. */
    public static final String CREATED_FIELD = "createdMillis";
    /** The description of the error (implies wanting to fail out node). */
    public static final String DESCRIPTION_FIELD = "description";

    private final String reportId;
    private final Instant createdTime;

    // This is the serialized report which is typically richer than we know of in this class. It's up to
    // clients of specific reports to query the details.
    private final Inspector reportInspector;

    private Report(String reportId, Instant createdTime, Inspector reportInspector) {
        this.reportId = reportId;
        this.createdTime = createdTime;
        this.reportInspector = reportInspector;
    }

    /** The ID of the report. */
    public String getReportId() {
        return reportId;
    }

    /** The time the report was created. */
    public Instant getCreatedTime() { return createdTime; }

    /** Whether this node has severe issues that to warrant failing it. */
    public boolean shouldFailNode() { return !getDescription().isEmpty(); }

    /** A textual summary of the report. */
    public String getDescription() { return reportInspector.field(DESCRIPTION_FIELD).asString(); }

    /** For exploring the JSON (Slime) of the report. */
    public Inspector getInspector() {
        return reportInspector;
    }

    /** The reportInspector will be used to serialize the full report later, including any createdTime and description. */
    public static Report fromSlime(String reportId, Inspector reportInspector) {
        long millisSinceEpoch = reportInspector.field(CREATED_FIELD).asLong();
        if (millisSinceEpoch <= 0) {
            // Including null or not set.
            millisSinceEpoch = Instant.now().toEpochMilli();
        }
        Instant createdTime = Instant.ofEpochMilli(millisSinceEpoch);

        return new Report(reportId, createdTime, reportInspector);
    }

    public void toSlime(Cursor reportCursor) {
        SlimeUtils.copyObject(reportInspector, reportCursor);

        // If the above inject inserted the created timestamp field, this is a no-op, which is what we want:
        // We'd like the created field to be set the first time we see it, if it is not already set then.
        reportCursor.setLong(CREATED_FIELD, createdTime.toEpochMilli());
    }
}
