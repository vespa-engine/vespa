// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespafeeder;

import com.yahoo.clientmetrics.MessageTypeMetricSet;
import com.yahoo.clientmetrics.RouteMetricSet;
import com.yahoo.concurrent.Timer;

import java.io.PrintStream;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Class that takes progress from the feed and prints to a stream.
 */
public class ProgressPrinter implements RouteMetricSet.ProgressCallback {
    private long startTime = 0;
    private long lastProgressTime = 0;
    private long lastVerboseProgress = 0;
    final Timer timer;
    final PrintStream output;
    final NumberFormat format;

    public ProgressPrinter(Timer timer, PrintStream output) {
        format = NumberFormat.getNumberInstance(Locale.US);
        format.setMaximumFractionDigits(2);
        format.setMinimumFractionDigits(2);
        format.setMinimumIntegerDigits(1);
        format.setParseIntegerOnly(false);
        format.setRoundingMode(RoundingMode.HALF_UP);
        format.setGroupingUsed(false);
        this.timer = timer;
        this.output = output;

        startTime = timer.milliTime();
        lastProgressTime = startTime;
        lastVerboseProgress = startTime;
    }

    private void printMetrics(PrintStream out, RouteMetricSet metrics) {
        for (MessageTypeMetricSet m : metrics.getMetrics().values()) {
            long timeSinceStart = timer.milliTime() - startTime;
            out.println(m.getMessageName() + ":\t" +
                    "ok: " + m.count +
                    " msgs/sec: " + format.format((double)m.count * 1000 / timeSinceStart) +
                    " failed: " + m.errorCount +
                    " ignored: " + m.ignored +
                    " latency(min, max, avg): " + m.latency_min + ", " + m.latency_max + ", " + m.latency_total/Long.max(1L, m.count));
        }
    }

    public static String getDashes(int count) {
        String dashes = "";
        for (int i = 0; i < count; i++) {
            dashes += "-";
        }

        return dashes;
    }

    public synchronized void renderStatusText(RouteMetricSet metrics, PrintStream stream) {
        String headline = "Messages sent to vespa (route " + metrics.getRoute() + ") :";
        stream.println(headline);
        stream.println(getDashes(headline.length()));
        printMetrics(stream, metrics);
    }

    public long getOkMessageCount(RouteMetricSet metrics) {
        long count = 0;
        for (MessageTypeMetricSet m : metrics.getMetrics().values()) {
            count += m.count;
        }

        return count;
    }

    @Override
    public void onProgress(RouteMetricSet metrics) {
        try {
            long timeNow = timer.milliTime();

            if (timeNow - lastVerboseProgress > 30000) {
                output.println("\n");
                renderStatusText(metrics, output);
                lastVerboseProgress = timeNow;
            } else if (timeNow - lastProgressTime > 1000) {
                output.print("\rSuccessfully sent " + getOkMessageCount(metrics) + " messages so far");
                lastProgressTime = timeNow;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void done(RouteMetricSet metrics) {
        try {
            output.println("\n");
            renderStatusText(metrics, output);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
