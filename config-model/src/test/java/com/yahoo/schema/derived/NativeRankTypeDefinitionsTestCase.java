// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.document.RankType;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testing stuff related to native rank type definitions
 *
 * @author geirst
 */
public class NativeRankTypeDefinitionsTestCase extends AbstractSchemaTestCase {
    @Test
    void testTables() {
        assertEquals(NativeTable.Type.FIRST_OCCURRENCE.getName(), "firstOccurrenceTable");
        assertEquals(NativeTable.Type.OCCURRENCE_COUNT.getName(), "occurrenceCountTable");
        assertEquals(NativeTable.Type.PROXIMITY.getName(), "proximityTable");
        assertEquals(NativeTable.Type.REVERSE_PROXIMITY.getName(), "reverseProximityTable");
        assertEquals(NativeTable.Type.WEIGHT.getName(), "weightTable");
    }

    @Test
    void testDefinitions() {
        NativeRankTypeDefinitionSet defs = new NativeRankTypeDefinitionSet("default");

        NativeRankTypeDefinition rank;
        Iterator<NativeTable> tables;

        assertEquals(4, defs.types().size());

        {
            rank = defs.getRankTypeDefinition(RankType.EMPTY);
            assertNotNull(rank);
            assertEquals(RankType.EMPTY, rank.getType());
            tables = rank.rankSettingIterator();
            assertEquals(new NativeTable(NativeTable.Type.FIRST_OCCURRENCE, "linear(0,0)"), tables.next());
            assertEquals(new NativeTable(NativeTable.Type.OCCURRENCE_COUNT, "linear(0,0)"), tables.next());
            assertEquals(new NativeTable(NativeTable.Type.PROXIMITY, "linear(0,0)"), tables.next());
            assertEquals(new NativeTable(NativeTable.Type.REVERSE_PROXIMITY, "linear(0,0)"), tables.next());
            assertEquals(new NativeTable(NativeTable.Type.WEIGHT, "linear(0,0)"), tables.next());
            assertFalse(tables.hasNext());
        }

        {
            rank = defs.getRankTypeDefinition(RankType.ABOUT);
            assertNotNull(rank);
            assertEquals(RankType.ABOUT, rank.getType());
            tables = rank.rankSettingIterator();
            assertEquals(new NativeTable(NativeTable.Type.FIRST_OCCURRENCE, "expdecay(8000,12.50)"), tables.next());
            assertEquals(new NativeTable(NativeTable.Type.OCCURRENCE_COUNT, "loggrowth(1500,4000,19)"), tables.next());
            assertEquals(new NativeTable(NativeTable.Type.PROXIMITY, "expdecay(500,3)"), tables.next());
            assertEquals(new NativeTable(NativeTable.Type.REVERSE_PROXIMITY, "expdecay(400,3)"), tables.next());
            assertEquals(new NativeTable(NativeTable.Type.WEIGHT, "linear(1,0)"), tables.next());
            assertFalse(tables.hasNext());
        }

        {
            rank = defs.getRankTypeDefinition(RankType.IDENTITY);
            assertNotNull(rank);
            assertEquals(RankType.IDENTITY, rank.getType());
            tables = rank.rankSettingIterator();
            assertEquals(new NativeTable(NativeTable.Type.FIRST_OCCURRENCE, "expdecay(100,12.50)"), tables.next());
            assertEquals(new NativeTable(NativeTable.Type.OCCURRENCE_COUNT, "loggrowth(1500,4000,19)"), tables.next());
            assertEquals(new NativeTable(NativeTable.Type.PROXIMITY, "expdecay(5000,3)"), tables.next());
            assertEquals(new NativeTable(NativeTable.Type.REVERSE_PROXIMITY, "expdecay(3000,3)"), tables.next());
            assertEquals(new NativeTable(NativeTable.Type.WEIGHT, "linear(1,0)"), tables.next());
            assertFalse(tables.hasNext());
        }

        {
            rank = defs.getRankTypeDefinition(RankType.TAGS);
            assertNotNull(rank);
            assertEquals(RankType.TAGS, rank.getType());
            tables = rank.rankSettingIterator();
            assertEquals(new NativeTable(NativeTable.Type.FIRST_OCCURRENCE, "expdecay(8000,12.50)"), tables.next());
            assertEquals(new NativeTable(NativeTable.Type.OCCURRENCE_COUNT, "loggrowth(1500,4000,19)"), tables.next());
            assertEquals(new NativeTable(NativeTable.Type.PROXIMITY, "expdecay(500,3)"), tables.next());
            assertEquals(new NativeTable(NativeTable.Type.REVERSE_PROXIMITY, "expdecay(400,3)"), tables.next());
            assertEquals(new NativeTable(NativeTable.Type.WEIGHT, "loggrowth(38,50,1)"), tables.next());
            assertFalse(tables.hasNext());
        }

        {
            assertEquals(RankType.ABOUT, defs.getRankTypeDefinition(RankType.DEFAULT).getType());
        }
    }

}
