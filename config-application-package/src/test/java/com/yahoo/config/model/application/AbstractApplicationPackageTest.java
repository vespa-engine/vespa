// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author arnej
 */
public class AbstractApplicationPackageTest {

    private static void checkValid(String fn) {
        assertTrue(AbstractApplicationPackage.validSchemaFilename(fn));
    }

    private static void checkInvalid(String fn) {
        assertFalse(AbstractApplicationPackage.validSchemaFilename(fn));
    }

    @Test
    public void testValidSchemaFilename() {
        checkValid("foo.sd");
        checkValid("schemas/foo.sd");
        checkValid("./foo.sd");
        checkValid("./schemas/foo.sd");
        checkInvalid("foo");
        checkInvalid("foo.ds");
        checkInvalid(".foo.sd");
        checkInvalid("schemas/.foo.sd");
        checkInvalid("schemas/subdir/._foo.sd");
    }
}
