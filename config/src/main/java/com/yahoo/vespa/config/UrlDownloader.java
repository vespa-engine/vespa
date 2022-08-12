// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.UrlReference;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.vespa.defaults.Defaults;

import java.io.File;
import java.time.Duration;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;

/**
 * @author lesters
 */
public class UrlDownloader {

    private static final Logger log = Logger.getLogger(UrlDownloader.class.getName());

    private static final int BASE_ERROR_CODE = 0x10000;
    public  static final int DOES_NOT_EXIST = BASE_ERROR_CODE + 1;
    public  static final int INTERNAL_ERROR = BASE_ERROR_CODE + 2;
    public  static final int HTTP_ERROR = BASE_ERROR_CODE + 3;

    private final Supervisor supervisor = new Supervisor(new Transport("url-downloader"));
    private final Spec spec;
    private Target target;

    public UrlDownloader() {
        spec = new Spec(Defaults.getDefaults().vespaHostname(), Defaults.getDefaults().vespaConfigProxyRpcPort());
    }

    public void shutdown() {
        supervisor.transport().shutdown().join();
    }

    private void connect() {
        int timeRemaining = 5000;
        try {
            while (timeRemaining > 0) {
                target = supervisor.connect(spec);
                // ping to check if connection is working
                Request request = new Request("frt.rpc.ping");
                target.invokeSync(request, Duration.ofSeconds(5));
                if (! request.isError()) {
                    log.log(FINE, () -> "Successfully connected to '" + spec + "', this = " + System.identityHashCode(this));
                    return;
                } else {
                    target.close();
                }
                Thread.sleep(500);
                timeRemaining -= 500;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean isValid() {
        return target != null && target.isValid();
    }

    private boolean temporaryError(Request req) {
        return false;  // Currently, none of the errors are considered temporary
    }

    public File waitFor(UrlReference urlReference, long timeout) {
        long start = System.currentTimeMillis() / 1000;
        if (! isValid())
            connect();
        long timeLeft = timeout;
        do {
            Request request = new Request("url.waitFor");
            request.parameters().add(new StringValue(urlReference.value()));

            double rpcTimeout = Math.min(timeLeft, 60 * 60.0);
            log.log(FINE, () -> "InvokeSync waitFor " + urlReference + " with " + rpcTimeout + " seconds timeout");
            target.invokeSync(request, rpcTimeout);

            if (request.checkReturnTypes("s")) {
                return new File(request.returnValues().get(0).asString());
            } else if (!request.isError()) {
                throw new RuntimeException("Invalid response: " + request.returnValues());
            } else if (temporaryError(request)) {
                log.log(INFO, "Retrying waitFor for " + urlReference + ": " + request.errorCode() + " -- " + request.errorMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted sleep between retries of waitFor", e);
                }
            } else {
                 throw new RuntimeException("Wait for " + urlReference + " failed: " + request.errorMessage() + " (" + request.errorCode() + ")");
            }
            timeLeft = start + timeout - System.currentTimeMillis() / 1000;
        } while (timeLeft > 0);

        throw new RuntimeException("Timed out waiting for " + urlReference + " after " + timeout);
    }

}
