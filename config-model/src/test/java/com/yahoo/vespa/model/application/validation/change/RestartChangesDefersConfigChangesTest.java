// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class RestartChangesDefersConfigChangesTest {

    @Test
    void changes_requiring_restart_defers_config_changes() {
        ValidationTester tester = new ValidationTester(new InMemoryProvisioner(5,
                new NodeResources(1, 3, 9, 1),
                true));
        VespaModel gen1 = tester.deploy(null, getServices(5, 3), Environment.prod, null).getFirst();

        // Change node count - no restart
        VespaModel gen2 = tester.deploy(gen1, getServices(4, 3), Environment.prod, null).getFirst();
        var config2 = new ComponentsConfig.Builder();
        gen2.getContainerClusters().get("default").getContainers().get(0).getConfig(config2);
        assertFalse(config2.getApplyOnRestart());

        // Change memory amount - requires restart
        VespaModel gen3 = tester.deploy(gen2, getServices(4, 2), Environment.prod, null).getFirst();
        var config3 = new ComponentsConfig.Builder();
        gen3.getContainerClusters().get("default").getContainers().get(0).getConfig(config3);
        assertTrue(config3.getApplyOnRestart());
    }

    private static String getServices(int nodes, int memory) {
        return "<services version='1.0'>" +
               "  <container id='default' version='1.0'>" +
               "    <nodes count='" + nodes + "'><resources vcpu='1' memory='" + memory + "Gb' disk='9Gb'/></nodes>" +
               "   </container>" +
               "</services>";
    }

}
