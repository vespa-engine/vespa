// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.jrt.Supervisor;
import com.yahoo.vespa.filedistribution.FileDistributionConnectionPool;
import com.yahoo.vespa.filedistribution.FileDownloader;
import java.time.Duration;

/**
 * Keeps track of file distribution and url download rpc servers.
 *
 * @author hmusum
 */
public class FileDistributionAndUrlDownload {

    private final FileDistributionRpcServer fileDistributionRpcServer;
    private final UrlDownloadRpcServer urlDownloadRpcServer;
    private final FileReferencesAndDownloadsMaintainer maintainer;

    public FileDistributionAndUrlDownload(Supervisor supervisor, ConfigSourceSet source) {
        fileDistributionRpcServer = new FileDistributionRpcServer(supervisor, createDownloader(supervisor, source));
        urlDownloadRpcServer = new UrlDownloadRpcServer(supervisor);
        maintainer = new FileReferencesAndDownloadsMaintainer();
    }

    public void close() {
        fileDistributionRpcServer.close();
        urlDownloadRpcServer.close();
        maintainer.close();
    }

    private FileDownloader createDownloader(Supervisor supervisor, ConfigSourceSet source) {
        return new FileDownloader(new FileDistributionConnectionPool(source, supervisor),
                                  supervisor,
                                  Duration.ofMinutes(5));
    }



}
