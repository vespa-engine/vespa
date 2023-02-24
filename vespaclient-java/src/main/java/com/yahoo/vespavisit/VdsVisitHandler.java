// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespavisit;

import com.yahoo.documentapi.ProgressToken;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorDataHandler;
import com.yahoo.vdslib.VisitorStatistics;

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Date;
import java.util.TimeZone;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * An abstract class that can be subclassed by different visitor handlers.
 *
 * @author Thomas Gundersen
 */
public abstract class VdsVisitHandler {

    boolean showProgress;
    boolean showStatistics;
    boolean abortOnClusterDown;
    boolean lastLineIsProgress = false;
    String lastPercentage;
    final Object printLock = new Object();

    private static class ProgressMeta {
        String fileName = "";
        String lastProgressContents;
        int unwrittenUpdates = 0;
        long lastWriteAtNanos = 0;
        Duration writeInterval = Duration.ofSeconds(10);

        boolean shouldWriteProgress() {
            return !fileName.isEmpty();
        }
    }

    ProgressMeta progressMeta = new ProgressMeta();
    final VisitorControlHandler controlHandler = new ControlHandler();

    public VdsVisitHandler(boolean showProgress, boolean showStatistics, boolean abortOnClusterDown) {
        this.showProgress = showProgress;
        this.showStatistics = showStatistics;
        this.abortOnClusterDown = abortOnClusterDown;
        this.progressMeta.lastWriteAtNanos = System.nanoTime(); // Avoid always writing a file on the first progress update
    }

    public boolean getShowProgress() {
        return showProgress;
    }

    public boolean getShowStatistics() {
        return showStatistics;
    }

    public boolean getAbortOnClusterDown() {
        return abortOnClusterDown;
    }

    public boolean getLastLineIsProgress() {
        return lastLineIsProgress;
    }

    public void setLastLineIsProgress(boolean isProgress) {
        lastLineIsProgress = isProgress;
    }

    public String getLastPercentage() {
        return lastPercentage;
    }

    public void setLastPercentage(String lastPercentage) {
        this.lastPercentage = lastPercentage;
    }

    public Object getPrintLock() {
        return printLock;
    }

    public void onDone() { }

    public String getProgressFileName() {
        return progressMeta.fileName;
    }

    public void setProgressFileName(String progressFileName) {
        this.progressMeta.fileName = progressFileName;
    }

    public VisitorControlHandler getControlHandler() { return controlHandler; }
    public abstract VisitorDataHandler getDataHandler();

    class ControlHandler extends VisitorControlHandler {
        VisitorStatistics statistics;

        private void rewriteProgressFile() {
            try {
                var tmpPath = Path.of(progressMeta.fileName + ".tmp");
                Files.writeString(tmpPath, progressMeta.lastProgressContents);
                Files.move(tmpPath, Path.of(progressMeta.fileName), REPLACE_EXISTING, ATOMIC_MOVE);
            } catch (IOException e) {
                e.printStackTrace();
                abort(); // Don't continue visiting if we're unable to save progress state
            }
        }

        public void onProgress(ProgressToken token) {
            if (progressMeta.shouldWriteProgress()) {
                 synchronized (token) {
                     progressMeta.unwrittenUpdates++;
                     progressMeta.lastProgressContents = token.toString();
                     long nowNanos = System.nanoTime();
                     if ((nowNanos - progressMeta.lastWriteAtNanos) > progressMeta.writeInterval.toNanos()) {
                         rewriteProgressFile();
                         progressMeta.unwrittenUpdates = 0;
                         progressMeta.lastWriteAtNanos = nowNanos;
                     }
                 }
            }
            if (showProgress) {
                synchronized (printLock) {
                    DecimalFormat df = new DecimalFormat("#.#");
                    String percentage = df.format(token.percentFinished());
                    if (!percentage.equals(lastPercentage)) {
                        if (lastLineIsProgress) {
                            System.err.print('\r');
                        }
                        // Pad with a few extra spaces to handle case where current line written is shorter
                        // than the previous line written. Would otherwise leave stale characters behind.
                        System.err.print(percentage + " % finished.   ");
                        lastLineIsProgress = true;
                        lastPercentage = percentage;
                    }
                }
            }
            super.onProgress(token);
        }

        @Override
        public void onVisitorStatistics(VisitorStatistics visitorStatistics) {
            statistics = visitorStatistics;
        }

        private String getDateTime() {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = new Date();
            return dateFormat.format(date);
        }

        public void onVisitorError(String message) {
            synchronized (printLock) {
                if (lastLineIsProgress) {
                    System.err.print('\r');
                    lastLineIsProgress = false;
                }
                System.err.println("Visitor error (" + getDateTime() + "): " + message);
                if (abortOnClusterDown && !isDone() && (message.lastIndexOf("Could not resolve")>=0 ||
                                                        message.lastIndexOf("don't allow external load")>=0)) {
                    System.err.println("Aborting visitor as --abortonclusterdown flag is set.");
                    abort();
                }
            }
        }
        public void onDone(CompletionCode code, String message) {
            // Flush any remaining unwritten progress updates.
            // It is expected that this happens-after any and all calls to onProgress().
            if (progressMeta.unwrittenUpdates > 0) {
                rewriteProgressFile();
            }
            if (lastLineIsProgress) {
                System.err.print('\n');
                lastLineIsProgress = false;
            }
            if (code != CompletionCode.SUCCESS) {
                if (code == CompletionCode.ABORTED) {
                    System.err.println("Visitor aborted: " + message);
                }
                else if (code == CompletionCode.TIMEOUT) {
                    System.err.println("Visitor timed out: " + message);
                }
                else {
                    System.err.println("Visitor aborted due to unknown issue " + code + ": " + message);
                }
            } else {
                if (showProgress) {
                    System.err.println("Completed visiting.");
                }
                if (showStatistics) {
                    System.err.println("*** Visitor statistics");
                    System.err.println(statistics == null ? "Nothing visited" : statistics.toString());
                }
            }
            super.onDone(code, message);
        }
    }
}
