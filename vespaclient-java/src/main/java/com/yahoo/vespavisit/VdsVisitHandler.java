// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespavisit;

import com.yahoo.documentapi.ProgressToken;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorDataHandler;
import com.yahoo.vdslib.VisitorStatistics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.Date;
import java.util.TimeZone;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;

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

    protected String progressFileName = "";

    final VisitorControlHandler controlHandler = new ControlHandler();

    public VdsVisitHandler(boolean showProgress, boolean showStatistics, boolean abortOnClusterDown) {
        this.showProgress = showProgress;
        this.showStatistics = showStatistics;
        this.abortOnClusterDown = abortOnClusterDown;
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
        return progressFileName;
    }

    public void setProgressFileName(String progressFileName) {
        this.progressFileName = progressFileName;
    }

    public VisitorControlHandler getControlHandler() { return controlHandler; }
    public abstract VisitorDataHandler getDataHandler();

    class ControlHandler extends VisitorControlHandler {
        VisitorStatistics statistics;

        public void onProgress(ProgressToken token) {
            if (progressFileName.length() > 0) {
                 try {
                     synchronized (token) {
                         File file = new File(progressFileName + ".tmp");
                         FileOutputStream fos = new FileOutputStream(file);
                         fos.write(token.toString().getBytes());
                         fos.close();
                         file.renameTo(new File(progressFileName));
                     }
                 }
                 catch (IOException e) {
                     e.printStackTrace();
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
                        System.err.print(percentage + " % finished.");
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
