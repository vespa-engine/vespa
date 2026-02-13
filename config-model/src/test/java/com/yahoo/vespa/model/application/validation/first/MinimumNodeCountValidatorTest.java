// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.first;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author hmusum
 */
public class MinimumNodeCountValidatorTest {

    private static final Zone zone = new Zone(SystemName.main, Environment.prod, RegionName.from("us-east"));

    private final ValidationTester tester = new ValidationTester(7, false,
                                                                 new TestProperties().setFirstTimeDeployment(true)
                                                                                     .setHostedVespa(true),
                                                                 zone);

    @Test
    void testMinimumNodeCountValidationForContentCluster() {
        try {
            // Content cluster with 1 node will trigger both minimum-node-count and redundancy-one validators
            tester.deploy(null, getContentClusterServices(1, 1), zone, null, "contentClusterId.indexing");
            fail("Expected exception due to too few nodes");
        }
        catch (Exception expected) {
            String message = Exceptions.toMessageString(expected);
            // Verify that minimum-node-count validation is triggered for both content and indexing clusters
            assert message.contains("minimum-node-count") : "Expected 'minimum-node-count' in: " + message;
            assert message.contains("content cluster 'contentClusterId'") : "Expected content cluster in: " + message;
            assert message.contains("contentClusterId.indexing") : "Expected indexing cluster in: " + message;
        }
    }

    @Test
    void testMinimumNodeCountValidationForContainerCluster() {
        try {
            tester.deploy(null, getContainerClusterServices(1), zone, null);
            fail("Expected exception due to too few nodes");
        }
        catch (Exception expected) {
            String message = Exceptions.toMessageString(expected);
            assert message.contains("Deploying clusters with fewer than 2 nodes in production requires a validation override on first deployment");
            assert message.contains("container cluster 'default'");
        }
    }

    @Test
    void testOverridingMinimumNodeCountValidation() {
        assertDoesNotThrow(() -> {
            // Need both overrides since content cluster with 1 node triggers both validators
            tester.deploy(null, getContentClusterServices(1, 1), zone, bothOverrides, "contentClusterId.indexing");
        });
        assertDoesNotThrow(() -> {
            tester.deploy(null, getContainerClusterServices(1), zone, minimumNodeCountOverride);
        });
    }

    @Test
    void testMinimumNodeCountValidationPasses() {
        // Should not throw exception when we have 2 or more nodes
        assertDoesNotThrow(() -> {
            tester.deploy(null, getContentClusterServices(2, 2), zone, null, "contentClusterId.indexing");
        });
        assertDoesNotThrow(() -> {
            tester.deploy(null, getContainerClusterServices(2), zone, null);
        });
    }

    private static String getContentClusterServices(int nodeCount, int redundancy) {
        return "<services version='1.0'>" +
               "  <content id='contentClusterId' version='1.0'>" +
               "    <redundancy>" + redundancy + "</redundancy>" +
               "    <engine>" +
               "    <proton/>" +
               "    </engine>" +
               "    <documents>" +
               "      <document type='music' mode='index'/>" +
               "    </documents>" +
               "    <nodes count='" + nodeCount + "'/>" +
               "   </content>" +
               "</services>";
    }

    private static String getContainerClusterServices(int nodeCount) {
        return "<services version='1.0'>" +
               "  <container id='default' version='1.0'>" +
               "    <nodes count='" + nodeCount + "'/>" +
               "   </container>" +
               "</services>";
    }

    private static final String minimumNodeCountOverride =
            """
                    <validation-overrides>
                        <allow until='2000-01-03'>minimum-node-count</allow>
                    </validation-overrides>
                    """;

    private static final String bothOverrides =
            """
                    <validation-overrides>
                        <allow until='2000-01-03'>minimum-node-count</allow>
                        <allow until='2000-01-03'>redundancy-one</allow>
                    </validation-overrides>
                    """;

}
