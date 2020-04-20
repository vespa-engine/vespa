// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.Stemming;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Stemming settings test
 *
 * @author bratseth
 */
public class StemmingSettingTestCase extends SchemaTestCase {

    @Test
    public void testStemmingSettings() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/stemmingsetting.sd");

        SDField artist = (SDField)search.getDocument().getField("artist");
        assertEquals(Stemming.SHORTEST, artist.getStemming(search));

        SDField title = (SDField)search.getDocument().getField("title");
        assertEquals(Stemming.NONE, title.getStemming(search));

        SDField song = (SDField)search.getDocument().getField("song");
        assertEquals(Stemming.MULTIPLE, song.getStemming(search));

        SDField track = (SDField)search.getDocument().getField("track");
        assertEquals(Stemming.SHORTEST, track.getStemming(search));

        SDField backward = (SDField)search.getDocument().getField("backward");
        assertEquals(Stemming.SHORTEST, backward.getStemming(search));

        Index defaultIndex = search.getIndex("default");
        assertEquals(Stemming.SHORTEST, defaultIndex.getStemming());
    }

    @Test
    public void requireThatStemmingIsDefaultBest() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/stemmingdefault.sd");
        assertNull(search.getConcreteField("my_str").getStemming());
        assertEquals(Stemming.BEST, search.getConcreteField("my_str").getStemming(search));
    }

}
