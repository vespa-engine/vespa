// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.significance.test;

import com.yahoo.component.ComponentId;
import com.yahoo.config.InnerNode;
import com.yahoo.config.ModelNode;
import com.yahoo.config.ModelReference;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;

import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.path.Path;
import com.yahoo.search.significance.config.SignificanceConfig;

import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.SignificanceModelRegistry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author MariusArhaug, glebashnik
 */

public class SignificanceModelTestCase {
    @Test
    void testSignificanceModelConfig_selfhosted() throws Exception {
        var vespaModel = loadModel(Path.fromString("src/test/cfg/significance/selfhosted/"), false);

        var cluster = vespaModel.getContainerClusters().get("container");
        assertEquals(1, cluster.getContainers().size());

        var significanceConfig = assertSignificancePresent(cluster);
        assertEquals(3, significanceConfig.model().size());

        assertModel(significanceConfig.model(0),
                Optional.of("significance-en-wikipedia-v1"),
                Optional.of("models/significance-en-wikipedia-v1.json.zst"),
                Optional.empty()
        );

        assertModel(significanceConfig.model(1),
                Optional.empty(),
                Optional.of("models/idf-norwegian-wiki.json.zst"),
                Optional.empty()
        );

        assertModel(significanceConfig.model(2),
                Optional.empty(),
                Optional.empty(),
                Optional.of("https://some/uri/blob.json")
        );
    }

    @Test
    void testSignificanceModelConfig_hosted() throws Exception {
        var vespaModel = loadModel(Path.fromString("src/test/cfg/significance/hosted/"), true);

        var cluster = vespaModel.getContainerClusters().get("container");
        assertEquals(1, cluster.getContainers().size());

        var significanceConfig = assertSignificancePresent(cluster);
        assertEquals(3, significanceConfig.model().size());


        assertModel(significanceConfig.model(0),
                Optional.of("significance-en-wikipedia-v1"),
                Optional.empty(),
                Optional.of("https://data.vespa-cloud.com/significance_models/significance-en-wikipedia-v1.json.zst")
        );

        assertModel(significanceConfig.model(1),
                Optional.empty(),
                Optional.of("models/idf-norwegian-wiki.json.zst"),
                Optional.empty()
        );

        assertModel(significanceConfig.model(2),
                Optional.empty(),
                Optional.empty(),
                Optional.of("https://some/uri/blob.json")
        );
    }

    private VespaModel loadModel(Path path, boolean hosted) throws Exception {
        FilesApplicationPackage applicationPackage = FilesApplicationPackage.fromFile(path.toFile());
        TestProperties properties = new TestProperties().setHostedVespa(hosted);
        DeployState state = new DeployState.Builder()
                .properties(properties)
                .endpoints(hosted ? Set.of(new ContainerEndpoint("container", ApplicationClusterEndpoint.Scope.zone, List.of("default.example.com"))) : Set.of())
                .applicationPackage(applicationPackage)
                .build();
        return new VespaModel(state);
    }

    private SignificanceConfig assertSignificancePresent(ApplicationContainerCluster cluster) {
        var id = new ComponentId("com.yahoo.language.significance.impl.DefaultSignificanceModelRegistry");
        var significance = (SignificanceModelRegistry) cluster.getComponentsMap().get(id);
        assertEquals("com.yahoo.language.significance.impl.DefaultSignificanceModelRegistry", significance.getClassId().getName());
        var cfgBuilder = new SignificanceConfig.Builder();
        significance.getConfig(cfgBuilder);
        return cfgBuilder.build();
    }

    // Ugly hack to read underlying model reference from config instance
    private static ModelReference modelReference(InnerNode cfg, String name) {
        try {
            var f = cfg.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return ((ModelNode) f.get(cfg)).getModelReference();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    void assertModel(SignificanceConfig.Model model, Optional<String> id, Optional<String> path, Optional<String> url) {
        var modelReference = modelReference(model, "path");
        var modelId = modelReference.modelId();
        var modelPath = modelReference.path();
        var modelUrl = modelReference.url();

        id.ifPresentOrElse(
                s -> assertEquals(s, modelId.orElseThrow()),
                () -> assertEquals(Optional.empty(), modelId)
        );

        path.ifPresentOrElse(
                s -> assertEquals(s, modelPath.orElseThrow().value()),
                () -> assertEquals(Optional.empty(), modelPath)
        );

        url.ifPresentOrElse(
                s -> assertEquals(s, modelUrl.orElseThrow().value()),
                () -> assertEquals(Optional.empty(), modelUrl)
        );
    }
}

