// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.significance.test;

import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
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
}

