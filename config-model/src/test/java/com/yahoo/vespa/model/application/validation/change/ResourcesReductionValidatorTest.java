// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    private final InMemoryProvisioner provisioner = new InMemoryProvisioner(30, new NodeResources(64, 128, 1000, 10), false);
    private final ValidationTester tester = new ValidationTester(provisioner);

    @Test
    void fail_when_reduction_by_over_50_percent() {
        VespaModel previous = tester.deploy(null, getServices(6, new NodeResources(8, 64, 800, 1)), Environment.prod, null).getFirst();
        try {
            tester.deploy(previous, getServices(6, new NodeResources(8, 16, 800, 1)), Environment.prod, null);
            fail("Expected exception due to resources reduction");
        } catch (IllegalArgumentException expected) {
            assertEquals("resources-reduction: Resource reduction in 'default' is too large: " +
                    "To guard against mistakes, the new max resources must be at least 50% " +
                    "of the current max resources in all dimensions. " +
                    "Current: [vcpu: 48.0, memory: 384.0 Gb, disk 4800.0 Gb, bandwidth: 1.8 Gbps, architecture: x86_64], " +
                    "new: [vcpu: 48.0, memory: 96.0 Gb, disk 4800.0 Gb, bandwidth: 1.8 Gbps, architecture: x86_64]. " +
                    ValidationOverrides.toAllowMessage(ValidationId.resourcesReduction),
                    Exceptions.toMessageString(expected));
        }
    }

    @Test
    void fail_when_reducing_multiple_resources_by_over_50_percent() {
        VespaModel previous = tester.deploy(null, getServices(6,new NodeResources(8, 64, 800, 1)), Environment.prod, null).getFirst();
        try {
            tester.deploy(previous, getServices(6, new NodeResources(3, 16, 200, 1)), Environment.prod, null);
            fail("Expected exception due to resources reduction");
        } catch (IllegalArgumentException expected) {
            assertEquals("resources-reduction: Resource reduction in 'default' is too large: " +
                         "To guard against mistakes, the new max resources must be at least 50% " +
                         "of the current max resources in all dimensions. " +
                         "Current: [vcpu: 48.0, memory: 384.0 Gb, disk 4800.0 Gb, bandwidth: 1.8 Gbps, architecture: x86_64], " +
                         "new: [vcpu: 18.0, memory: 96.0 Gb, disk 1200.0 Gb, bandwidth: 1.8 Gbps, architecture: x86_64]. " +
                    ValidationOverrides.toAllowMessage(ValidationId.resourcesReduction),
                    Exceptions.toMessageString(expected));
        }
    }

    @Test
    void small_resource_decrease_is_allowed() {
        VespaModel previous = tester.deploy(null, getServices(6, new NodeResources(1.5, 64, 800, 1)), Environment.prod, null).getFirst();
        tester.deploy(previous, getServices(6, new NodeResources(.5, 48, 600, 1)), Environment.prod, null);
    }

    @Test
    void reorganizing_resources_is_allowed() {
        VespaModel previous = tester.deploy(null, getServices(12, new NodeResources(2, 10, 100, 1)), Environment.prod, null).getFirst();
        tester.deploy(previous, getServices(4, new NodeResources(6, 30, 300, 1)), Environment.prod, null);
    }

    @Test
    void overriding_resource_decrease() {
        VespaModel previous = tester.deploy(null, getServices(6, new NodeResources(8, 64, 800, 1)), Environment.prod, null).getFirst();
        tester.deploy(previous, getServices(6, new NodeResources(8, 16, 800, 1)), Environment.prod, resourcesReductionOverride); // Allowed due to override
    }

    @Test
    void allowed_to_go_to_not_specifying_resources() {
        VespaModel previous = tester.deploy(null, getServices(6, new NodeResources(1.5, 64, 800, 1)), Environment.prod, null).getFirst();
        tester.deploy(previous, getServices(6, null), Environment.prod, null);
    }

    @Test
    void allowed_to_go_from_not_specifying_resources() {
        VespaModel previous = tester.deploy(null, getServices(6, null), Environment.prod, null).getFirst();
        tester.deploy(previous, getServices(6, new NodeResources(1.5, 64, 800, 1)), Environment.prod, null);
    }

    @Test
    void testSizeReductionValidation() {
        ValidationTester tester = new ValidationTester(33);

        VespaModel previous = tester.deploy(null, getServices(30, null), Environment.prod, null).getFirst();
        try {
            tester.deploy(previous, getServices(14, null), Environment.prod, null);
            fail("Expected exception due to resources reduction");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("resources-reduction: Size reduction in 'default' is too large: " +
                         "To guard against mistakes, the new max nodes must be at least 50% of the current nodes. " +
                         "Current nodes: 30, new nodes: 14. " +
                         ValidationOverrides.toAllowMessage(ValidationId.resourcesReduction),
                         Exceptions.toMessageString(expected));
        }
    }

    @Test
    void testSizeReductionValidationMinimalDecreaseIsAllowed() {
        ValidationTester tester = new ValidationTester(30);

        VespaModel previous = tester.deploy(null, getServices(3, null), Environment.prod, null).getFirst();
        tester.deploy(previous, getServices(2, null), Environment.prod, null);
    }

    @Test
    void testOverridingSizeReductionValidation() {
        ValidationTester tester = new ValidationTester(33);

        VespaModel previous = tester.deploy(null, getServices(30, null), Environment.prod, null).getFirst();
        tester.deploy(previous, getServices(14, null), Environment.prod, resourcesReductionOverride); // Allowed due to override
    }

    private static String getServices(int nodes, NodeResources resources) {
        String resourcesStr = resources == null ?
                "" :
                String.format("        <resources vcpu='%.0f' memory='%.0fG' disk='%.0fG'/>",
                        resources.vcpu(), resources.memoryGb(), resources.diskGb());
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