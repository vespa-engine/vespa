// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.yahoo.config.provision.ClusterSpec.Type.container;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gjoranv
 */
public class HostResourceTest {

    @Test
    void require_exception_when_no_matching_hostalias() {
        MockRoot root = new MockRoot();
        TestService service = new TestService(root, 1);

        try {
            service.initService(root.getDeployState());
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().endsWith("No host found for service 'hostresourcetest$testservice0'. " +
                    "The hostalias is probably missing from hosts.xml."));
        }
    }

    @Test
    void host_with_membership() {
        HostResource host = hostResourceWithMemberships(ClusterMembership.from(clusterSpec(container, "container"), 0));
        assertClusterMembership(host, container, "container");
    }

    private void assertClusterMembership(HostResource host, ClusterSpec.Type type, String id) {
        ClusterSpec membership = host.spec().membership().map(ClusterMembership::cluster)
                .orElseThrow(() -> new RuntimeException("No cluster membership!"));

        assertEquals(type, membership.type());
        assertEquals(id, membership.id().value());
    }

    private static ClusterSpec clusterSpec(ClusterSpec.Type type, String id) {
        return ClusterSpec.specification(type, ClusterSpec.Id.from(id)).group(ClusterSpec.Group.from(0)).vespaVersion("6.42").build();
    }

    private static HostResource hostResourceWithMemberships(ClusterMembership membership) {
        return new HostResource(Host.createHost(null, "hostname"),
                                new HostSpec("hostname",
                                             NodeResources.unspecified(), NodeResources.unspecified(), NodeResources.unspecified(),
                                             membership,
                                             Optional.empty(), Optional.empty(), Optional.empty()));
    }

    private static int counter = 0;
    int getCounter() { return ++counter; }

    private class TestService extends AbstractService {
        private final int portCount;

        TestService(TreeConfigProducer parent, int portCount) {
            super(parent, "testService" + getCounter());
            this.portCount = portCount;
        }

        @Override
        public boolean requiresWantedPort() {
            return true;
        }

        @Override
        public int getPortCount() { return portCount; }

        @Override
        public void allocatePorts(int start, PortAllocBridge from) {
            for (int i = 0; i < portCount; i++) {
                String suffix = "generic." + i;
                if (start == 0) {
                    from.allocatePort(suffix);
                } else {
                    from.requirePort(start++, suffix);
                }
            }
        }

    }
}
