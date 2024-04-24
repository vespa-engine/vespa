// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.significance.test;

import com.yahoo.component.ComponentId;
import com.yahoo.config.InnerNode;
import com.yahoo.config.ModelNode;
import com.yahoo.config.ModelReference;
import com.yahoo.search.significance.config.SignificanceConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.SignificanceModelRegistry;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author MariusArhaug
 */

public class SignificanceModelTestCase {
    private VespaModel createModel(String filename) {
        return new VespaModelCreatorWithFilePkg(filename).create();
    }

    @Test
    void testIndexGreaterThanNumNodes() {
        VespaModel vespaModel = createModel("src/test/cfg/significance");
        ApplicationContainerCluster containerCluster = vespaModel.getContainerClusters().get("container");
        assertEquals(1, containerCluster.getContainers().size());
    }

    @Test
    void testSignificance() {
        VespaModel vespaModel = createModel("src/test/cfg/significance");
        ApplicationContainerCluster containerCluster = vespaModel.getContainerClusters().get("container");
        var significanceConfig = assertSignificancePresent(containerCluster);
        assertEquals(3, significanceConfig.model().size());
        assertEquals("en", significanceConfig.model().get(0).language());
        assertEquals("no", significanceConfig.model().get(1).language());
        assertEquals("ru", significanceConfig.model().get(2).language());

        assertEquals("models/idf-norwegian-wiki.json.zst", modelReference(significanceConfig.model().get(1), "path").path().orElseThrow().value());
        assertEquals("https://some/uri/blob.json", modelReference(significanceConfig.model().get(2), "path").url().orElseThrow().value());


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
}

