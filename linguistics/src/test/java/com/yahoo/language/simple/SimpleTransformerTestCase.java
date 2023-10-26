// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.simple;

import com.yahoo.language.Language;
import com.yahoo.language.process.Transformer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class SimpleTransformerTestCase {

    private final static Transformer TRANSFORMER = new SimpleTransformer();

    @Test
    public void requireThatNonAccentsRemain() {
        assertTransform("foo", "foo");
    }

    @Test
    public void requireThatTransformerRemovesAccents() {
        assertTransform("\u212B", "A");
        assertTransform("\u2126", "\u03A9");
        assertTransform("\u00C5", "A");
        assertTransform("\u00F4", "o");
        assertTransform("\u1E69", "s");
        assertTransform("\u1E0B\u0323", "d");
        assertTransform("\u0071\u0307\u0323", "q");
        assertTransform("\uFB01", "\uFB01");
        assertTransform("2\u2075", "2\u2075");
        assertTransform("\u1E9B\u0323", "\u017F");
    }

    private static void assertTransform(String input, String expectedTransform) {
        assertEquals(expectedTransform, TRANSFORMER.accentDrop(input, Language.ENGLISH));
    }

}
