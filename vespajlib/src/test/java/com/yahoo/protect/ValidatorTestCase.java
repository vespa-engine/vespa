// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.protect;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class ValidatorTestCase {

    @Test
    public void testEnsureNotNull() {
        try {
            Validator.ensureNotNull("Description",null);
            fail("No exception");
        }
        catch (Exception e) {
            // success
        }
    }

    @Test
    public void testEnsureNotInitialized() {
        try {
            Validator.ensureNotInitialized("Description","Field-owner","Initialized-field-value");
            fail("No exception");
        }
        catch (Exception e) {
            // success
        }
    }

    @Test
    public void testEnsureInRange() {
        try {
            Validator.ensureInRange("Description",2,4,5);
            fail("No exception");
        }
        catch (Exception e) {
            // success
        }
    }

    @Test
    public void testSmallerInts() {
        try {
            Validator.ensureSmaller("Small-description",3,"Large-description",2);
            fail("No exception");
        }
        catch (Exception e) {
            // success
        }
    }

    @Test
    public void testSmallerComparables() {
        try {
            Validator.ensureSmaller("Small-description","b","Large-description","a");
            fail("No exception");
        }
        catch (Exception e) {
            // success
        }
    }

    @Test
    public void testEnsure() {
        try {
            Validator.ensure("Description",false);
            fail("No exception");
        }
        catch (Exception e) {
            // success
        }
    }

    @Test
    public void testEnsureInstanceOf() {
        try {
            Validator.ensureInstanceOf("Description","item",Integer.class);
            fail("No exception");
        }
        catch (Exception e) {
            // success
        }
    }

    @Test
    public void testVarArgsEnsure() {
        Validator.ensure(true, "ignored");
        try {
            Validator.ensure(false, "a", "b", "c");
        } catch (Exception e) {
            assertEquals("abc", e.getMessage());
        }
    }

}
