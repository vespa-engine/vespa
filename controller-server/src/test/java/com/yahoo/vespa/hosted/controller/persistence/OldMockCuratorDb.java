// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.inject.Inject;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;

import java.time.Duration;

/**
 * A curator db backed by a mock curator.
 * 
 * @author bratseth
 */
@SuppressWarnings("unused") // injected
public class OldMockCuratorDb extends OldCuratorDb {

    public OldMockCuratorDb(MockCurator curator) {
        super(curator, Duration.ofMillis(100));
    }

}
