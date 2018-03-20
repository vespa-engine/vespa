// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.component.Version;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;

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
    public void next_available_baseport_is_BASE_PORT_when_no_ports_have_been_reserved() {
        HostResource host = mockHostResource();
        assertThat(host.nextAvailableBaseport(1), is(HostResource.BASE_PORT));
    }

    @Test
    public void next_available_baseport_is_BASE_PORT_plus_one_when_one_port_has_been_reserved() {
        HostResource host = mockHostResource();
        host.reservePort(new TestService(1), HostResource.BASE_PORT);
        assertThat(host.nextAvailableBaseport(1), is(HostResource.BASE_PORT + 1));
    }

    @Test
    public void no_available_baseport_when_service_requires_more_consecutive_ports_than_available() {
        HostResource host = mockHostResource();

        for (int p = HostResource.BASE_PORT; p < HostResource.BASE_PORT + HostResource.MAX_PORTS; p += 2) {
            host.reservePort(new TestService(1), p);
        }
        assertThat(host.nextAvailableBaseport(2), is(0));

        try {
            host.reservePort(new TestService(2), HostResource.BASE_PORT);
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("Too many ports are reserved"));
        }
    }

    @Test
    public void require_exception_when_no_matching_hostalias() {
        TestService service = new TestService(1);
        try {
            service.initService();
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), endsWith("No host found for service 'hostresourcetest$testservice0'. " +
                    "The hostalias is probably missing from hosts.xml."));
        }
    }

    @Test
    public void port_above_vespas_port_range_can_be_reserved() {
        HostResource host = mockHostResource();
        host.allocateService(new TestService(1), HostResource.BASE_PORT + HostResource.MAX_PORTS + 1);
    }

    @Test(expected = RuntimeException.class)
    public void allocating_same_port_throws_exception() {
        HostResource host = mockHostResource();
        TestService service1 = new TestService(1);
        TestService service2 = new TestService(1);

        host.allocateService(service1, HostResource.BASE_PORT);
        host.allocateService(service2, HostResource.BASE_PORT);
    }

    @Test(expected = RuntimeException.class)
    public void allocating_overlapping_ports_throws_exception() {
        HostResource host = mockHostResource();
        TestService service2 = new TestService(2);
        TestService service1 = new TestService(1);

        host.allocateService(service2, HostResource.BASE_PORT);
        host.allocateService(service1, HostResource.BASE_PORT + 1);
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
        return ClusterSpec.from(type, ClusterSpec.Id.from(id), ClusterSpec.Group.from(0), Version.fromString("6.42"), false);
    }

    private HostResource mockHostResource() {
        return new HostResource(new Host(new MockRoot()));
    }

    private static HostResource hostResourceWithMemberships(ClusterMembership... memberships) {
        HostResource host = new HostResource(new Host(null, "hostname"));
        Arrays.asList(memberships).forEach(host::addClusterMembership);
        return host;
    }

    private class TestService extends AbstractService {
        private final int portCount;

        TestService(int portCount) {
            super("testService");
            this.portCount = portCount;
        }

        @Override
        public boolean requiresWantedPort() {
            return true;
        }

        @Override
        public int getPortCount() { return portCount; }
    }
}
