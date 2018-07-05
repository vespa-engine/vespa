// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.simple;

import com.yahoo.language.process.Normalizer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class SimpleNormalizerTestCase {

    private static final Normalizer NORMALIZER = new SimpleNormalizer();

    @Test
    public void requireThatInputIsNfkcNormalized() {
        assertNormalize("\u212B", "\u00C5");
        assertNormalize("\u2126", "\u03A9");
        assertNormalize("\u00C5", "\u00C5");
        assertNormalize("\u00F4", "\u00F4");
        assertNormalize("\u1E69", "\u1E69");
        assertNormalize("\u1E0B\u0323", "\u1E0D\u0307");
        assertNormalize("\u0071\u0307\u0323", "q\u0323\u0307");
        assertNormalize("\uFB01", "fi");
        assertNormalize("\u0032\u2075", "25");
        assertNormalize("\u1E9B\u0323", "\u1E69");
    }

    private static void assertNormalize(String input, String expectedNormalForm) {
        assertEquals(expectedNormalForm, NORMALIZER.normalize(input));
    }

}
