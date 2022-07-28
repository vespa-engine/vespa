// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Tests deriving of various field types
 *
 * @author  bratseth
 */
public class PrefixExactAttributeTestCase extends AbstractExportingTestCase {

    @Test
    void testTypes() throws IOException, ParseException {
        assertCorrectDeriving("prefixexactattribute");
    }

}
