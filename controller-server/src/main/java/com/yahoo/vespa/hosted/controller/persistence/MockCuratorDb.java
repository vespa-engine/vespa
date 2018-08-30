// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.inject.Inject;
import com.yahoo.vespa.curator.mock.MockCurator;

import java.time.Duration;

/**
 * A curator db backed by a mock curator.
 * 
 * @author bratseth
 */
@SuppressWarnings("unused") // injected
public class MockCuratorDb extends CuratorDb {

    @Inject
    public MockCuratorDb() {
        this("test-controller:2222");
    }

    public MockCuratorDb(String zooKeeperEnsembleConnectionSpec) {
        super(new MockCurator() {
            @Override
            public String zooKeeperEnsembleConnectionSpec() {
                return zooKeeperEnsembleConnectionSpec;
            }
        }, Duration.ofMillis(100));
    }

}
