// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.OnnxModelCost;
import com.yahoo.config.model.api.OnnxModelOptions;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.text.Text;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
class JvmHeapSizeValidatorTest {

    @Test
    void fails_on_too_low_jvm_percentage() throws IOException, SAXException {
        var deployState = createDeployState(8, 7L * 1024 * 1024 * 1024);
        var model = new VespaModel(new NullConfigModelRegistry(), deployState);
        var e = assertThrows(IllegalArgumentException.class, () -> new JvmHeapSizeValidator().validate(model, deployState));
        String expectedMessage = "Allocated percentage of memory of JVM in cluster 'container' is too low (3% < 15%). Estimated cost of ONNX models is 7.00GB";
        assertTrue(e.getMessage().contains(expectedMessage), e.getMessage());
    }

    @Test
    void fails_on_too_low_heap_size() throws IOException, SAXException {
        var deployState = createDeployState(2.2, 1024L * 1024 * 1024);
        var model = new VespaModel(new NullConfigModelRegistry(), deployState);
        var e = assertThrows(IllegalArgumentException.class, () -> new JvmHeapSizeValidator().validate(model, deployState));
        String expectedMessage = "Allocated memory to JVM in cluster 'container' is too low (0.50GB < 0.60GB). Estimated cost of ONNX models is 1.00GB.";
        assertTrue(e.getMessage().contains(expectedMessage), e.getMessage());
    }

    @Test
    void accepts_adequate_heap_size() throws IOException, SAXException {
        var deployState = createDeployState(8, 1024L * 1024 * 1024);
        var model = new VespaModel(new NullConfigModelRegistry(), deployState);
        assertDoesNotThrow(() -> new JvmHeapSizeValidator().validate(model, deployState));
    }

    @Test
    void accepts_services_with_explicit_jvm_size() throws IOException, SAXException {
        String servicesXml =
                """
                <?xml version="1.0" encoding="utf-8" ?>
                <services version='1.0'>
                    <container version='1.0'>
                        <nodes count="2">
                            <jvm allocated-memory='5%'/>
                            <resources vcpu="4" memory="2Gb" disk="125Gb"/>
                        </nodes>
                        <component id="hf-embedder" type="hugging-face-embedder">
                            <transformer-model url="https://my/url/model.onnx"/>
                            <tokenizer-model path="app/tokenizer.json"/>
                        </component>
                    </container>
                </services>""";
        var deployState = createDeployState(servicesXml, 2, 1024L * 1024 * 1024);
        var model = new VespaModel(new NullConfigModelRegistry(), deployState);
        assertDoesNotThrow(() -> new JvmHeapSizeValidator().validate(model, deployState));
    }

    private static DeployState createDeployState(String servicesXml, double nodeGb, long modelCostBytes) {
        return new DeployState.Builder()
                .applicationPackage(
                        new MockApplicationPackage.Builder()
                                .withServices(servicesXml)
                                .build())
                .modelHostProvisioner(new InMemoryProvisioner(5, new NodeResources(4, nodeGb, 125, 0.3), true))
                .endpoints(Set.of(new ContainerEndpoint("container", ApplicationClusterEndpoint.Scope.zone, List.of("c.example.com"))))
                .properties(new TestProperties().setHostedVespa(true).setDynamicHeapSize(true))
                .onnxModelCost(new ModelCostDummy(modelCostBytes))
                .build();
    }

    private static DeployState createDeployState(double nodeGb, long modelCostBytes) {
        String servicesXml =
                Text.format("""
                <?xml version="1.0" encoding="utf-8" ?>
                <services version='1.0'>
                    <container version='1.0'>
                        <nodes count="2">
                            <resources vcpu="4" memory="%fGb" disk="125Gb"/>
                        </nodes>
                        <component id="hf-embedder" type="hugging-face-embedder">
                            <transformer-model url="https://my/url/model.onnx"/>
                            <tokenizer-model path="app/tokenizer.json"/>
                        </component>
                    </container>
                </services>""", nodeGb);
        return createDeployState(servicesXml, nodeGb, modelCostBytes);
    }

    private static class ModelCostDummy implements OnnxModelCost, OnnxModelCost.Calculator {
        final AtomicLong totalCost = new AtomicLong();
        final long modelCost;

        ModelCostDummy(long modelCost) { this.modelCost = modelCost; }

        @Override public Calculator newCalculator(ApplicationPackage appPkg, ApplicationId applicationId) { return this; }
        @Override public Map<String, ModelInfo> models() { return Map.of(); }
        @Override public void setRestartOnDeploy() {}
        @Override public boolean restartOnDeploy() { return false;}
        @Override public long aggregatedModelCostInBytes() { return totalCost.get(); }
        @Override public void registerModel(ApplicationFile path) {}
        @Override public void registerModel(ApplicationFile path, OnnxModelOptions onnxModelOptions) {}

        @Override
        public void registerModel(URI uri) {
            assertEquals("https://my/url/model.onnx", uri.toString());
            totalCost.addAndGet(modelCost);
        }

        @Override
        public void registerModel(URI uri, OnnxModelOptions onnxModelOptions) {
            assertEquals("https://my/url/model.onnx", uri.toString());
            totalCost.addAndGet(modelCost);
        }

    }

}
