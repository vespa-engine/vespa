// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.vespa.config.server.filedistribution.FileDistributionLock;
import com.yahoo.vespa.config.server.filedistribution.FileDistributionProvider;
import com.yahoo.vespa.curator.Curator;

import java.io.File;
import java.util.concurrent.locks.Lock;

/**
 * Factory for creating providers that are used to interact with file distribution.
 *
 * @author Ulf Lilleengen
 */
@SuppressWarnings("WeakerAccess")
public class FileDistributionFactory {

    private static final String lockPath = "/vespa/filedistribution/lock";
    private final String zkSpec;
    private final Lock lock;
    private final Supervisor supervisor = new Supervisor(new Transport());

    @Inject
    public FileDistributionFactory(Curator curator) {
        this(curator, curator.zooKeeperEnsembleConnectionSpec());
    }

    public FileDistributionFactory(Curator curator, String zkSpec) {
        this.lock = new FileDistributionLock(curator, lockPath);
        this.zkSpec = zkSpec;
    }

    public FileDistributionProvider createProvider(File applicationPackage, ApplicationId applicationId, boolean disableFileDistributor) {
        return new FileDistributionProvider(supervisor, applicationPackage, zkSpec, applicationId.serializedForm(), lock, disableFileDistributor);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        supervisor.transport().shutdown().join();
    }
}
