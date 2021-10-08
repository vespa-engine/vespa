// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hadoop.mapreduce.util;

import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.Counters;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import java.io.IOException;

public class VespaCounters {

    public static final String GROUP = "Vespa Feed Counters";
    public static final String DOCS_OK = "Documents ok";
    public static final String DOCS_SENT = "Documents sent";
    public static final String DOCS_FAILED = "Documents failed";
    public static final String DOCS_SKIPPED = "Documents skipped";

    private final Counter documentsSent;
    private final Counter documentsOk;
    private final Counter documentsFailed;
    private final Counter documentsSkipped;


    private VespaCounters(Job job) throws IOException {
        Counters counters = job.getCounters();
        documentsSent = counters.findCounter(GROUP, DOCS_SENT);
        documentsOk = counters.findCounter(GROUP, DOCS_OK);
        documentsFailed = counters.findCounter(GROUP, DOCS_FAILED);
        documentsSkipped = counters.findCounter(GROUP, DOCS_SKIPPED);
    }


    private VespaCounters(TaskAttemptContext context) {
        documentsSent = context.getCounter(GROUP, DOCS_SENT);
        documentsOk = context.getCounter(GROUP, DOCS_OK);
        documentsFailed = context.getCounter(GROUP, DOCS_FAILED);
        documentsSkipped = context.getCounter(GROUP, DOCS_SKIPPED);
    }


    private VespaCounters(org.apache.hadoop.mapred.Counters counters) {
        documentsSent = counters.findCounter(GROUP, DOCS_SENT);
        documentsOk = counters.findCounter(GROUP, DOCS_OK);
        documentsFailed = counters.findCounter(GROUP, DOCS_FAILED);
        documentsSkipped = counters.findCounter(GROUP, DOCS_SKIPPED);
    }


    public static VespaCounters get(Job job) throws IOException {
        return new VespaCounters(job);
    }


    public static VespaCounters get(TaskAttemptContext context) {
        return new VespaCounters(context);
    }


    public static VespaCounters get(org.apache.hadoop.mapred.Counters counters) {
        return new VespaCounters(counters);

    }


    public long getDocumentsSent() {
        return documentsSent.getValue();
    }


    public void incrementDocumentsSent(long incr) {
        documentsSent.increment(incr);
    }


    public long getDocumentsOk() {
        return documentsOk.getValue();
    }


    public void incrementDocumentsOk(long incr) {
        documentsOk.increment(incr);
    }


    public long getDocumentsFailed() {
        return documentsFailed.getValue();
    }


    public void incrementDocumentsFailed(long incr) {
        documentsFailed.increment(incr);
    }


    public long getDocumentsSkipped() {
        return documentsSkipped.getValue();
    }


    public void incrementDocumentsSkipped(long incr) {
        documentsSkipped.increment(incr);
    }

}
