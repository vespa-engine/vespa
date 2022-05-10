// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

/**
 * Tests sort settings
 *
 * @author baldersheim
 */
public class SortingTestCase extends AbstractExportingTestCase {

    @Test
    public void testDocumentDerivingNewParser() throws IOException, ParseException {
        assertCorrectDeriving("sorting", new TestProperties());
    }

}
