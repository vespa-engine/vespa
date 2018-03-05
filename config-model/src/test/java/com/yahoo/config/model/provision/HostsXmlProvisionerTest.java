// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import com.yahoo.config.provision.HostSpec;
import com.yahoo.vespa.model.container.Container;
import org.junit.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author hmusum
 */
public class HostsXmlProvisionerTest {

    private static final String oneHost = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<hosts>\n" +
            "    <host name=\"test1.yahoo.com\">\n" +
            "        <alias>node1</alias>\n" +
            "        <alias>node2</alias>\n" +
            "    </host>\n" +
            "</hosts>";

    private static final String threeHosts = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<hosts>\n" +
            "    <host name=\"test1.yahoo.com\">\n" +
            "        <alias>node1</alias>\n" +
            "    </host>\n" +
            "    <host name=\"test2.yahoo.com\">\n" +
            "        <alias>node2</alias>\n" +
            "        <alias>node3</alias>\n" +
            "    </host>\n" +
            "    <host name=\"test3.yahoo.com\">\n" +
            "        <alias>node4</alias>\n" +
            "    </host>\n" +
            "</hosts>";

    @Test
    public void require_basic_works() {
        HostsXmlProvisioner hostProvisioner = createProvisioner(threeHosts);

        // 4 services, 2 host aliases, mapping to 2 host.
        List<String> aliases = createAliases();
        Map<String, HostSpec> map = allocate(hostProvisioner, aliases);

        assertCorrectNumberOfHosts(map, 2);
        for (HostSpec hostSpec : map.values()) {
            if (hostSpec.hostname().equals("test2.yahoo.com")) {
                assertThat(hostSpec.aliases().size(), is(2));
            } else {
                assertThat(hostSpec.aliases().size(), is(1));
            }
        }
        assertThat(map.size(), is(2));
        assertTrue(map.keySet().containsAll(aliases));

        // 5 services, 3 host aliases, mapping to 2 host.
        aliases = createAliases(Collections.singletonList("node3"));
        map = allocate(hostProvisioner, aliases);

        assertCorrectNumberOfHosts(map, 2);
        assertThat(map.size(), is(3));
        assertTrue(map.keySet().containsAll(aliases));

        // 5 services, 3 host aliases, mapping to 3 host.
        aliases = createAliases(Collections.singletonList("node4"));
        map = allocate(hostProvisioner, aliases);
        assertThat(map.size(), is(3));
        assertCorrectNumberOfHosts(map, 3);
        assertTrue(map.keySet().containsAll(aliases));
        
        assertEquals("", System.getProperty("zookeeper.vespa.clients"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void require_exception_when_unknown_hosts_alias() {
        HostsXmlProvisioner hostProvisioner = createProvisioner(oneHost);
        hostProvisioner.allocateHost("unknown");
    }

    private void assertCorrectNumberOfHosts(Map<String, HostSpec> hostToServiceMap, int expectedHostCount) {
        Set<String> hostSet = new HashSet<>();
        for (HostSpec host : hostToServiceMap.values()) {
            hostSet.add(host.hostname());
        }
        assertThat(hostSet.size(), is(expectedHostCount));
    }

    private HostsXmlProvisioner createProvisioner(String hosts) {
        return new HostsXmlProvisioner(new StringReader(hosts));
    }

    private List<String> createAliases() {
        return createAliases(new ArrayList<>());
    }

    // Admin services on node1, qrserver on node2 + additional specs
    private List<String> createAliases(Collection<String> additionalAliases) {
        ArrayList<String> aliases = new ArrayList<>();
        aliases.add("node1");
        aliases.add("node1");
        aliases.add("node1");
        aliases.add("node2");
        aliases.addAll(additionalAliases);
        return aliases;
    }

    private Map<String, HostSpec> allocate(HostsXmlProvisioner hostProvisioner, List<String> aliases) {
        Map<String, HostSpec> map = new LinkedHashMap<>();
        for (String alias : aliases) {
            map.put(alias, hostProvisioner.allocateHost(alias));
        }
        return map;
    }

    @Test
    public void require_singlenode_HostAlias_is_used_if_hosts_xml() {
        String servicesXml = "<jdisc id='default' version='1.0' />";
        HostsXmlProvisioner hostProvisioner = createProvisioner(oneHost);
        HostSpec hostSpec = hostProvisioner.allocateHost(Container.SINGLENODE_CONTAINER_SERVICESPEC);
        assertThat(hostSpec.hostname(), is("test1.yahoo.com"));
    }
}

