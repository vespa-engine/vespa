// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.curator.mock.MockCurator;

import java.time.Duration;

/**
 * A curator db backed by a mock curator.
 * 
 * @author bratseth
 */
@SuppressWarnings("unused") // injected
public class MockCuratorDb extends CuratorDb {

    private final MockCurator curator;

    @Inject
    public MockCuratorDb(ConfigserverConfig config) {
        this("test-controller:2222");
    }

    public MockCuratorDb(SystemName system) {
        this("test-controller:2222");
    }

    public MockCuratorDb(String zooKeeperEnsembleConnectionSpec) {
        this(new MockCurator() { @Override public String zooKeeperEnsembleConnectionSpec() { return zooKeeperEnsembleConnectionSpec; } });
    }

    public MockCuratorDb(MockCurator curator) {
        super(curator, Duration.ofMillis(100));
        this.curator = curator;
    }

    public MockCurator curator() { return curator; }

}
