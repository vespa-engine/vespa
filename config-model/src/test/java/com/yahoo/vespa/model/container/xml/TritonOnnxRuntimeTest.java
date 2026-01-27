// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import ai.vespa.triton.TritonConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.TritonOnnxRuntime;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author glebashnik
 */
public class TritonOnnxRuntimeTest {
    @Test
    void testTritonWithDefaultConfiguration() throws Exception {
        String xml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <services version="1.0">
                <container id="container" version="1.0">
                    <triton>
                    </triton>
                </container>
            </services>
            """;

        var model = buildModelFromXml(xml);
        var config = getTritonConfig(model);

        assertEquals("triton:8001", config.grpcEndpoint()); // Default
        assertEquals("/sidecars/triton/models", config.modelRepository()); // Default
        assertEquals(TritonConfig.ModelControlMode.EXPLICIT, config.modelControlMode()); // Default
    }

    @Test
    void testTritonWithCustomConfiguration() throws Exception {
        String xml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <services version="1.0">
                <container id="container" version="1.0">
                    <triton>
                        <grpcEndpoint>localhost:8001</grpcEndpoint>
                        <modelRepository>/models</modelRepository>
                        <modelControlMode>EXPLICIT</modelControlMode>
                    </triton>
                </container>
            </services>
            """;

        var model = buildModelFromXml(xml);
        var config = getTritonConfig(model);

        assertEquals("localhost:8001", config.grpcEndpoint());
        assertEquals("/models", config.modelRepository());
        assertEquals(TritonConfig.ModelControlMode.EXPLICIT, config.modelControlMode());
    }

    @Test
    void testTritonInvalidModelControlMode() {
        String xml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <services version="1.0">
                <container id="container" version="1.0">
                    <triton>
                        <modelControlMode>INVALID</modelControlMode>
                    </triton>
                </container>
            </services>
            """;
        
        assertThrows(IllegalArgumentException.class, () -> buildModelFromXml(xml));
    }

    private VespaModel buildModelFromXml(String servicesXml) throws Exception {
        String hosts = "<hosts><host name='localhost'><alias>node1</alias></host></hosts>";
        return new VespaModelCreatorWithMockPkg(hosts, servicesXml).create();
    }

    private TritonConfig getTritonConfig(VespaModel model) {
        Component<?, ?> component = model.getContainerClusters().get("container").getComponentsMap().values().stream()
                .filter(c -> c instanceof TritonOnnxRuntime)
                .findFirst()
                .orElse(null);
        assertNotNull(component, "Triton component should exist");
        assertInstanceOf(TritonOnnxRuntime.class, component, "Component should be TritonInferenceServer");

        TritonOnnxRuntime triton = (TritonOnnxRuntime) component;
        TritonConfig.Builder builder = new TritonConfig.Builder();
        triton.getConfig(builder);
        return builder.build();
    }
}
