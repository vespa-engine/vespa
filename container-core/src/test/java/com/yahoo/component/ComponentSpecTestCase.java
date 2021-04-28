// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Arne Bergene Fossaa
 */
public class ComponentSpecTestCase {

    @Test
    public void testMatches() {
        ComponentId a = new ComponentId("test:1");
        ComponentId b = new ComponentId("test:1.1.1");
        ComponentId c = new ComponentId("test:2");
        ComponentId d = new ComponentId("test:3");
        ComponentId e = new ComponentId("test");

        ComponentSpecification aspec = new ComponentSpecification("test");
        ComponentSpecification bspec = new ComponentSpecification("test:1");
        ComponentSpecification cspec = new ComponentSpecification("test:2");
        ComponentSpecification dspec = new ComponentSpecification("test1:2");

        assertTrue(aspec.matches(a));
        assertTrue(aspec.matches(b));
        assertTrue(aspec.matches(c));
        assertTrue(aspec.matches(d));
        assertTrue(aspec.matches(e));

        assertTrue(bspec.matches(a));
        assertTrue(bspec.matches(b));
        assertFalse(bspec.matches(c));
        assertFalse(bspec.matches(d));
        assertFalse(bspec.matches(e));

        assertFalse(cspec.matches(a));
        assertFalse(cspec.matches(b));
        assertTrue(cspec.matches(c));
        assertFalse(cspec.matches(d));
        assertFalse(cspec.matches(e));

        assertFalse(dspec.matches(a));
        assertFalse(dspec.matches(b));
        assertFalse(dspec.matches(c));
        assertFalse(dspec.matches(d));
        assertFalse(dspec.matches(e));

    }

    @Test
    public void testMatchesWithNamespace() {
        ComponentId namespace = new ComponentId("namespace:2");

        ComponentId a = new ComponentId("test", new Version(1), namespace);
        ComponentId b = new ComponentId("test:1@namespace:2");
        ComponentId c = new ComponentId("test:1@namespace");
        assertEquals(a, b);
        assertFalse(a.equals(c));

        ComponentSpecification spec = new ComponentSpecification("test", null, namespace);
        assertTrue(spec.matches(a));
        assertTrue(spec.matches(b));
        assertFalse(spec.matches(c));
    }

    @Test
    public void testStringValue() {
        assertStringValueEqualsInputSpec("a:1.0.0.alpha@namespace");
        assertStringValueEqualsInputSpec("a:1.0.0.alpha");
        assertStringValueEqualsInputSpec("a:1.0");
        assertStringValueEqualsInputSpec("a");
    }

    private void assertStringValueEqualsInputSpec(String componentSpec) {
        assertEquals(componentSpec,
                new ComponentSpecification(componentSpec).stringValue());
    }

}
