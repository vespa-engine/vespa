// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.yahoo.cloud.config.CuratorConfig;
import com.yahoo.net.HostName;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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

    @Test
    public void require_that_write_fails_if_data_is_more_than_jute_max_buffer() {
        CuratorConfig.Builder builder = new CuratorConfig.Builder();
        builder.server(createZKBuilder(localhost, port1));
        try (Curator curator = createCurator(new CuratorConfig(builder), 1)) {
            try {
                curator.set(Path.createRoot().append("foo"), Utf8.toBytes("more than 1 byte"));
                fail("Did not fail when writing more than juteMaxBuffer bytes");
            } catch (IllegalArgumentException e) {
                assertEquals("Cannot not set data at /foo, 16 bytes is too much, max number of bytes allowed per node is 1",
                             e.getMessage());
            }
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
        return createCurator(curatorConfig, Curator.defaultJuteMaxBuffer);
    }

    private Curator createCurator(CuratorConfig curatorConfig, long juteMaxBuffer)  {
        return new Curator(ConnectionSpec.create(curatorConfig.server(),
                                                 CuratorConfig.Server::hostname,
                                                 CuratorConfig.Server::port,
                                                 curatorConfig.zookeeperLocalhostAffinity()),
                           Optional.empty(),
                           juteMaxBuffer,
                           Curator.DEFAULT_ZK_SESSION_TIMEOUT);
    }

}
