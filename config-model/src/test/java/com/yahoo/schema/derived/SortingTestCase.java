// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Tests sort settings
 *
 * @author baldersheim
 */
public class SortingTestCase extends AbstractExportingTestCase {

    @Test
    void testDocumentDerivingNewParser() throws IOException, ParseException {
        assertCorrectDeriving("sorting", new TestProperties());
    }

}
