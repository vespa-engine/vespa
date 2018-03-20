// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.Test;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class AllocatedHostsTest {

    private final HostSpec h1 = new HostSpec("host1", Optional.empty());
    private final HostSpec h2 = new HostSpec("host2", Optional.empty());
    private final HostSpec h3 = new HostSpec("host3", Optional.of(ClusterMembership.from("container/test/0/0", com.yahoo.component.Version.fromString("6.73.1"))));

    @Test
    public void testAllocatedHostsSerialization() throws IOException {
        Set<HostSpec> hosts = new LinkedHashSet<>();
        hosts.add(h1);
        hosts.add(h2);
        hosts.add(h3);
        AllocatedHosts info = AllocatedHosts.withHosts(hosts);
        assertAllocatedHosts(info);
    }

    private void assertAllocatedHosts(AllocatedHosts info) throws IOException {
        AllocatedHosts serializedAllocatedHosts = AllocatedHosts.fromJson(info.toJson(), Optional.empty());
        assertEquals(info.getHosts().size(), serializedAllocatedHosts.getHosts().size());
        assertTrue(serializedAllocatedHosts.getHosts().contains(h1));
        assertTrue(serializedAllocatedHosts.getHosts().contains(h2));
        assertTrue(serializedAllocatedHosts.getHosts().contains(h3));
        assertTrue(!getHost(h1.hostname(), serializedAllocatedHosts.getHosts()).membership().isPresent());
        assertEquals("container/test/0/0", getHost(h3.hostname(), serializedAllocatedHosts.getHosts()).membership().get().stringValue());
        assertEquals(h3.membership().get().cluster().vespaVersion(), getHost(h3.hostname(), 
                                                                             serializedAllocatedHosts.getHosts()).membership().get().cluster().vespaVersion());
    }

    private HostSpec getHost(String hostname, Set<HostSpec> hosts) {
        for (HostSpec host : hosts)
            if (host.hostname().equals(hostname))
                return host;
        throw new IllegalArgumentException("No host " + hostname + " is present");
    }

}
