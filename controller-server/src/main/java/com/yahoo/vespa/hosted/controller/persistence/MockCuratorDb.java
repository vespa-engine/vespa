// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.InMemoryFlagSource;

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
        this("test-controller:2222", SystemName.from(config.system()));
    }

    public MockCuratorDb(SystemName system) {
        this("test-controller:2222", system);
    }

    public MockCuratorDb(String zooKeeperEnsembleConnectionSpec, SystemName system) {
        this(new MockCurator() { @Override public String zooKeeperEnsembleConnectionSpec() { return zooKeeperEnsembleConnectionSpec; } },
             system);
    }

    public MockCuratorDb(MockCurator curator, SystemName system) {
        super(curator, Duration.ofMillis(100), new InMemoryFlagSource(), system);
        this.curator = curator;
    }

    public MockCurator curator() { return curator; }

}
