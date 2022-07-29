// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author bratseth
 */
public class ReservedWordsAsFieldNamesTestCase extends AbstractSchemaTestCase {

    @Test
    void testIt() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/reserved_words_as_field_names.sd");
        assertNotNull(schema.getDocument().getField("inline"));
        assertNotNull(schema.getDocument().getField("constants"));
        assertNotNull(schema.getDocument().getField("reference"));
    }

}
