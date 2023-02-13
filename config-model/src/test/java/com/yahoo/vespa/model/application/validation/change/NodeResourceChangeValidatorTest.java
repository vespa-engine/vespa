// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class NodeResourceChangeValidatorTest {

    @Test
    void test_restart_action_count() {
        assertEquals(0, validate(model(1, 1, 1, 1), model(1, 1, 1, 1)).size());
        assertEquals(1, validate(model(1, 1, 1, 1), model(2, 1, 1, 1)).size());
        assertEquals(2, validate(model(1, 1, 1, 1), model(1, 2, 1, 1)).size());
        assertEquals(3, validate(model(1, 1, 1, 1), model(1, 1, 2, 1)).size());
        assertEquals(4, validate(model(1, 1, 1, 1), model(1, 1, 1, 2)).size());
        assertEquals(5, validate(model(1, 1, 1, 1), model(2, 1, 1, 2)).size());
        assertEquals(6, validate(model(1, 1, 1, 1), model(1, 2, 1, 2)).size());
        assertEquals(7, validate(model(1, 1, 1, 1), model(1, 1, 2, 2)).size());
        assertEquals(8, validate(model(1, 1, 1, 1), model(2, 1, 2, 2)).size());
        assertEquals(9, validate(model(1, 1, 1, 1), model(1, 2, 2, 2)).size());
        assertEquals(10, validate(model(1, 1, 1, 1), model(2, 2, 2, 2)).size());
    }

    @Test
    void test_restart_action_details() {
        ConfigChangeAction containerAction = validate(model(1, 1, 1, 1), model(2, 1, 1, 1)).get(0);
        assertEquals(ConfigChangeAction.Type.RESTART, containerAction.getType());
        assertEquals("service 'container' of type container on host0", containerAction.getServices().get(0).toString());
        assertEquals(false, containerAction.ignoreForInternalRedeploy());

        ConfigChangeAction contentAction = validate(model(1, 1, 1, 1), model(1, 1, 2, 1)).get(0);
        assertEquals(ConfigChangeAction.Type.RESTART, contentAction.getType());
        assertEquals("service 'searchnode' of type searchnode on host3", contentAction.getServices().get(0).toString());
        assertEquals(false, contentAction.ignoreForInternalRedeploy());
    }

    private List<ConfigChangeAction> validate(VespaModel current, VespaModel next) {
        return new NodeResourceChangeValidator().validate(current, next, new DeployState.Builder().build());
    }

    private static VespaModel model(int mem1, int mem2, int mem3, int mem4) {
        var properties = new TestProperties();
        properties.setHostedVespa(true);
        var deployState = new DeployState.Builder().properties(properties)
                                                   .modelHostProvisioner(new Provisioner());
        return new VespaModelCreatorWithMockPkg(
                null,
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<services version='1.0'>\n" +
                "    <container id='container1' version='1.0'>\n" +
                "       <nodes count='1'>\n" +
                "           <resources vcpu='1' memory='" + mem1 + "Gb' disk='100Gb'/>" +
                "       </nodes>\n" +
                "   </container>\n" +
                "    <container id='container2' version='1.0'>\n" +
                "       <nodes count='2'>\n" +
                "           <resources vcpu='1' memory='" + mem2 + "Gb' disk='100Gb'/>" +
                "       </nodes>\n" +
                "   </container>\n" +
                "   <content id='content1' version='1.0'>\n" +
                "       <nodes count='3'>\n" +
                "           <resources vcpu='1' memory='" + mem3 + "Gb' disk='100Gb'/>" +
                "       </nodes>\n" +
                "       <documents>\n" +
                "           <document mode='index' type='test'/>\n" +
                "       </documents>\n" +
                "       <redundancy>2</redundancy>\n" +
                "   </content>\n" +
                "   <content id='content2' version='1.0'>\n" +
                "       <nodes count='4'>\n" +
                "           <resources vcpu='1' memory='" + mem4 + "Gb' disk='100Gb'/>" +
                "       </nodes>\n" +
                "       <documents>\n" +
                "           <document mode='streaming' type='test'/>\n" +
                "       </documents>\n" +
                "       <redundancy>2</redundancy>\n" +
                "   </content>\n" +
                "</services>",
                List.of("schema test { document test {} }"))
                       .create(deployState);
    }

    private static class Provisioner implements HostProvisioner {

        private int hostsCreated = 0;

        @Override
        public HostSpec allocateHost(String alias) {
            return new HostSpec(alias, Optional.empty());
        }

        @Override
        public List<HostSpec> prepare(ClusterSpec cluster, Capacity capacity, ProvisionLogger logger) {
            List<HostSpec> hosts = new ArrayList<>();
            var resources = capacity.minResources().nodeResources();
            for (int i = 0; i < capacity.minResources().nodes(); i++)
                hosts.add(new HostSpec("host" + (hostsCreated++),
                                       resources, resources, resources,
                                       ClusterMembership.from(cluster, i),
                                       Optional.empty(), Optional.empty(), Optional.empty()));
            return hosts;
        }

    }

}
