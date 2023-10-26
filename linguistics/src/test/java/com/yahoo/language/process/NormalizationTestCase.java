// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.language.simple.SimpleLinguistics;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Mathias Mølster Lidal
 */
public class NormalizationTestCase {

    private final Normalizer normalizer = new SimpleLinguistics().getNormalizer();

    @Test
    public void testEmptyStringNormalization() {
        assertEquals("", normalizer.normalize(""));
    }

    @Test
    public void testDoubleWidthAscii() {
        assertNormalize("\uff41\uff42\uff43\uff44\uff45\uff46\uff47\uff48\uff49", "abcdefghi");
    }

    @Test
    public void testLigature() {
        assertNormalize("\uFB01nance", "finance");
    }

    private void assertNormalize(String input, String exp) {
        assertEquals(exp, normalizer.normalize(input));
    }

}
