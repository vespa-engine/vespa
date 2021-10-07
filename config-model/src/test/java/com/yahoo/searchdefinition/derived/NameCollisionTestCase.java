// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchdefinition.derived;

import org.junit.Test;

/**
 * Verifies that a struct in a document type is preferred over another dopcument type
 * of the same name.
 *
 * @author bratseth
 */
public class NameCollisionTestCase extends AbstractExportingTestCase {

    @Test
    public void testNameCollision() throws Exception {
        assertCorrectDeriving("namecollision", "collisionstruct", new TestableDeployLogger());
    }

}
