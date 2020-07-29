// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.model.api.FileDistribution;

import java.io.File;

/**
 * @author hmusum
 */
public class MockFileDistributionProvider extends FileDistributionProvider {

    public MockFileDistributionProvider(File applicationDir, File fileReferencesDir) {
        super(new MockFileRegistry(applicationDir, fileReferencesDir.toPath()),
              new MockFileDistribution(fileReferencesDir));
    }

    public FileDistribution getFileDistribution() {
        return super.getFileDistribution();
    }

}
