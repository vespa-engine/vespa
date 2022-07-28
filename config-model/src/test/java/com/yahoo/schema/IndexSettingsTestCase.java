// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.Stemming;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void requireThatInterlavedFeaturesAreSetOnExtraField() throws ParseException {
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
