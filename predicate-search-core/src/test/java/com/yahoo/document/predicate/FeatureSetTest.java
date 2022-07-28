// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class FeatureSetTest {

    @Test
    void requireThatFeatureSetIsAValue() {
        assertTrue(PredicateValue.class.isAssignableFrom(FeatureSet.class));
    }

    @Test
    void requireThatAccessorsWork() {
        FeatureSet node = new FeatureSet("key", "valueA", "valueB");
        assertEquals("key", node.getKey());
        assertValues(Arrays.asList("valueA", "valueB"), node);
        node.addValue("valueC");
        assertValues(Arrays.asList("valueA", "valueB", "valueC"), node);
        node.addValues(Arrays.asList("valueD", "valueE"));
        assertValues(Arrays.asList("valueA", "valueB", "valueC", "valueD", "valueE"), node);
        node.setValues(Arrays.asList("valueF", "valueG"));
        assertValues(Arrays.asList("valueF", "valueG"), node);
    }

    @Test
    void requireThatValueSetIsMutable() {
        FeatureSet node = new FeatureSet("key");
        node.getValues().add("valueA");
        assertValues(Arrays.asList("valueA"), node);

        node = new FeatureSet("key", "valueA");
        node.getValues().add("valueB");
        assertValues(Arrays.asList("valueA", "valueB"), node);
    }

    @Test
    void requireThatConstructorsWork() {
        FeatureSet node = new FeatureSet("key", "valueA", "valueB");
        assertEquals("key", node.getKey());
        assertValues(Arrays.asList("valueA", "valueB"), node);

        node = new FeatureSet("key", Arrays.asList("valueA", "valueB"));
        assertEquals("key", node.getKey());
        assertValues(Arrays.asList("valueA", "valueB"), node);
    }

    @Test
    void requireThatCloneIsImplemented() throws CloneNotSupportedException {
        FeatureSet node1 = new FeatureSet("key", "valueA", "valueB");
        FeatureSet node2 = node1.clone();
        assertEquals(node1, node2);
        assertNotSame(node1, node2);
        assertNotSame(node1.getValues(), node2.getValues());
    }

    @Test
    void requireThatHashCodeIsImplemented() {
        assertEquals(new FeatureSet("key").hashCode(), new FeatureSet("key").hashCode());
    }

    @Test
    void requireThatEqualsIsImplemented() {
        FeatureSet lhs = new FeatureSet("keyA", "valueA", "valueB");
        assertEquals(lhs, lhs);
        assertNotEquals(lhs, new Object());

        FeatureSet rhs = new FeatureSet("keyB");
        assertNotEquals(lhs, rhs);
        rhs.setKey("keyA");
        assertNotEquals(lhs, rhs);
        rhs.addValue("valueA");
        assertNotEquals(lhs, rhs);
        rhs.addValue("valueB");
        assertEquals(lhs, rhs);
    }

    @Test
    void requireThatkeyIsMandatoryInConstructor() {
        try {
            new FeatureSet(null);
            fail();
        } catch (NullPointerException e) {
            assertEquals("key", e.getMessage());
        }
        try {
            new FeatureSet(null, Collections.<String>emptyList());
            fail();
        } catch (NullPointerException e) {
            assertEquals("key", e.getMessage());
        }
    }

    @Test
    void requireThatkeyIsMandatoryInSetter() {
        FeatureSet node = new FeatureSet("foo");
        try {
            node.setKey(null);
            fail();
        } catch (NullPointerException e) {
            assertEquals("key", e.getMessage());
        }
        assertEquals("foo", node.getKey());
    }

    @Test
    void requireThatValueIsMandatoryInSetter() {
        FeatureSet node = new FeatureSet("foo", "bar");
        try {
            node.addValue(null);
            fail();
        } catch (NullPointerException e) {
            assertEquals("value", e.getMessage());
        }
        assertValues(Arrays.asList("bar"), node);
    }

    @Test
    void requireThatKeyIsEscapedInToString() {
        assertEquals("foo in [val]",
                new FeatureSet("foo", "val").toString());
        assertEquals("'\\foo' in [val]",
                new FeatureSet("\foo", "val").toString());
        assertEquals("'\\x27foo\\x27' in [val]",
                new FeatureSet("'foo'", "val").toString());
    }

    @Test
    void requireThatValuesAreEscapedInToString() {
        assertEquals("key in [bar, foo]",
                new FeatureSet("key", "foo", "bar").toString());
        assertEquals("key in ['\\foo', 'ba\\r']",
                new FeatureSet("key", "\foo", "ba\r").toString());
        assertEquals("key in ['\\x27bar\\x27', '\\x27foo\\x27']",
                new FeatureSet("key", "'foo'", "'bar'").toString());
    }

    @Test
    void requireThatSimpleStringsArePrettyPrinted() {
        assertEquals("foo in [bar]",
                new FeatureSet("foo", "bar").toString());
    }

    @Test
    void requireThatComplexStringsAreEscaped() {
        assertEquals("'\\foo' in ['ba\\r']",
                new FeatureSet("\foo", "ba\r").toString());
    }

    @Test
    void requireThatNegatedFeatureSetsArePrettyPrinted() {
        assertEquals("country not in [no, se]",
                new Negation(new FeatureSet("country", "no", "se")).toString());
    }

    private static void assertValues(Collection<String> expected, FeatureSet actual) {
        List<String> tmp = new ArrayList<>(expected);
        for (String value : actual.getValues()) {
            assertNotNull(tmp.remove(value), value);
        }
        assertTrue(tmp.isEmpty(), tmp.toString());
    }

}
