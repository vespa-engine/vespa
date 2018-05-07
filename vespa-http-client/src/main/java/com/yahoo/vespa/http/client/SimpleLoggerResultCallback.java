// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple implementation of the ResultCallback that logs to std err for every X documents:
 *   "Result received: 34 (1 failed so far, 2003 sent, success rate 1999.23 docs/sec)."
 * On each failure it will print the Result object content. If tracing is enabled, it will print trace messages to
 * std err as well.
 *
 * @author dybis
 */
public class SimpleLoggerResultCallback implements FeedClient.ResultCallback {

    private final Object monitor = new Object();
    private int resultCounter = 0;
    private int failureCounter = 0;
    private final AtomicInteger sentDocumentCounter;
    private final int printStatsForEveryXDocument;
    private Instant startSampleInstant = Instant.now();
    private int startSampleResultCount = 0;

    protected void println(String output) {
        System.err.println(output);
    }

    /**
     * Constructor
     *
     * @param sentDocumentCounter a counter that is increased outside this class, but can be nice to print here.
     * @param printStatsForEveryXDocument how often to print stats.
     */
    public SimpleLoggerResultCallback(AtomicInteger sentDocumentCounter, int printStatsForEveryXDocument) {
        this.sentDocumentCounter = sentDocumentCounter;
        this.printStatsForEveryXDocument = printStatsForEveryXDocument;
    }

    /**
     * Prints how many documents that are received, failed and sent.
     */
    public void printProgress() {
        synchronized (monitor) {
            DocumentRate docRate = newSamplingPeriod(Instant.now());
            println(new Date() + " Result received: " + resultCounter
                    + " (" + failureCounter + " failed so far, " + sentDocumentCounter.get()
                    + " sent, success rate " + docRate + ").");
        }
    }

    static class DocumentRate {
        public final double rate;
        DocumentRate(double rate) {
            this.rate = rate;
        }
        @Override
        public String toString() {
            return String.format(Locale.US, "%.2f docs/sec", rate);
        }
    }

    /*
     * Returns success results per second for last interval and resets variables.
     */
    protected DocumentRate newSamplingPeriod(Instant now) {
        double docsDelta = resultCounter - failureCounter - startSampleResultCount;
        Duration duration = Duration.between(startSampleInstant, now);
        startSampleInstant = now;
        startSampleResultCount = resultCounter - failureCounter;
        long durationMilliSecs = duration.toMillis() + 1; // +1 to avoid division by zero
        return new DocumentRate(1000. * docsDelta / durationMilliSecs);
    }

    int getResultCount() {
        synchronized (monitor) {
            return resultCounter;
        }
    }

    int getFailedDocumentCount() {
        synchronized (monitor) {
            return failureCounter;
        }
    }

    @Override
    public void onCompletion(String docId, Result documentResult) {
        synchronized (monitor) {
            if (printStatsForEveryXDocument > 0 && (resultCounter % printStatsForEveryXDocument) == 0) {
                printProgress();
            }
            resultCounter++;
            if (!documentResult.isSuccess()) {
                failureCounter++;
                println("Failure: " + documentResult + (documentResult.getDetails().isEmpty() ? "" : ":"));
                for (Result.Detail detail : documentResult.getDetails())
                    println("    " + detail);
            }
        }
    }

}
