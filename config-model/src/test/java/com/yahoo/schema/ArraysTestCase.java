// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * tests importing of document containing array type fields
 *
 * @author bratseth
 */
public class ArraysTestCase extends AbstractSchemaTestCase {

    @Test
    void testArrayImporting() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/arrays.sd");

        SDField tags = (SDField) schema.getDocument().getField("tags");
        assertEquals(DataType.STRING, ((CollectionDataType) tags.getDataType()).getNestedType());

        SDField ratings = (SDField) schema.getDocument().getField("ratings");
        assertTrue(ratings.getDataType() instanceof ArrayDataType);
        assertEquals(DataType.INT, ((ArrayDataType) ratings.getDataType()).getNestedType());
    }

}
