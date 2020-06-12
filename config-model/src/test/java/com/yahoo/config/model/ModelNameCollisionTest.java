// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.vespa.model.VespaModel;
import org.junit.After;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ModelNameCollisionTest {

    private static final Path appDir = Path.fromString("src/test/cfg/application/ml_serving_name_collision");

    @After
    public void removeGeneratedModelFiles() {
        IOUtils.recursiveDeleteDir(appDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
    }

    @Test
    public void testMl_ServingApplication() throws SAXException, IOException {
        ApplicationPackageTester tester = ApplicationPackageTester.create(appDir.toString());
        try {
            new VespaModel(tester.app());
        }
        catch (IllegalArgumentException e) {
            assertEquals("The models in " +
                         appDir + "/models/parent/mnist_softmax.onnx and " +
                         appDir + "/models/parent/mnist_softmax" +
                         " both resolve to the model name 'parent_mnist_softmax'",
                         e.getMessage());
        }
    }

}
