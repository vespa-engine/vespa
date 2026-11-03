// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.Stemming;
import com.yahoo.schema.document.TokensMode;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rank settings
 *
 * @author bratseth
 */
public class IndexSettingsTestCase extends AbstractSchemaTestCase {

    @Test
    void testStemmingSettings() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/indexsettings.sd");

        SDField usingDefault = (SDField) schema.getDocument().getField("usingdefault");
        assertEquals(Stemming.SHORTEST, usingDefault.getStemming(schema));

        SDField notStemmed = (SDField) schema.getDocument().getField("notstemmed");
        assertEquals(Stemming.NONE, notStemmed.getStemming(schema));

        SDField allStemmed = (SDField) schema.getDocument().getField("allstemmed");
        assertEquals(Stemming.SHORTEST, allStemmed.getStemming(schema));

        SDField multiStemmed = (SDField) schema.getDocument().getField("multiplestems");
        assertEquals(Stemming.MULTIPLE, multiStemmed.getStemming(schema));
    }

    @Test
    void testLinguisticsSettings() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/indexsettings.sd");

        SDField l1 = (SDField) schema.getDocument().getField("l1");
        assertEquals("p1", l1.getIndexLinguisticsProfile());
        assertEquals("p1", l1.getSearchLinguisticsProfile());
        assertNull(l1.getIndexLinguisticsTokens());
        assertNull(l1.getSearchLinguisticsTokens());

        SDField l2 = (SDField) schema.getDocument().getField("l2");
        assertEquals("p2", l2.getIndexLinguisticsProfile());
        assertEquals("p1", l2.getSearchLinguisticsProfile());

        SDField l3 = (SDField) schema.getDocument().getField("l3");
        assertEquals("p1", l3.getIndexLinguisticsProfile());
        assertEquals("p1", l3.getSearchLinguisticsProfile());
        assertEquals(TokensMode.ALTERNATIVES, l3.getIndexLinguisticsTokens());
        assertEquals(TokensMode.ALTERNATIVES, l3.getSearchLinguisticsTokens());

        SDField l4 = (SDField) schema.getDocument().getField("l4");
        assertEquals("p2", l4.getIndexLinguisticsProfile());
        assertEquals("p1", l4.getSearchLinguisticsProfile());
        assertEquals(TokensMode.ORIGINAL, l4.getIndexLinguisticsTokens());
        assertEquals(TokensMode.ORIGINAL_AND_ALTERNATIVES, l4.getSearchLinguisticsTokens());
    }

    @Test
    void requireThatInterleavedFeaturesAreSetOnExtraField() throws ParseException {
        ApplicationBuilder builder = ApplicationBuilder.createFromString(joinLines(
                "search test {",
                "  document test {",
                "    field content type string {",
                "      indexing: index | summary",
                "      index: enable-bm25",
                "    }",
                "  }",
                "  field extra type string {",
                "    indexing: input content | index | summary",
                "    index: enable-bm25",
                "  }",
                "}"
        ));
        Schema schema = builder.getSchema();
        Index contentIndex = schema.getIndex("content");
        assertTrue(contentIndex.useInterleavedFeatures());
        Index extraIndex = schema.getIndex("extra");
        assertTrue(extraIndex.useInterleavedFeatures());
    }

}
