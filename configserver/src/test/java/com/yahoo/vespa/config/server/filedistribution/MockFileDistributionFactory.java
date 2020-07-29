// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.cloud.config.ConfigserverConfig;

import java.io.File;

/**
* @author Ulf Lilleengen
*/
public class MockFileDistributionFactory extends FileDistributionFactory {

    public MockFileDistributionFactory(ConfigserverConfig configserverConfig) {
        super(configserverConfig);
    }

    @Override
    public com.yahoo.vespa.config.server.filedistribution.FileDistributionProvider createProvider(File applicationFile) {
        return new MockFileDistributionProvider(applicationFile, new File(configserverConfig.fileReferencesDir()));
    }

}
