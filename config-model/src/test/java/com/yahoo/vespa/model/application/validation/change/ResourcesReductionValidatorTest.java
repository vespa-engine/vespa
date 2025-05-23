// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author freva
 * @author bratseth
 */
public class ResourcesReductionValidatorTest {

    private static final String CONTAINER_CLUSTER = "default.indexing";

    private final NodeResources hostResources = new NodeResources(64, 128, 1000, 10);
    private final InMemoryProvisioner provisioner           = new InMemoryProvisioner(30, hostResources, true, InMemoryProvisioner.defaultHostResources);
    private final InMemoryProvisioner provisionerSelfHosted = new InMemoryProvisioner(30, hostResources, true, NodeResources.unspecified());
    private final NodeResources defaultResources = InMemoryProvisioner.defaultHostResources;
    private final ValidationTester tester = new ValidationTester(provisioner);

    @Test
    void ok_when_reduction_by_over_50_percent_in_hosted_dev() {
        var fromResources = new NodeResources(8, 64, 800, 1);
        var toResources = new NodeResources(8, 16, 800, 1);
        VespaModel previous = tester.deploy(null, contentServices(6, fromResources), Environment.dev, null, CONTAINER_CLUSTER).getFirst();
        tester.deploy(previous, contentServices(6, toResources), Environment.dev, null, CONTAINER_CLUSTER);
    }

    @Test
    void fail_when_reduction_by_over_50_percent() {
        var fromResources = new NodeResources(8, 64, 800, 1);
        var toResources = new NodeResources(8, 16, 800, 1);
        VespaModel previous = tester.deploy(null, contentServices(6, fromResources), Environment.prod, null, CONTAINER_CLUSTER).getFirst();
        try {
            tester.deploy(previous, contentServices(6, toResources), Environment.prod, null, CONTAINER_CLUSTER);
            fail("Expected exception due to resources reduction");
        } catch (IllegalArgumentException expected) {
            assertResourceReductionException(expected, "from 64.0 to 16.0 memory GiB per node");
        }
    }

    @Test
    void fail_when_reducing_multiple_resources_by_over_50_percent() {
        var fromResources = new NodeResources(8, 64, 800, 1);
        var toResources = new NodeResources(3, 16, 200, 1);
        VespaModel previous = tester.deploy(null, contentServices(6, fromResources), Environment.prod, null, CONTAINER_CLUSTER).getFirst();
        try {
            tester.deploy(previous, contentServices(6, toResources), Environment.prod, null, CONTAINER_CLUSTER);
            fail("Expected exception due to resources reduction");
        } catch (IllegalArgumentException expected) {
            assertResourceReductionException(expected, "from 8.0 to 3.0 vcpu per node");
        }
    }

    @Test
    void small_resource_decrease_is_allowed() {
        VespaModel previous = tester.deploy(null, contentServices(6, new NodeResources(1.5, 64, 800, 1)), Environment.prod, null, CONTAINER_CLUSTER).getFirst();
        tester.deploy(previous, contentServices(6, new NodeResources(.5, 48, 600, 1)), Environment.prod, null, CONTAINER_CLUSTER);
    }

    @Test
    void reorganizing_resources_is_allowed() {
        VespaModel previous = tester.deploy(null, contentServices(12, new NodeResources(2, 10, 100, 1)), Environment.prod, null, CONTAINER_CLUSTER).getFirst();
        tester.deploy(previous, contentServices(4, new NodeResources(6, 30, 300, 1)), Environment.prod, null, CONTAINER_CLUSTER);
    }

    @Test
    void overriding_resource_decrease() {
        VespaModel previous = tester.deploy(null, contentServices(6, new NodeResources(8, 64, 800, 1)), Environment.prod, null, CONTAINER_CLUSTER).getFirst();
        tester.deploy(previous, contentServices(6, new NodeResources(8, 16, 800, 1)), Environment.prod, resourcesReductionOverride, CONTAINER_CLUSTER); // Allowed due to override
    }

    @Test
    void reduction_is_detected_when_going_from_unspecified_resources_container() {
        NodeResources toResources = defaultResources.withDiskGb(defaultResources.diskGb() / 5);
        try {
            VespaModel previous = tester.deploy(null, containerServices(6, null), Environment.prod, null).getFirst();
            tester.deploy(previous, containerServices(6, toResources), Environment.prod, null);
            fail("Expected exception due to resources reduction");
        }
        catch (IllegalArgumentException expected) {
            assertResourceReductionException(expected, "from 50.0 to 10.0 disk Gb per node");
        }
    }

    @Test
    void reduction_is_detected_when_going_to_unspecified_resources_container() {
        NodeResources fromResources = defaultResources.withVcpu(defaultResources.vcpu() * 3);
        try {
            VespaModel previous = tester.deploy(null, containerServices(6, fromResources), Environment.prod, null).getFirst();
            tester.deploy(previous, containerServices(6, null), Environment.prod, null);
            fail("Expected exception due to resources reduction");
        }
        catch (IllegalArgumentException expected) {
            assertResourceReductionException(expected, "from 3.0 to 1.0 vcpu per node");
        }
    }

    @Test
    void reduction_is_detected_when_going_from_unspecified_resources_content() {
        NodeResources toResources = defaultResources.withDiskGb(defaultResources.diskGb() / 5);
        try {
            VespaModel previous = tester.deploy(null, contentServices(6, null), Environment.prod, null, CONTAINER_CLUSTER).getFirst();
            tester.deploy(previous, contentServices(6, toResources), Environment.prod, null, CONTAINER_CLUSTER);
            fail("Expected exception due to resources reduction");
        } catch (IllegalArgumentException expected) {
            assertResourceReductionException(expected, "from 50.0 to 10.0 disk Gb per node");
        }
    }

    @Test
    void reduction_is_detected_when_going_to_unspecified_resources_content() {
        NodeResources fromResources = defaultResources.withVcpu(defaultResources.vcpu() * 3);
        try {
            VespaModel previous = tester.deploy(null, contentServices(6, fromResources), Environment.prod, null, CONTAINER_CLUSTER).getFirst();
            tester.deploy(previous, contentServices(6, null), Environment.prod, null, CONTAINER_CLUSTER);
            fail("Expected exception due to resources reduction");
        }
        catch (IllegalArgumentException expected) {
            assertResourceReductionException(expected, "from 3.0 to 1.0 vcpu per node");
        }
    }

    @Test
    void testSizeReductionValidationWithUnspecifiedResourcesHosted() {
        int fromNodes = 30;
        int toNodes = 14;
        try {
            ValidationTester tester = new ValidationTester(33);
            VespaModel previous = tester.deploy(null, contentServices(fromNodes, null), Environment.prod, null, CONTAINER_CLUSTER).getFirst();
            tester.deploy(previous, contentServices(toNodes, null), Environment.prod, null, CONTAINER_CLUSTER);
            fail("Expected exception due to resources reduction");
        }
        catch (IllegalArgumentException expected) {
            assertResourceReductionException(expected, "group size from 30 to 14 nodes");
        }
    }

    /** Emulate a self-hosted setup in only the sense that it does not set node resources on the provisioned hosts. */
    @Test
    void testSizeReductionValidationSelfhosted() {
        var tester = new ValidationTester(provisionerSelfHosted);

        VespaModel previous = tester.deploy(null, contentServices(10, null), Environment.prod, null, CONTAINER_CLUSTER).getFirst();
        try {
            tester.deploy(previous, contentServices(4, null), Environment.prod, null, CONTAINER_CLUSTER);
            fail("Expected exception due to resources reduction");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("resources-reduction: Effective resource reduction in cluster 'default' is too large: " +
                         "group size from 10 to 4 nodes. " +
                         "To protect against mistakes, changes causing load increases of more than 100% are blocked. " +
                         ValidationOverrides.toAllowMessage(ValidationId.resourcesReduction),
                         Exceptions.toMessageString(expected));
        }
    }

    @Test
    void testSizeReductionValidationMinimalDecreaseIsAllowed() {
        ValidationTester tester = new ValidationTester(30);

        VespaModel previous = tester.deploy(null, contentServices(3, null), Environment.prod, null, CONTAINER_CLUSTER).getFirst();
        tester.deploy(previous, contentServices(2, null), Environment.prod, null, CONTAINER_CLUSTER);
    }

    @Test
    void testOverridingSizeReductionValidation() {
        ValidationTester tester = new ValidationTester(33);

        VespaModel previous = tester.deploy(null, contentServices(30, null), Environment.prod, null, CONTAINER_CLUSTER).getFirst();
        tester.deploy(previous, contentServices(14, null), Environment.prod, resourcesReductionOverride, CONTAINER_CLUSTER); // Allowed due to override
    }

    @Test
    void testRedundancyIncreaseValidation() {
        VespaModel previous = tester.deploy(null, getServices(1, 3, 1), Environment.prod, null, "contentClusterId.indexing").getFirst();
        try {
            tester.deploy(previous, getServices(3, 3, 1), Environment.prod, null, "contentClusterId.indexing");
            fail("Expected exception due to redundancy increase");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("resources-reduction: " +
                         "Effective resource reduction in cluster 'contentClusterId' is too large: " +
                         "redundancy from 1 to 3. " +
                         "To protect against mistakes, changes causing load increases of more than 100% are blocked. " +
                         ValidationOverrides.toAllowMessage(ValidationId.resourcesReduction),
                         Exceptions.toMessageString(expected));
        }
    }

    @Test
    void testNoRedundancyIncreaseValidationErrorWhenIncreasingGroupsAndNodes() {
        VespaModel previous = tester.deploy(null, getServices(1, 2, 2), Environment.prod, null, "contentClusterId.indexing").getFirst();
        tester.deploy(previous, getServices(1, 4, 4), Environment.prod, null, "contentClusterId.indexing");
    }

    @Test
    void testRedundancyIncreaseValidationWithGroups() {
        // Changing redundancy from 1 to 2 is allowed when having 2 nodes in 2 groups versus 3 nodes in 1 group
        // (effective redundancy for the cluster is 2 in both cases)
        VespaModel previous = tester.deploy(null, getServices(1, 2, 2), Environment.prod, null, "contentClusterId.indexing").getFirst();
        tester.deploy(previous, getServices(2, 3, 1), Environment.prod, null, "contentClusterId.indexing");
    }

    @Test
    void testRedundancyDecreaseWithTooLargeNodeDecrease() {
        try {
            VespaModel previous = tester.deploy(null, getServices(3, 7, 1), Environment.prod, null, "contentClusterId.indexing").getFirst();
            tester.deploy(previous, getServices(2, 2, 1), Environment.prod, null, "contentClusterId.indexing");
            fail("Expected exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("resources-reduction: Effective resource reduction in cluster 'contentClusterId' is too large: " +
                         "redundancy from 3 to 2, group size from 7 to 2 nodes. " +
                         "To protect against mistakes, changes causing load increases of more than 100% are blocked. " +
                         ValidationOverrides.toAllowMessage(ValidationId.resourcesReduction),
                         expected.getMessage());
        }
    }

    @Test
    void testOverridingContentRemovalValidation() {
        VespaModel previous = tester.deploy(null, getServices(2, 3, 1), Environment.prod, null, "contentClusterId.indexing").getFirst();
        tester.deploy(previous, getServices(3, 3, 1), Environment.prod, redundancyIncreaseOverride, "contentClusterId.indexing"); // Allowed due to override
    }

    private static String getServices(int redundancy, int nodes, int groups) {
        return "<services version='1.0'>" +
               "  <content id='contentClusterId' version='1.0'>" +
               "    <redundancy>" + redundancy + "</redundancy>" +
               "    <engine>" +
               "    <proton/>" +
               "    </engine>" +
               "    <documents>" +
               "      <document type='music' mode='index'/>" +
               "    </documents>" +
               "    <nodes count='" + nodes + "' groups='" + groups + "'/>" +
               "   </content>" +
               "</services>";
    }

    private static final String redundancyIncreaseOverride =
            "<validation-overrides>\n" +
            "    <allow until='2000-01-03'>redundancy-increase</allow>\n" +
            "</validation-overrides>\n";

    private void assertResourceReductionException(Exception e, String change) {
        assertEquals("resources-reduction: Effective resource reduction in cluster 'default' is too large: " +
                     change + ". " +
                     "To protect against mistakes, changes causing load increases of more than 100% are blocked. " +
                     ValidationOverrides.toAllowMessage(ValidationId.resourcesReduction),
                     Exceptions.toMessageString(e));
    }

    private static String containerServices(int nodes, NodeResources resources) {
        String resourcesStr = resources == null ?
                "" :
                String.format("        <resources vcpu='%.0f' memory='%.0fG' disk='%.0fG'/>",
                        resources.vcpu(), resources.memoryGiB(), resources.diskGb());
        return "<services version='1.0'>" +
                "  <container id='default' version='1.0'>" +
                "    <nodes count='" + nodes + "'>" +
                resourcesStr +
                "    </nodes>" +
                "   </container>" +
                "</services>";
    }

    private static String contentServices(int nodes, NodeResources resources) {
        String resourcesStr = resources == null ?
                              "" :
                              String.format("        <resources vcpu='%.0f' memory='%.0fG' disk='%.0fG'/>",
                                            resources.vcpu(), resources.memoryGiB(), resources.diskGb());
        return "<services version='1.0'>" +
               "  <content id='default' version='1.0'>" +
               "    <redundancy>1</redundancy>" +
               "    <engine>" +
               "    <proton/>" +
               "    </engine>" +
               "    <documents>" +
               "      <document type='music' mode='index'/>" +
               "    </documents>" +
               "    <nodes count='" + nodes + "'>" +
               resourcesStr +
               "    </nodes>" +
               "   </content>" +
               "</services>";
    }

    private static final String resourcesReductionOverride =
            "<validation-overrides>\n" +
            "    <allow until='2000-01-03'>resources-reduction</allow>\n" +
            "</validation-overrides>\n";

}
