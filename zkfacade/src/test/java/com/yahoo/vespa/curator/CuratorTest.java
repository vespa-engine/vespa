// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.yahoo.cloud.config.CuratorConfig;
import com.yahoo.net.HostName;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * Very simple testing of setting up curator
 *
 * @author Ulf Lilleengen
 */
public class CuratorTest {

    private static final String localhost = HostName.getLocalhost();

    private static final int port1 = 1;
    private static final int port2 = 2;
    private static final String spec1 = localhost + ":" + port1;
    private static final String spec2 = localhost + ":" + port2;

    @Test
    public void require_curator_is_created_from_config() {
        try (Curator curator = createCurator(createTestConfig())) {
            assertEquals(spec1 + "," + spec2, curator.zooKeeperEnsembleConnectionSpec());
            assertEquals(2, curator.zooKeeperEnsembleCount());
        }
    }

    @Test
    public void require_that_server_count_is_correct() {
        CuratorConfig.Builder builder = new CuratorConfig.Builder();
        builder.server(createZKBuilder(localhost, port1));
        try (Curator curator = createCurator(new CuratorConfig(builder))) {
            assertEquals(1, curator.zooKeeperEnsembleCount());
        }
    }

    private CuratorConfig createTestConfig() {
        CuratorConfig.Builder builder = new CuratorConfig.Builder();
        builder.server(createZKBuilder(localhost, port1));
        builder.server(createZKBuilder(localhost, port2));
        return new CuratorConfig(builder);
    }

    private CuratorConfig.Server.Builder createZKBuilder(String hostname, int port) {
        CuratorConfig.Server.Builder zkBuilder = new CuratorConfig.Server.Builder();
        zkBuilder.hostname(hostname);
        zkBuilder.port(port);
        return zkBuilder;
    }

    private Curator createCurator(CuratorConfig curatorConfig)  {
        return new Curator(ConnectionSpec.create(curatorConfig.server(),
                                                 CuratorConfig.Server::hostname,
                                                 CuratorConfig.Server::port,
                                                 curatorConfig.zookeeperLocalhostAffinity()),
                           Optional.empty());
    }

}
