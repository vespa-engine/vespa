// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.schema.derived;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.document.DocumentTypeManager;

import org.junit.jupiter.api.Test;

/**
 * Verifies that a struct in a document type is preferred over another document type
 * of the same name.
 *
 * @author bratseth
 */
public class NameCollisionTestCase extends AbstractExportingTestCase {

    @Test
    void testNameCollision() throws Exception {
        assertCorrectDeriving("namecollision", "collisionstruct",
                new TestProperties(),
                new TestableDeployLogger());
        DocumentTypeManager.fromFile("temp/namecollision/documentmanager.cfg");
    }

}
