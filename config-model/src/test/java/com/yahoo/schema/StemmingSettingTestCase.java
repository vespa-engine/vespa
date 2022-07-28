// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.Stemming;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Stemming settings test
 *
 * @author bratseth
 */
public class StemmingSettingTestCase extends AbstractSchemaTestCase {

    @Test
    void testStemmingSettings() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/stemmingsetting.sd");

        SDField artist = (SDField) schema.getDocument().getField("artist");
        assertEquals(Stemming.SHORTEST, artist.getStemming(schema));

        SDField title = (SDField) schema.getDocument().getField("title");
        assertEquals(Stemming.NONE, title.getStemming(schema));

        SDField song = (SDField) schema.getDocument().getField("song");
        assertEquals(Stemming.MULTIPLE, song.getStemming(schema));

        SDField track = (SDField) schema.getDocument().getField("track");
        assertEquals(Stemming.SHORTEST, track.getStemming(schema));

        SDField backward = (SDField) schema.getDocument().getField("backward");
        assertEquals(Stemming.SHORTEST, backward.getStemming(schema));

        Index defaultIndex = schema.getIndex("default");
        assertEquals(Stemming.SHORTEST, defaultIndex.getStemming());
    }

    @Test
    void requireThatStemmingIsDefaultBest() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/stemmingdefault.sd");
        assertNull(schema.getConcreteField("my_str").getStemming());
        assertEquals(Stemming.BEST, schema.getConcreteField("my_str").getStemming(schema));
    }

}
