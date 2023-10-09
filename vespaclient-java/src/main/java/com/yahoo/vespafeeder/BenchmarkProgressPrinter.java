// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespafeeder;

import com.yahoo.clientmetrics.MessageTypeMetricSet;
import com.yahoo.clientmetrics.RouteMetricSet;
import com.yahoo.concurrent.Timer;

import java.io.PrintStream;

/**
 * Class that takes progress from the feed and prints to a stream.
 */
public class BenchmarkProgressPrinter implements RouteMetricSet.ProgressCallback {
    private final long startTime;
    private final Timer timer;
    private final PrintStream output;

    public BenchmarkProgressPrinter(Timer timer, PrintStream output) {
        this.timer = timer;
        this.output = output;
        this.startTime = timer.milliTime();
    }

    private void printMetrics(PrintStream out, RouteMetricSet metrics) {
        for (MessageTypeMetricSet m : metrics.getMetrics().values()) {
            long timeUsed = timer.milliTime() - startTime;
            out.println(timeUsed + ", " + m.count + ", " + m.errorCount + ", " + m.latency_min + ", " + m.latency_max + ", " + m.latency_total/Long.max(1L, m.count));
        }
    }

    @Override
    public void onProgress(RouteMetricSet metrics) {
    }

    @Override
    public void done(RouteMetricSet metrics) {
        try {
		    output.println("# Time used, num ok, num error, min latency, max latency, average latency");
            printMetrics(output, metrics);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
