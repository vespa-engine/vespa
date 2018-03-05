// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
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
import static org.junit.Assert.assertTrue;

/**
 * @author hmusum
 */
public class SingleNodeProvisionerTest {

    @Test
    public void require_basic_works() {
        SingleNodeProvisioner hostProvisioner = new SingleNodeProvisioner();

        // 4 services, 2 host aliases, mapping to 2 host.
        List<String> aliases = createAliases();
        Map<String, HostSpec> map = allocate(hostProvisioner, aliases);
        assertCorrectNumberOfHost(map, 1);
        assertThat(map.size(), is(2));
        assertTrue(map.keySet().containsAll(aliases));

        // 5 services, 3 host aliases, mapping to 2 host.
        aliases = createAliases(Collections.singletonList("node3"));
        map = allocate(hostProvisioner, aliases);

        assertCorrectNumberOfHost(map, 1);
        assertThat(map.size(), is(3));
        assertTrue(map.keySet().containsAll(aliases));

        // 5 services, 3 host aliases, mapping to 3 host.
        aliases = createAliases(Collections.singletonList("node4"));
        map = allocate(hostProvisioner, aliases);
        assertThat(map.size(), is(3));
        assertCorrectNumberOfHost(map, 1);
        assertTrue(map.keySet().containsAll(aliases));
    }

    @Test
    public void require_allocate_clustermembership_works() throws IOException, SAXException {
        String servicesXml = "<services version='1.0'>"
                           + "  <admin version='3.0'>"
                           + "    <nodes count='1' />"
                           + "  </admin>"
                           + "  <jdisc version='1.0'>"
                           + "    <search />"
                           + "    <nodes count='1' />"
                           + "  </jdisc>"
                           + "</services>";
        ApplicationPackage app = new MockApplicationPackage.Builder().withServices(servicesXml).build();
        VespaModel model = new VespaModel(app);
        assertThat(model.getHosts().size(), is(1));
    }


    private Map<String, HostSpec> allocate(HostProvisioner provisioner, List<String> aliases) {
        Map<String, HostSpec> map = new LinkedHashMap<>();
        for (String alias : aliases) {
            map.put(alias, provisioner.allocateHost(alias));
        }
        return map;
    }


    private void assertCorrectNumberOfHost(Map<String, HostSpec> hostToServiceMap, int expectedHostCount) {
        Set<String> hostSet = new HashSet<>();
        for (HostSpec host : hostToServiceMap.values()) {
            hostSet.add(host.hostname());
        }
        assertThat(hostSet.size(), is(expectedHostCount));
    }

    private List<String> createAliases() {
        return createAliases(new ArrayList<String>());
    }

    // Admin services on node1, qrserver on node2 + additional specs
    private List<String> createAliases(Collection<String> additionalAliases) {
        List<String> aliases = new ArrayList<>();
        aliases.add("node1");
        aliases.add("node1");
        aliases.add("node1");
        aliases.add("node2");
        aliases.addAll(additionalAliases);
        return aliases;
    }

}
