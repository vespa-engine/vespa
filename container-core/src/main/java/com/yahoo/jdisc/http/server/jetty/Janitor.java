// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.DaemonThreadFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Separate janitor threadpool for tasks that cannot be executed on the jdisc default threadpool due to risk of deadlock.
 * Modelled as a separate component as the underlying executor must be available across {@link JettyHttpServer} instances.
 *
 * @author bjorncs
 */
public class Janitor extends AbstractComponent {

    private static final Logger log = Logger.getLogger(Janitor.class.getName());

    private final ExecutorService executor;

    @Inject
    public Janitor() {
        int threadPoolSize = Math.max(2, Runtime.getRuntime().availableProcessors()/4);
        log.info("Creating janitor executor with " + threadPoolSize + " threads");
        this.executor = Executors.newFixedThreadPool(threadPoolSize, new DaemonThreadFactory("jdisc-janitor-"));
    }

    public void scheduleTask(Runnable task) { executor.execute(task); }

    @Override
    public void deconstruct() {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warning("Failed to shutdown janitor in time");
            }
        } catch (InterruptedException e) {
            log.warning("Interrupted while shutting down janitor");
            Thread.currentThread().interrupt();
        }
    }
}
