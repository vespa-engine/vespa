// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.datatypes.DoubleFieldValue;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class AnnotationTypesTestCase {

    @Test
    public void requireThatProximityBreakAcceptsDoubleWeight() {
        try {
            new Annotation(AnnotationTypes.PROXIMITY_BREAK, new DoubleFieldValue(6.9));
        } catch (Exception e) {
            fail("this is required for ticket #665166, do not change");
        }
    }
}
