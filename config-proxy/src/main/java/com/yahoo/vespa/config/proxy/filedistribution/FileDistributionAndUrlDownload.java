// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.jrt.Supervisor;
import com.yahoo.vespa.config.JRTConnectionPool;
import com.yahoo.vespa.filedistribution.FileDistributionRpcServer;
import com.yahoo.vespa.filedistribution.FileDownloader;

import java.util.stream.Stream;

/**
 * Keeps track of file distribution and url download rpc servers.
 *
 * @author hmusum
 */
public class FileDistributionAndUrlDownload {

    private final FileDistributionRpcServer fileDistributionRpcServer;
    private final UrlDownloadRpcServer urlDownloadRpcServer;

    public FileDistributionAndUrlDownload(Supervisor supervisor, ConfigSourceSet source) {
        fileDistributionRpcServer = new FileDistributionRpcServer(supervisor, new FileDownloader(new JRTConnectionPool(source)));
        urlDownloadRpcServer = new UrlDownloadRpcServer(supervisor);
    }

    public void close() {
        fileDistributionRpcServer.close();
        urlDownloadRpcServer.close();
    }

}
