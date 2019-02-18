// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import javax.net.ssl.SSLEngine;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link TlsContext} that regularly reloads the credentials referred to from the transport security options file.
 *
 * @author bjorncs
 */
public class ReloadingTlsContext implements TlsContext {

    private static final Duration UPDATE_PERIOD = Duration.ofHours(1);

    private static final Logger log = Logger.getLogger(ReloadingTlsContext.class.getName());

    private final Path tlsOptionsConfigFile;
    private final AuthorizationMode mode;
    private final AtomicReference<TlsContext> currentTlsContext;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "tls-context-reloader");
                thread.setDaemon(true);
                return thread;
            });

    public ReloadingTlsContext(Path tlsOptionsConfigFile, AuthorizationMode mode) {
        this.tlsOptionsConfigFile = tlsOptionsConfigFile;
        this.mode = mode;
        this.currentTlsContext = new AtomicReference<>(new DefaultTlsContext(tlsOptionsConfigFile, mode));
        this.scheduler.scheduleAtFixedRate(new SslContextReloader(),
                                           UPDATE_PERIOD.getSeconds()/*initial delay*/,
                                           UPDATE_PERIOD.getSeconds(),
                                           TimeUnit.SECONDS);
    }

    @Override
    public SSLEngine createSslEngine() {
        return currentTlsContext.get().createSslEngine();
    }

    @Override
    public void close() {
        try {
            scheduler.shutdownNow();
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private class SslContextReloader implements Runnable {
        @Override
        public void run() {
            try {
                currentTlsContext.set(new DefaultTlsContext(tlsOptionsConfigFile, mode));
            } catch (Throwable t) {
                log.log(Level.SEVERE, String.format("Failed to load SSLContext (path='%s'): %s", tlsOptionsConfigFile, t.getMessage()), t);
            }
        }
    }

}
