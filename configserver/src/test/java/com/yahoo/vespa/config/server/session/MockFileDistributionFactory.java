// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.filedistribution.FileDistributionProvider;
import com.yahoo.vespa.config.server.filedistribution.MockFileDistributionProvider;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;

import java.io.File;

/**
* @author lulf
* @since 5.1
*/
public class MockFileDistributionFactory extends FileDistributionFactory {

    public final MockFileDistributionProvider mockFileDistributionProvider = new MockFileDistributionProvider();

    // Prevent instantiation without supplied curator instance
    private MockFileDistributionFactory() {
        super(new MockCurator(), "");
    }

    public MockFileDistributionFactory(Curator curator) {
        super(curator, "");
    }

    @Override
    public FileDistributionProvider createProvider(File applicationFile, ApplicationId applicationId) {
        return mockFileDistributionProvider;
    }
}
