package com.yahoo.vespa.model.significance.test;

import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

