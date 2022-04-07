// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchdefinition.derived;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.searchdefinition.ApplicationBuilder;

import org.junit.Test;
import static org.junit.Assert.assertThrows;

/**
 * Verifies that a struct in a document type is preferred over another document type
 * of the same name.
 *
 * @author bratseth
 */
public class NameCollisionTestCase extends AbstractExportingTestCase {

    @Test
    public void testNameCollision() throws Exception {
        assertCorrectDeriving("namecollision", "collisionstruct",
                              new TestProperties().setExperimentalSdParsing(false),
                              new TestableDeployLogger());
        var docman = DocumentTypeManager.fromFile("temp/namecollision/documentmanager.cfg");

        assertCorrectDeriving("namecollision", "collisionstruct",
                              new TestProperties().setExperimentalSdParsing(true),
                              new TestableDeployLogger());
        docman = DocumentTypeManager.fromFile("temp/namecollision/documentmanager.cfg");
    }

}
