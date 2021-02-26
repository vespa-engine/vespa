// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.vespa.zookeeper.ReconfigException;
import com.yahoo.vespa.zookeeper.Reconfigurer;
import com.yahoo.vespa.zookeeper.Sleeper;
import com.yahoo.vespa.zookeeper.VespaZooKeeperAdmin;
import com.yahoo.vespa.zookeeper.ZooKeeperServer;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author mpolden
 */
public class MockReconfigurer extends Reconfigurer {

    private List<ZooKeeperServer> servers = List.of();
    private int reconfigurations = 0;

    public MockReconfigurer() {
        super(new MockVespaZooKeperAdmin(), new Sleeper() {
            @Override
            public void sleep(Duration duration) {
                // Ignored
            }
        });
    }

    @Override
    public void reconfigure(List<ZooKeeperServer> wantedServers) {
        servers = wantedServers.stream()
                               .sorted(Comparator.comparing(ZooKeeperServer::id))
                               .collect(Collectors.toUnmodifiableList());
        reconfigurations++;
    }

    public List<ZooKeeperServer> servers() {
        return servers;
    }

    public int reconfigurations() {
        return reconfigurations;
    }

    private static class MockVespaZooKeperAdmin implements VespaZooKeeperAdmin {

        @Override
        public void reconfigure(String connectionSpec, String joiningServers, String leavingServers) throws ReconfigException {
        }

    }

}
