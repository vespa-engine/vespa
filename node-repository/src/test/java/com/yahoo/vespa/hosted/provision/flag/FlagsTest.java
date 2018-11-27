// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.flag;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.testutils.MockNodeFlavors;
import com.yahoo.vespa.hosted.provision.testutils.MockNodeRepository;
import org.junit.Test;

import java.util.function.Supplier;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class FlagsTest {

    @Test
    public void test_flag_toggling() {
        NodeRepository nodeRepository = new MockNodeRepository(new MockCurator(), new MockNodeFlavors());
        Flags flags = nodeRepository.flags();
        Supplier<Flag> flag = () -> flags.get(FlagId.exclusiveLoadBalancer);

        // Flag is disabled by default
        assertFalse(flag.get().isEnabled());

        // Toggle flag for a node
        {
            HostName node1 = HostName.from("host1");
            flags.setEnabled(FlagId.exclusiveLoadBalancer, node1, true);
            assertTrue(flag.get().isEnabled(node1));
            assertFalse(flag.get().isEnabled());
            flags.setEnabled(FlagId.exclusiveLoadBalancer, node1, false);
            assertFalse(flag.get().isEnabled(node1));
        }

        // Toggle flag for an application
        {
            ApplicationId app1 = ApplicationId.from("tenant1", "application1", "default");
            flags.setEnabled(FlagId.exclusiveLoadBalancer, app1, true);
            assertTrue(flag.get().isEnabled(app1));
            assertFalse(flag.get().isEnabled());
            flags.setEnabled(FlagId.exclusiveLoadBalancer, app1, false);
            assertFalse(flag.get().isEnabled(app1));
        }

        // Toggle flag globally
        {
            flags.setEnabled(FlagId.exclusiveLoadBalancer, true);
            assertTrue(flag.get().isEnabled());
            // Flag is implicitly enabled for all dimensions
            assertTrue(flag.get().isEnabled(HostName.from("host1")));
            assertTrue(flag.get().isEnabled(ApplicationId.from("tenant1", "application1", "default")));
            flags.setEnabled(FlagId.exclusiveLoadBalancer, false);
            assertFalse(flag.get().isEnabled());
        }
    }


}
