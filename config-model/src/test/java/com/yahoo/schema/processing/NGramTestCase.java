// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.document.MatchType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.Stemming;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bratseth
 */
public class NGramTestCase extends AbstractSchemaTestCase {

    @Test
    void testNGram() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/ngram.sd");
        assertNotNull(schema);

        SDField gram1 = schema.getConcreteField("gram_1");
        assertEquals(MatchType.GRAM, gram1.getMatching().getType());
        assertEquals(1, gram1.getMatching().getGramSize());

        SDField gram2 = schema.getConcreteField("gram_2");
        assertEquals(MatchType.GRAM, gram2.getMatching().getType());
        assertEquals(-1, gram2.getMatching().getGramSize()); // Not set explicitly

        SDField gram3 = schema.getConcreteField("gram_3");
        assertEquals(MatchType.GRAM, gram3.getMatching().getType());
        assertEquals(3, gram3.getMatching().getGramSize());

        assertEquals("input gram_1 | ngram 1 | index gram_1 | summary gram_1", gram1.getIndexingScript().iterator().next().toString());
        assertEquals("input gram_2 | ngram 2 | attribute gram_2 | index gram_2", gram2.getIndexingScript().iterator().next().toString());
        assertEquals("input gram_3 | ngram 3 | index gram_3", gram3.getIndexingScript().iterator().next().toString());

        assertFalse(gram1.getNormalizing().doRemoveAccents());
        assertEquals(Stemming.NONE, gram1.getStemming());

        List<String> queryCommands = gram1.getQueryCommands();
        assertEquals(2, queryCommands.size());
        assertEquals("ngram 1", queryCommands.get(1));
    }

    @Test
    void testInvalidNGramSetting1() throws IOException, ParseException {
        try {
            ApplicationBuilder.buildFromFile("src/test/examples/invalidngram1.sd");
            fail("Should cause an exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("gram-size can only be set when the matching mode is 'gram'", e.getMessage());
        }
    }

    @Test
    void testInvalidNGramSetting2() throws IOException, ParseException {
        try {
            ApplicationBuilder.buildFromFile("src/test/examples/invalidngram2.sd");
            fail("Should cause an exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("gram-size can only be set when the matching mode is 'gram'", e.getMessage());
        }
    }

    @Test
    void testInvalidNGramSetting3() throws IOException, ParseException {
        try {
            ApplicationBuilder.buildFromFile("src/test/examples/invalidngram3.sd");
            fail("Should cause an exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("gram matching is not supported with attributes, use 'index' in indexing", e.getMessage());
        }
    }

}
