// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.component.Version;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static com.yahoo.config.provision.ClusterSpec.Type.admin;
import static com.yahoo.config.provision.ClusterSpec.Type.container;
import static com.yahoo.config.provision.ClusterSpec.Type.content;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class HostResourceTest {

    @Test
    public void require_exception_when_no_matching_hostalias() {
        MockRoot root = new MockRoot();
        TestService service = new TestService(root, 1);

        try {
            service.initService(root.deployLogger());
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), endsWith("No host found for service 'hostresourcetest$testservice0'. " +
                    "The hostalias is probably missing from hosts.xml."));
        }
    }

    @Test
    public void no_clusters_yields_no_primary_cluster_membership() {
        HostResource host = hostResourceWithMemberships();
        assertTrue(host.clusterMemberships().isEmpty());

        assertFalse(host.primaryClusterMembership().isPresent());
    }

    @Test
    public void one_cluster_yields_that_primary_cluster_membership() {
        HostResource host = hostResourceWithMemberships(ClusterMembership.from(clusterSpec(container, "jdisc"), 0));
        assertClusterMembership(host, container, "jdisc");
    }

    @Test
    public void content_cluster_membership_is_preferred_over_other_types() {
        HostResource host = hostResourceWithMemberships(
                ClusterMembership.from(clusterSpec(container, "jdisc"), 0),
                ClusterMembership.from(clusterSpec(content, "search"), 0),
                ClusterMembership.from(clusterSpec(admin, "admin"), 0));

        assertClusterMembership(host, content, "search");
    }

    @Test
    public void container_cluster_membership_is_preferred_over_admin() {
        HostResource host = hostResourceWithMemberships(
                ClusterMembership.from(clusterSpec(admin, "admin"), 0),
                ClusterMembership.from(clusterSpec(container, "jdisc"), 0));

        assertClusterMembership(host, container, "jdisc");
    }

    @Test
    public void cluster_membership_that_was_added_first_is_preferred() {
        HostResource host = hostResourceWithMemberships(
                ClusterMembership.from(clusterSpec(content, "content1"), 0),
                ClusterMembership.from(clusterSpec(content, "content0"), 0),
                ClusterMembership.from(clusterSpec(content, "content2"), 0));

        assertClusterMembership(host, content, "content1");
    }

    private void assertClusterMembership(HostResource host, ClusterSpec.Type type, String id) {
        ClusterSpec membership = host.primaryClusterMembership().map(ClusterMembership::cluster)
                .orElseThrow(() -> new RuntimeException("No cluster membership!"));

        assertEquals(type, membership.type());
        assertEquals(id, membership.id().value());
    }

    private static ClusterSpec clusterSpec(ClusterSpec.Type type, String id) {
        return ClusterSpec.from(type, ClusterSpec.Id.from(id), ClusterSpec.Group.from(0), Version.fromString("6.42"), false, Collections.emptySet());
    }

    private HostResource mockHostResource(MockRoot root) {
        return new HostResource(new Host(root));
    }

    private static HostResource hostResourceWithMemberships(ClusterMembership... memberships) {
        HostResource host = new HostResource(Host.createHost(null, "hostname"));
        Arrays.asList(memberships).forEach(host::addClusterMembership);
        return host;
    }

    private static int counter = 0;
    int getCounter() { return ++counter; }

    private class TestService extends AbstractService {
        private final int portCount;

        TestService(AbstractConfigProducer parent, int portCount) {
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
        public String[] getPortSuffixes() {
            String[] suffixes = new String[portCount];
            for (int i = 0; i < portCount; i++) {
                suffixes[i] = "generic." + i;
            }
            return suffixes;
        }
    }
}
