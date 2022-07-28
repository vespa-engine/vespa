// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.schema.document.SDField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * tests importing of document containing array type fields and weighted set type fields, new syntax.
 *
 * @author Einar M R Rosenvinge
 */
public class ArraysWeightedSetsTestCase extends AbstractSchemaTestCase {
    @Test
    void testArrayWeightedSetsImporting() throws java.io.IOException, com.yahoo.schema.parser.ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/arraysweightedsets.sd");

        SDField tags = (SDField) schema.getDocument().getField("tags");
        assertTrue(tags.getDataType() instanceof ArrayDataType);
        assertEquals(DataType.STRING, ((CollectionDataType) tags.getDataType()).getNestedType());

        SDField ratings = (SDField) schema.getDocument().getField("ratings");
        assertTrue(ratings.getDataType() instanceof ArrayDataType);
        assertEquals(DataType.INT, ((CollectionDataType) ratings.getDataType()).getNestedType());

        SDField flags = (SDField) schema.getDocument().getField("flags");
        assertTrue(flags.getDataType() instanceof WeightedSetDataType);
        assertEquals(DataType.STRING, ((CollectionDataType) flags.getDataType()).getNestedType());

        SDField banners = (SDField) schema.getDocument().getField("banners");
        assertTrue(banners.getDataType() instanceof WeightedSetDataType);
        assertEquals(DataType.INT, ((CollectionDataType) banners.getDataType()).getNestedType());
    }

}
