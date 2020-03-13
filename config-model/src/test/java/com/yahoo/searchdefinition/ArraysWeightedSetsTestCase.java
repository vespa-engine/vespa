// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.searchdefinition.document.SDField;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * tests importing of document containing array type fields and weighted set type fields, new syntax.
 *
 * @author Einar M R Rosenvinge
 */
public class ArraysWeightedSetsTestCase extends SearchDefinitionTestCase {
    @Test
    public void testArrayWeightedSetsImporting() throws java.io.IOException, com.yahoo.searchdefinition.parser.ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/arraysweightedsets.sd");

        SDField tags = (SDField) search.getDocument().getField("tags");
        assertTrue(tags.getDataType() instanceof ArrayDataType);
        assertEquals(DataType.STRING, ((CollectionDataType)tags.getDataType()).getNestedType());

        SDField ratings = (SDField) search.getDocument().getField("ratings");
        assertTrue(ratings.getDataType() instanceof ArrayDataType);
        assertEquals(DataType.INT, ((CollectionDataType)ratings.getDataType()).getNestedType());

        SDField flags = (SDField) search.getDocument().getField("flags");
        assertTrue(flags.getDataType() instanceof WeightedSetDataType);
        assertEquals(DataType.STRING, ((CollectionDataType)flags.getDataType()).getNestedType());

        SDField banners = (SDField) search.getDocument().getField("banners");
        assertTrue(banners.getDataType() instanceof WeightedSetDataType);
        assertEquals(DataType.INT, ((CollectionDataType)banners.getDataType()).getNestedType());
    }

}
