// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.document.SDField;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests comment handling
 *
 * @author bratseth
 */
public class CommentTestCase extends AbstractSchemaTestCase {

    @Test
    void testComments() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/comment.sd");
        SDField field = schema.getConcreteField("a");
        assertEquals("{ input a | tokenize normalize stem:\"BEST\" | summary a | index a; }",
                field.getIndexingScript().toString());
    }

}
