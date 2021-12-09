// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import ai.vespa.feed.client.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dryrun implementation that reports every request/operation as successful
 *
 * @author bjorncs
 */
class DryrunCluster implements Cluster {

    private final static Logger log = Logger.getLogger(DryrunCluster.class.getName());

    static final Duration DELAY = Duration.ofMillis(1);

    @Override
    public void dispatch(HttpRequest request, CompletableFuture<HttpResponse> vessel) {
        long millis = DELAY.toMillis();
        log.log(Level.FINE, "Dryrun of request '{0}' with delay of {1}ms", new Object[]{request, millis});
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                vessel.cancel(true);
                Thread.currentThread().interrupt();
                return;
            }
        }
        vessel.complete(new SimpleOkResponse());
    }

    private static class SimpleOkResponse implements HttpResponse {
        @Override public int code() { return 200; }
        @Override public byte[] body() { return "{\"message\":\"dummy dryrun message\"}".getBytes(StandardCharsets.UTF_8); }
    }

}
