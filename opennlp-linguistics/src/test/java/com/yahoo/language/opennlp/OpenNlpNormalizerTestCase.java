package com.yahoo.language.opennlp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class OpenNlpNormalizerTestCase {

    @Test
    public void testNormalizing() {
        var normalizer = new OpenNlpLinguisticsTester().normalizer();
        assertEquals("cafe", normalizer.normalize("cafe"));
        // TODO: Accent normalize
        // assertEquals("cafe", normalizer.normalize("café"));
        // assertEquals("cafe", normalizer.normalize("cafè"));
    }

}
