// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.text.Text;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * @author gleb
 */
class InferenceMemoryValidatorTest {

    @Test
    void fails_when_inference_memory_exceeds_node_memory() throws IOException, SAXException {
        var deployState = createDeployState(4.0, "5Gb");
        var model = new VespaModel(new NullConfigModelRegistry(), deployState);
        ValidationTester.expect(new InferenceMemoryValidator(), model, deployState,
                "Inference memory in cluster 'container' (5.00 GiB) cannot exceed available node memory (4.00 GiB)");
    }

    @Test
    void accepts_when_inference_memory_within_node_memory() throws IOException, SAXException {
        var deployState = createDeployState(8.0, "4Gb");
        var model = new VespaModel(new NullConfigModelRegistry(), deployState);
        assertDoesNotThrow(() -> ValidationTester.validate(new InferenceMemoryValidator(), model, deployState));
    }

    @Test
    void accepts_when_inference_memory_equals_node_memory() throws IOException, SAXException {
        var deployState = createDeployState(4.0, "4Gb");
        var model = new VespaModel(new NullConfigModelRegistry(), deployState);
        assertDoesNotThrow(() -> ValidationTester.validate(new InferenceMemoryValidator(), model, deployState));
    }

    @Test
    void accepts_when_no_inference_memory_specified() throws IOException, SAXException {
        String servicesXml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <services version='1.0'>
                    <container version='1.0'>
                        <nodes count="2">
                            <resources vcpu="4" memory="4Gb" disk="125Gb"/>
                        </nodes>
                    </container>
                </services>""";
        var deployState = createDeployState(servicesXml, 4.0);
        var model = new VespaModel(new NullConfigModelRegistry(), deployState);
        assertDoesNotThrow(() -> ValidationTester.validate(new InferenceMemoryValidator(), model, deployState));
    }

    @Test
    void accepts_when_containers_have_different_memory_and_smallest_is_sufficient() throws IOException, SAXException {
        // This tests the min() logic in the validator
        var deployState = createDeployState(6.0, "5Gb");
        var model = new VespaModel(new NullConfigModelRegistry(), deployState);
        assertDoesNotThrow(() -> ValidationTester.validate(new InferenceMemoryValidator(), model, deployState));
    }

    private static DeployState createDeployState(double nodeGb, String inferenceMemory) {
        String servicesXml = Text.format("""
                <?xml version="1.0" encoding="utf-8" ?>
                <services version='1.0'>
                    <container version='1.0'>
                        <inference>
                            <memory>%s</memory>
                        </inference>
                        <nodes count="2">
                            <resources vcpu="4" memory="%fGb" disk="125Gb"/>
                        </nodes>
                    </container>
                </services>""", inferenceMemory, nodeGb);
        return createDeployState(servicesXml, nodeGb);
    }

    private static DeployState createDeployState(String servicesXml, double nodeGb) {
        return new DeployState.Builder()
                .applicationPackage(
                        new MockApplicationPackage.Builder()
                                .withServices(servicesXml)
                                .build())
                .modelHostProvisioner(new InMemoryProvisioner(5, new NodeResources(4, nodeGb, 125, 0.3), true))
                .endpoints(Set.of(new ContainerEndpoint("container", ApplicationClusterEndpoint.Scope.zone, List.of("c.example.com"))))
                .properties(new TestProperties().setHostedVespa(true))
                .build();
    }

}