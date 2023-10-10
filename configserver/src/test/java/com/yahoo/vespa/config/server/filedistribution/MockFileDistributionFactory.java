// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.application.api.FileRegistry;

import java.io.File;

/**
* @author Ulf Lilleengen
*/
public class MockFileDistributionFactory extends FileDistributionFactory {

    public MockFileDistributionFactory(ConfigserverConfig configserverConfig) {
        super(configserverConfig, new FileDirectory(configserverConfig));
    }

    @Override
    public FileRegistry createFileRegistry(File applicationPackage) {
        return new MockFileRegistry(applicationPackage, fileDirectory);
    }

    @Override
    public AddFileInterface createFileManager(File applicationDir) {
        return new MockFileManager();
    }

}
