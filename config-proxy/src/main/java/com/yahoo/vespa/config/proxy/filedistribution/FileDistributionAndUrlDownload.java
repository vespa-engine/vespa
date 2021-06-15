// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.jrt.Supervisor;
import com.yahoo.vespa.config.JRTConnectionPool;
import com.yahoo.vespa.filedistribution.FileDownloader;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Keeps track of file distribution and url download rpc servers.
 *
 * @author hmusum
 */
public class FileDistributionAndUrlDownload {

    private static final Duration delay = Duration.ofMinutes(1);
    private final FileDistributionRpcServer fileDistributionRpcServer;
    private final UrlDownloadRpcServer urlDownloadRpcServer;
    private final ScheduledExecutorService cleanupExecutor =
            new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("file references and downloads cleanup"));

    public FileDistributionAndUrlDownload(Supervisor supervisor, ConfigSourceSet source) {
        fileDistributionRpcServer =
                new FileDistributionRpcServer(supervisor,
                                              new FileDownloader(new JRTConnectionPool(source, "filedistribution-jrt-pool-")));
        urlDownloadRpcServer = new UrlDownloadRpcServer(supervisor);
        cleanupExecutor.scheduleAtFixedRate(new CachedFilesMaintainer(), delay.toSeconds(), delay.toSeconds(), TimeUnit.SECONDS);
    }

    public void close() {
        fileDistributionRpcServer.close();
        urlDownloadRpcServer.close();
        cleanupExecutor.shutdownNow();
        try {
            if ( ! cleanupExecutor.awaitTermination(10, TimeUnit.SECONDS))
                throw new RuntimeException("Unable to shutdown " + cleanupExecutor + " before timeout");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
