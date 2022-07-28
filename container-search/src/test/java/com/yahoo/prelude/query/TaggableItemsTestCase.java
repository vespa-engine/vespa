// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Keep CompositeTaggableItem, SimpleTaggableItem and TaggableSegmentItem in
 * lockstep.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class TaggableItemsTestCase {

    @BeforeEach
    public void setUp() throws Exception {
    }

    @AfterEach
    public void tearDown() throws Exception {
    }

    private static class ApiMethod {
        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ApiMethod other = (ApiMethod) obj;
            if (!name.equals(other.name)) {
                return false;
            }
            if (parameterTypes.length != other.parameterTypes.length) {
                return false;
            }
            for (int i = 0; i < parameterTypes.length; ++i) {
                if (parameterTypes[i] != other.parameterTypes[i]) {
                    return false;
                }
            }
            if (returnType != other.returnType) {
                return false;
            }
            return true;
        }

        public ApiMethod(final Method method) {
            if (method == null) {
                throw new IllegalArgumentException();
            }
            name = method.getName();
            returnType = method.getReturnType();
            parameterTypes = method.getParameterTypes();
        }

        @Override
        public String toString() {
            final StringBuilder s = new StringBuilder();
            s.append(returnType.getSimpleName()).append(' ').append(name)
                    .append('(');
            final int initLen = s.length();
            for (final Class<?> c : parameterTypes) {
                if (s.length() != initLen) {
                    s.append(", ");
                }
                s.append(c.getSimpleName());
            }
            s.append(')');
            return s.toString();
        }

        private final String name;
        private final Class<?> returnType;
        private final Class<?>[] parameterTypes;

    }

    @Test
    void requireSimilarAPIs() {
        final Method[] composite = CompositeTaggableItem.class
                .getDeclaredMethods();
        final Method[] simple = SimpleTaggableItem.class.getDeclaredMethods();
        final Method[] segment = TaggableSegmentItem.class.getDeclaredMethods();
        final int numberOfMethods = 10;
        assertEquals(numberOfMethods, composite.length);
        assertEquals(numberOfMethods, simple.length);
        assertEquals(numberOfMethods, segment.length);
        final Set<ApiMethod> compositeSet = methodSet(composite);
        final Set<ApiMethod> simpleSet = methodSet(simple);
        final Set<ApiMethod> segmentSet = methodSet(segment);
        assertEquals(compositeSet, simpleSet);
        assertEquals(simpleSet, segmentSet);

    }

    public Set<ApiMethod> methodSet(final Method[] methods) {
        final Set<ApiMethod> methodSet = new HashSet<>();
        for (final Method m : methods) {
            methodSet.add(new ApiMethod(m));
        }
        return methodSet;
    }

    @Test
    final void testSetUniqueID() {
        final PhraseSegmentItem p = new PhraseSegmentItem("farmyards", false,
                false);
        assertFalse(p.hasUniqueID());
        p.setUniqueID(10);
        assertEquals(10, p.getUniqueID());
        assertTrue(p.hasUniqueID());
    }

    @Test
    final void testSetConnectivity() {
        final PhraseSegmentItem p = new PhraseSegmentItem("farmyards", false,
                false);
        assertEquals(0.0d, p.getConnectivity(), 1e-9);
        final WordItem w = new WordItem("nalle");
        final double expectedConnectivity = 37e9;
        p.setConnectivity(w, expectedConnectivity);
        assertSame(w, p.getConnectedItem());
        assertEquals(expectedConnectivity, p.getConnectivity(), 1e0);
    }

    @Test
    final void testSetSignificance() {
        final PhraseSegmentItem p = new PhraseSegmentItem("farmyards", false,
                false);
        // unset
        assertEquals(0.0d, p.getSignificance(), 1e-9);
        assertFalse(p.hasExplicitSignificance());
        p.setSignificance(500.0d);
        assertTrue(p.hasExplicitSignificance());
    }

}
