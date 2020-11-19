// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.google.inject.Inject;
import com.yahoo.container.core.VipStatusConfig;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.jdisc.Metric;
import java.util.logging.Level;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.defaults.Defaults;

/**
 * Transmit status to VIP from file or memory. Bind this to
 * "http://{@literal *}/status.html" to serve VIP status requests.
 *
 * @author Steinar Knutsen
 * @author bratseth
 */
public final class VipStatusHandler extends ThreadedHttpRequestHandler {

    private static final String NUM_REQUESTS_METRIC = "jdisc.http.requests.status";

    private final boolean accessDisk;
    private final File statusFile;
    private final VipStatus vipStatus;

    // belongs in the response, but that's not a static class
    static final String OK_MESSAGE = "<title>OK</title>\n";
    static final byte[] VIP_OK = Utf8.toBytes(OK_MESSAGE);

    class StatusResponse extends HttpResponse {

        static final String COULD_NOT_FIND_STATUS_FILE = "Could not find status file.\n";
        static final String NO_SEARCH_BACKENDS = "No search backends available, VIP status disabled."; // TODO: Generify
        private static final String TEXT_HTML = "text/html";
        private String contentType = TEXT_HTML;
        private byte[] data = null;
        private File file = null;

        private StatusResponse() {
            super(com.yahoo.jdisc.http.HttpResponse.Status.OK); // status may be overwritten below
            if (vipStatus != null && ! vipStatus.isInRotation()) {
                setOutOfServiceStatus();
            } else if (accessDisk) {
                preSlurpFile();
            } else {
                vipRespond();
            }
        }

        @Override
        public void render(OutputStream stream) throws IOException {
            if (file != null) {
                readAndWrite(stream);
            }
            else if (data != null) {
                stream.write(data);
            }
            else {
                throw new IllegalStateException("Neither file nor hardcoded data. This is a bug.");
            }
            stream.close();
        }

        private void readAndWrite(OutputStream stream) throws IOException {
            InputStream input;
            int lastRead = 0;
            input = new FileInputStream(file);
            try {
                while (lastRead != -1) {
                    byte[] buffer = new byte[5000];
                    lastRead = input.read(buffer);
                    if (lastRead > 0) {
                        stream.write(buffer, 0, lastRead);
                    }
                }
            } finally {
                stream.close();
                input.close();
            }
        }

        private void preSlurpFile() {
            try {
                if (!statusFile.exists()) {
                    fileNotFound();
                    return;
                }
                if (!statusFile.canRead()) {
                    accessDenied();
                    return;
                }
            } catch (SecurityException e) {
                internalError();
                return;
            }
            this.file = statusFile;
        }

        private void accessDenied() {
            contentType = "text/plain";
            data = Utf8.toBytes("Status file inaccessible.\n");
            setStatus(com.yahoo.jdisc.http.HttpResponse.Status.NOT_FOUND);
        }

        private void internalError() {
            contentType = "text/plain";
            data = Utf8.toBytes("Internal error while fetching status file.\n");
            setStatus(com.yahoo.jdisc.http.HttpResponse.Status.NOT_FOUND);
        }

        private void fileNotFound() {
            contentType = "text/plain";
            data = Utf8.toBytes(COULD_NOT_FIND_STATUS_FILE);
            setStatus(com.yahoo.jdisc.http.HttpResponse.Status.NOT_FOUND);
        }

        private void vipRespond() {
            data = VIP_OK;
        }

        /**
         * Behaves like a VIP status response file has been deleted.
         */
        private void setOutOfServiceStatus() {
            contentType = "text/plain";
            data = Utf8.toBytes(NO_SEARCH_BACKENDS);
            setStatus(com.yahoo.jdisc.http.HttpResponse.Status.NOT_FOUND);
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public String getCharacterEncoding() {
            return null;
        }
    }

    /**
     * Create this with a dedicated thread pool to avoid returning an error to VIPs when the regular thread pool is 
     * out of capacity. This is the default behavior.
     */
    @Inject
    public VipStatusHandler(VipStatusConfig vipConfig, Metric metric, VipStatus vipStatus) {
        // One thread should be enough for status handling - otherwise something else is completely wrong,
        // in which case this will eventually start returning a 503 (due to work rejection) as the bounded
        // queue will fill up
        this(new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(100)),
             vipConfig, metric, vipStatus);
    }

    public VipStatusHandler(Executor executor, VipStatusConfig vipConfig, Metric metric) {
        this(executor, vipConfig, metric, null);
    }

    public VipStatusHandler(Executor executor, VipStatusConfig vipConfig, Metric metric, VipStatus vipStatus) {
        super(executor, metric);
        this.accessDisk = vipConfig.accessdisk();
        this.statusFile = new File(Defaults.getDefaults().underVespaHome(vipConfig.statusfile()));
        this.vipStatus = vipStatus;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        if (metric != null)
            metric.add(NUM_REQUESTS_METRIC, 1, null);
        return new StatusResponse();
    }

}
