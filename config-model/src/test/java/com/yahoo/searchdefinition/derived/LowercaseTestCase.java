// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

/**
 * @author bratseth
 */
public class LowercaseTestCase extends AbstractExportingTestCase {

    @Test
    public void testDeriving() throws IOException, ParseException {
        assertCorrectDeriving("lowercase");
    }

}
