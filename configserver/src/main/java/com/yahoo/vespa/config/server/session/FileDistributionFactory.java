// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.vespa.config.server.filedistribution.FileDistributionProvider;

import java.io.File;

/**
 * Factory for creating providers that are used to interact with file distribution.
 *
 * @author Ulf Lilleengen
 */
@SuppressWarnings("WeakerAccess")
public class FileDistributionFactory {

    private final Supervisor supervisor = new Supervisor(new Transport());

    public FileDistributionProvider createProvider(File applicationPackage) {
        return new FileDistributionProvider(supervisor, applicationPackage);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        supervisor.transport().shutdown().join();
    }
}
