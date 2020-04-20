// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.Stemming;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

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

}
