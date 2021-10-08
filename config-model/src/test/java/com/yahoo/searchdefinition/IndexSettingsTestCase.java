// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.Stemming;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Rank settings
 *
 * @author bratseth
 */
public class IndexSettingsTestCase extends SchemaTestCase {

    @Test
    public void testStemmingSettings() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/indexsettings.sd");

        SDField usingDefault=(SDField) search.getDocument().getField("usingdefault");
        assertEquals(Stemming.SHORTEST,usingDefault.getStemming(search));

        SDField notStemmed=(SDField) search.getDocument().getField("notstemmed");
        assertEquals(Stemming.NONE,notStemmed.getStemming(search));

        SDField allStemmed=(SDField) search.getDocument().getField("allstemmed");
        assertEquals(Stemming.SHORTEST,allStemmed.getStemming(search));

        SDField multiStemmed=(SDField) search.getDocument().getField("multiplestems");
        assertEquals(Stemming.MULTIPLE, multiStemmed.getStemming(search));
    }

    @Test
    public void requireThatInterlavedFeaturesAreSetOnExtraField() throws ParseException {
        SearchBuilder builder = SearchBuilder.createFromString(joinLines(
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
        Search search = builder.getSearch();
        Index contentIndex = search.getIndex("content");
        assertTrue(contentIndex.useInterleavedFeatures());
        Index extraIndex = search.getIndex("extra");
        assertTrue(extraIndex.useInterleavedFeatures());
    }

}
