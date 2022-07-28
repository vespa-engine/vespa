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
 */
public class ResourcesReductionValidatorTest {

    private final InMemoryProvisioner provisioner = new InMemoryProvisioner(30, new NodeResources(64, 128, 1000, 10), false);
    private final ValidationTester tester = new ValidationTester(provisioner);

    @Test
    void fail_when_reduction_by_over_50_percent() {
        VespaModel previous = tester.deploy(null, getServices(new NodeResources(8, 64, 800, 1)), Environment.prod, null).getFirst();
        try {
            tester.deploy(previous, getServices(new NodeResources(8, 16, 800, 1)), Environment.prod, null);
            fail("Expected exception due to resources reduction");
        } catch (IllegalArgumentException expected) {
            assertEquals("resources-reduction: Resource reduction in 'default' is too large. " +
                    "Current memory GB: 64.00, new: 16.00. New min resources must be at least 50% of the current min resources. " +
                    ValidationOverrides.toAllowMessage(ValidationId.resourcesReduction),
                    Exceptions.toMessageString(expected));
        }
    }

    @Test
    void fail_when_reducing_multiple_resources_by_over_50_percent() {
        VespaModel previous = tester.deploy(null, getServices(new NodeResources(8, 64, 800, 1)), Environment.prod, null).getFirst();
        try {
            tester.deploy(previous, getServices(new NodeResources(3, 16, 200, 1)), Environment.prod, null);
            fail("Expected exception due to resources reduction");
        } catch (IllegalArgumentException expected) {
            assertEquals("resources-reduction: Resource reduction in 'default' is too large. " +
                    "Current vCPU: 8.00, new: 3.00. Current memory GB: 64.00, new: 16.00. Current disk GB: 800.00, new: 200.00. " +
                    "New min resources must be at least 50% of the current min resources. " +
                    ValidationOverrides.toAllowMessage(ValidationId.resourcesReduction),
                    Exceptions.toMessageString(expected));
        }
    }

    @Test
    void small_resource_decrease_is_allowed() {
        VespaModel previous = tester.deploy(null, getServices(new NodeResources(1.5, 64, 800, 1)), Environment.prod, null).getFirst();
        tester.deploy(previous, getServices(new NodeResources(.5, 48, 600, 1)), Environment.prod, null);
    }

    @Test
    void overriding_resource_decrease() {
        VespaModel previous = tester.deploy(null, getServices(new NodeResources(8, 64, 800, 1)), Environment.prod, null).getFirst();
        tester.deploy(previous, getServices(new NodeResources(8, 16, 800, 1)), Environment.prod, resourcesReductionOverride); // Allowed due to override
    }

    @Test
    void allowed_to_go_to_not_specifying_resources() {
        VespaModel previous = tester.deploy(null, getServices(new NodeResources(1.5, 64, 800, 1)), Environment.prod, null).getFirst();
        tester.deploy(previous, getServices(null), Environment.prod, null);
    }

    @Test
    void allowed_to_go_from_not_specifying_resources() {
        VespaModel previous = tester.deploy(null, getServices(null), Environment.prod, null).getFirst();
        tester.deploy(previous, getServices(new NodeResources(1.5, 64, 800, 1)), Environment.prod, null);
    }

    private static String getServices(NodeResources resources) {
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
                "    <nodes count='5'>" +
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