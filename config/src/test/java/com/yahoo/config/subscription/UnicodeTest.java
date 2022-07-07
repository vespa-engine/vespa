// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.foo.UnicodeConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests reading of a config containing unicode characters in UTF-8
 *
 * @author Vidar Larsen
 * @author Harald Musum
 */
public class UnicodeTest {

    /**
     * Reads a config from a file which is exactly like one returned from
     * the config server given only default values for this config.
     * The parsing code is the same whether the reading happens from file
     * or from a server connection, so this tests that this config can be
     * received correctly from the server
     */
    @Test
    @SuppressWarnings("deprecation")
    public void testUnicodeConfigReading() {
        ConfigGetter<UnicodeConfig> getter = new ConfigGetter<>(UnicodeConfig.class);
        UnicodeConfig config = getter.getConfig("file:src/test/resources/configs/unicode/unicode.cfg");

        assertEquals("Hei \u00E6\u00F8\u00E5 \uBC14\uB451 \u00C6\u00D8\u00C5 hallo", config.unicodestring1());
        assertEquals("abc \u00E6\u00F8\u00E5 \u56F2\u7881 \u00C6\u00D8\u00C5 ABC", config.unicodestring2());
    }
}
