// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Control argument checking in BooleanParser.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class BooleanParserTestCase {

    @Test
    public final void testParseBoolean() {
        boolean gotException = false;
        try {
            BooleanParser.parseBoolean(null);
        } catch (final NullPointerException e) {
            gotException = true;
        }
        assertTrue(gotException);
        gotException = false;
        try {
            BooleanParser.parseBoolean("nalle");
        } catch (final IllegalArgumentException e) {
            gotException = true;
        }
        assertTrue(BooleanParser.parseBoolean("true"));
        assertFalse(BooleanParser.parseBoolean("false"));
    }

}
