// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.parser.ParseException;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests that indexing statements are parsed correctly.
 *
 * @author frodelu
 */
public class IndexingParsingTestCase extends AbstractSchemaTestCase {

    @Test
    void requireThatIndexingExpressionsCanBeParsed() throws Exception {
        assertNotNull(ApplicationBuilder.buildFromFile("src/test/examples/indexing.sd"));
    }

    @Test
    void requireThatParseExceptionPositionIsCorrect() throws Exception {
        try {
            ApplicationBuilder.buildFromFile("src/test/examples/indexing_invalid_expression.sd");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(Exceptions.toMessageString(e).contains("at line 5, column 57."));
        }
    }

}
