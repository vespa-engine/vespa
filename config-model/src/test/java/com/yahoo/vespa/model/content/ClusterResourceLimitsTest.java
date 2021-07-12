// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.searchdefinition.derived.TestableDeployLogger;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.w3c.dom.Document;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author geirst
 */
public class ClusterResourceLimitsTest {

    private static class Fixture {
        private final boolean enableFeedBlockInDistributor;
        private final boolean hostedVespa;
        private final boolean throwIfSpecified;
        private final ResourceLimits.Builder ctrlBuilder = new ResourceLimits.Builder();
        private final ResourceLimits.Builder nodeBuilder = new ResourceLimits.Builder();

        public Fixture() {
            this(false);
        }

        public Fixture(boolean enableFeedBlockInDistributor) {
            this(enableFeedBlockInDistributor, false, false);
        }

        public Fixture(boolean enableFeedBlockInDistributor, boolean hostedVespa, boolean throwIfSpecified) {
            this.enableFeedBlockInDistributor = enableFeedBlockInDistributor;
            this.hostedVespa = hostedVespa;
            this.throwIfSpecified = throwIfSpecified;
        }

        public Fixture ctrlDisk(double limit) {
            ctrlBuilder.setDiskLimit(limit);
            return this;
        }
        public Fixture ctrlMemory(double limit) {
            ctrlBuilder.setMemoryLimit(limit);
            return this;
        }
        public Fixture nodeDisk(double limit) {
            nodeBuilder.setDiskLimit(limit);
            return this;
        }
        public Fixture nodeMemory(double limit) {
            nodeBuilder.setMemoryLimit(limit);
            return this;
        }
        public ClusterResourceLimits build() {
            ModelContext.FeatureFlags featureFlags = new TestProperties();
            var builder = new ClusterResourceLimits.Builder(enableFeedBlockInDistributor,
                                                            hostedVespa,
                                                            throwIfSpecified,
                                                            new BaseDeployLogger(),
                                                            featureFlags.resourceLimitDisk(),
                                                            featureFlags.resourceLimitMemory());
            builder.setClusterControllerBuilder(ctrlBuilder);
            builder.setContentNodeBuilder(nodeBuilder);
            return builder.build();
        }
    }

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void content_node_limits_are_derived_from_cluster_controller_limits_if_not_set() {
        assertLimits(0.4, 0.7, 0.7, 0.85,
                new Fixture().ctrlDisk(0.4).ctrlMemory(0.7));
        assertLimits(0.4, null, 0.7, null,
                new Fixture().ctrlDisk(0.4));
        assertLimits(null, 0.7, null, 0.85,
                new Fixture().ctrlMemory(0.7));


        assertLimits(0.4, 0.7, 0.7, 0.85,
                new Fixture(true).ctrlDisk(0.4).ctrlMemory(0.7));
        assertLimits(0.4, 0.8, 0.7, 0.9,
                new Fixture(true).ctrlDisk(0.4));
        assertLimits(0.8, 0.7, 0.9, 0.85,
                new Fixture(true).ctrlMemory(0.7));
    }

    @Test
    public void content_node_limits_can_be_set_explicit() {
        assertLimits(0.4, 0.7, 0.9, 0.95,
                new Fixture().ctrlDisk(0.4).ctrlMemory(0.7).nodeDisk(0.9).nodeMemory(0.95));
        assertLimits(0.4, null, 0.95, null,
                new Fixture().ctrlDisk(0.4).nodeDisk(0.95));
        assertLimits(null, 0.7, null, 0.95,
                new Fixture().ctrlMemory(0.7).nodeMemory(0.95));

        assertLimits(0.4, 0.7, 0.9, 0.95,
                new Fixture(true).ctrlDisk(0.4).ctrlMemory(0.7).nodeDisk(0.9).nodeMemory(0.95));
        assertLimits(0.4, 0.8, 0.95, 0.9,
                new Fixture(true).ctrlDisk(0.4).nodeDisk(0.95));
        assertLimits(0.8, 0.7, 0.9, 0.95,
                new Fixture(true).ctrlMemory(0.7).nodeMemory(0.95));
    }

    @Test
    public void cluster_controller_limits_are_equal_to_content_node_limits_minus_one_percent_if_not_set() {
        assertLimits(0.89, 0.94, 0.9, 0.95,
                new Fixture().nodeDisk(0.9).nodeMemory(0.95));
        assertLimits(0.89, null, 0.9, null,
                new Fixture().nodeDisk(0.9));
        assertLimits(null, 0.94, null, 0.95,
                new Fixture().nodeMemory(0.95));
        assertLimits(null, 0.0, null, 0.005,
                new Fixture().nodeMemory(0.005));

        assertLimits(0.89, 0.94, 0.9, 0.95,
                new Fixture(true).nodeDisk(0.9).nodeMemory(0.95));
    }

    @Test
    public void limits_are_derived_from_the_other_if_not_set() {
        assertLimits(0.6, 0.94, 0.8, 0.95,
                new Fixture().ctrlDisk(0.6).nodeMemory(0.95));
        assertLimits(0.89, 0.7, 0.9, 0.85,
                new Fixture().ctrlMemory(0.7).nodeDisk(0.9));
    }

    @Test
    public void default_resource_limits_when_feed_block_is_enabled_in_distributor() {
        assertLimits(0.8, 0.8, 0.9, 0.9,
                new Fixture(true));
    }

    @Test
    @Ignore // TODO: Remove hosted_limits_are_used_if_app_is_allowed_to_set_limits and enable this when code is fixed to do so
    public void hosted_log_when_resource_limits_are_specified() {
        TestableDeployLogger logger = new TestableDeployLogger();

        var limits = hostedBuildAndLogIfSpecified(logger);
        assertEquals(1, logger.warnings.size());
        assertEquals("Element 'resource-limits' is not allowed to be set", logger.warnings.get(0));

        // Verify that default limits are used
        assertLimits(0.8, 0.8, limits.getClusterControllerLimits());
        assertLimits(0.9, 0.9, limits.getContentNodeLimits());
    }

    @Test
    public void hosted_exception_is_thrown_when_resource_limits_are_specified() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(containsString("Element 'resource-limits' is not allowed to be set"));
        hostedBuildAndThrowIfSpecified();
    }

    @Test
    // TODO: Remove this and enable hosted_log_when_resource_limits_are_specified when code is fixed to do so
    public void hosted_limits_are_used_if_app_is_allowed_to_set_limits() {
        TestableDeployLogger logger = new TestableDeployLogger();

        var limits = hostedBuildAndLogIfSpecified(logger);
        assertEquals(1, logger.warnings.size());
        assertEquals("Element 'resource-limits' is not allowed to be set", logger.warnings.get(0));

        // Verify that limits in XML are used
        assertLimits(0.8, 0.92, limits.getClusterControllerLimits());
        assertLimits(0.9, 0.96, limits.getContentNodeLimits());
    }

    @Test
    public void hosted_limits_from_feature_flag_are_used() {
        TestableDeployLogger logger = new TestableDeployLogger();

        TestProperties featureFlags = new TestProperties();
        featureFlags.setResourceLimitDisk(0.85);
        featureFlags.setResourceLimitMemory(0.90);
        var limits = hostedBuild(false, logger, featureFlags, false);

        // Verify that limits from feature flags are used
        assertLimits(0.85, 0.90, limits.getClusterControllerLimits());
        assertLimits(0.925, 0.95, limits.getContentNodeLimits());
    }

    @Test
    public void exception_is_thrown_when_resource_limits_are_out_of_range() {
        TestableDeployLogger logger = new TestableDeployLogger();

        TestProperties featureFlags = new TestProperties();
        featureFlags.setResourceLimitDisk(1.1);
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(containsString("Resource limit for disk is set to illegal value 1.1, but must be in the range [0.0, 1.0]"));
        hostedBuild(false, logger, featureFlags, false);

        featureFlags = new TestProperties();
        featureFlags.setResourceLimitDisk(-0.1);
        expectedException.expectMessage(containsString("Resource limit for disk is set to illegal value -0.1, but must be in the range [0.0, 1.0]"));
        hostedBuild(false, logger, featureFlags, false);
    }

    private void hostedBuildAndThrowIfSpecified() {
        hostedBuild(true, new TestableDeployLogger(), new TestProperties(), true);
    }

    private ClusterResourceLimits hostedBuildAndLogIfSpecified(DeployLogger deployLogger) {
        return hostedBuild(false, deployLogger);
    }

    private ClusterResourceLimits hostedBuild(boolean throwIfSpecified, DeployLogger deployLogger) {
        return hostedBuild(throwIfSpecified, deployLogger, new TestProperties(), true);
    }

    private ClusterResourceLimits hostedBuild(boolean throwIfSpecified,
                                              DeployLogger deployLogger,
                                              ModelContext.FeatureFlags featureFlags,
                                              boolean limitsInXml) {
        Document clusterXml = XML.getDocument("<cluster id=\"test\">" +
                                              "  <tuning>\n" +
                                              "    <resource-limits>\n" +
                                              "      <memory>0.92</memory>\n" +
                                              "    </resource-limits>\n" +
                                              "  </tuning>\n" +
                                              "</cluster>");

        Document noLimitsXml = XML.getDocument("<cluster id=\"test\">" +
                                              "</cluster>");

        ClusterResourceLimits.Builder builder = new ClusterResourceLimits.Builder(true,
                                                                                  true,
                                                                                  throwIfSpecified,
                                                                                  deployLogger,
                                                                                  featureFlags.resourceLimitDisk(),
                                                                                  featureFlags.resourceLimitMemory());
        return builder.build(new ModelElement((limitsInXml ? clusterXml : noLimitsXml).getDocumentElement()));
    }

    private void assertLimits(Double expCtrlDisk, Double expCtrlMemory, Double expNodeDisk, Double expNodeMemory, Fixture f) {
        var limits = f.build();
        assertLimits(expCtrlDisk, expCtrlMemory, limits.getClusterControllerLimits());
        assertLimits(expNodeDisk, expNodeMemory, limits.getContentNodeLimits());
    }

    private void assertLimits(Double expDisk, Double expMemory, ResourceLimits limits) {
        assertLimit(expDisk, limits.getDiskLimit(), "disk");
        assertLimit(expMemory, limits.getMemoryLimit(), "memory");
    }

    private void assertLimit(Double expLimit, Optional<Double> actLimit, String limitType) {
        if (expLimit == null) {
            assertFalse(actLimit.isPresent());
        } else {
            assertEquals(limitType + " limit not as expected", expLimit, actLimit.get(), 0.00001);
        }
    }

}
