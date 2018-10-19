// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;

/**
 * Check rules for resegmenting words in YQL+ when segmenter is deemed
 * incompatible. The class under testing is {@link YqlParser}.
 *
 * @author Steinar Knutsen
 */
public class ResegmentingTestCase {

    private YqlParser parser;

    @Before
    public void setUp() throws Exception {
        ParserEnvironment env = new ParserEnvironment();
        parser = new YqlParser(env);
    }

    @After
    public void tearDown() throws Exception {
        parser = null;
    }

    @Test
    public final void testWord() {
        assertEquals(
                "title:'a b'",
                parser.parse(
                        new Parsable()
                                .setQuery("select * from sources * where [{\"segmenter\": {\"version\": \"18.47.39\", \"backend\": \"nonexistant\"}}] (title contains \"a b\");"))
                        .toString());
    }

    @Test
    public final void testPhraseSegment() {
        assertEquals(
                "title:'c d'",
                parser.parse(
                        new Parsable()
                                .setQuery("select * from sources * where"
                                        + " [{\"segmenter\": {\"version\": \"18.47.39\", \"backend\": \"nonexistant\"}}]"
                                        + " (title contains ([{\"origin\": {\"offset\": 0, \"length\":3, \"original\": \"c d\"}}]"
                                        + " phrase(\"a\",  \"b\")));"))
                        .toString());
    }

    @Test
    public final void testPhraseInEquiv() {
        assertEquals(
                "EQUIV title:a title:'c d'",
                parser.parse(
                        new Parsable()
                                .setQuery("select * from sources * where"
                                        + " [{\"segmenter\": {\"version\": \"18.47.39\", \"backend\": \"nonexistant\"}}]"
                                        + " (title contains"
                                        + " equiv(\"a\","
                                        + " ([{\"origin\": {\"offset\": 0, \"length\":3, \"original\": \"c d\"}}]\"b\")"
                                        + ")"
                                        + ");"))
                        .toString());
    }

    @Test
    public final void testPhraseSegmentToAndSegment() {
        assertEquals(
                "SAND title:c title:d",
                parser.parse(
                        new Parsable()
                                .setQuery("select * from sources * where"
                                        + " [{\"segmenter\": {\"version\": \"18.47.39\", \"backend\": \"nonexistant\"}}]"
                                        + " (title contains ([{\"origin\": {\"offset\": 0, \"length\":3, \"original\": \"c d\"}, \"andSegmenting\": true}]"
                                        + " phrase(\"a\",  \"b\")));"))
                        .toString());
    }

    @Test
    public final void testPhraseSegmentInPhrase() {
        assertEquals(
                "title:\"a 'c d'\"",
                parser.parse(
                        new Parsable()
                                .setQuery("select * from sources * where [{\"segmenter\": {\"version\": \"18.47.39\", \"backend\": \"nonexistant\"}}]"
                                        + " (title contains phrase(\"a\","
                                        + " ([{\"origin\": {\"offset\": 0, \"length\":3, \"original\": \"c d\"}}]"
                                        + "  phrase(\"e\", \"f\"))));"))
                        .toString());
    }

    @Test
    public final void testWordNoImplicitTransforms() {
        assertEquals(
                "title:a b",
                parser.parse(
                        new Parsable()
                                .setQuery("select * from sources * where [{\"segmenter\": {\"version\": \"18.47.39\", \"backend\": \"nonexistant\"}}] (title contains ([{\"implicitTransforms\": false}]\"a b\"));"))
                        .toString());
    }

    @Test
    public final void testPhraseSegmentNoImplicitTransforms() {
        assertEquals(
                "title:'a b'",
                parser.parse(
                        new Parsable()
                                .setQuery("select * from sources * where"
                                        + " [{\"segmenter\": {\"version\": \"18.47.39\", \"backend\": \"nonexistant\"}}]"
                                        + " (title contains ([{\"origin\": {\"offset\": 0, \"length\":3, \"original\": \"c d\"}, \"implicitTransforms\": false}]"
                                        + " phrase(\"a\",  \"b\")));"))
                        .toString());
    }

    @Test
    public final void testPhraseSegmentToAndSegmentNoImplicitTransforms() {
        assertEquals(
                "SAND title:a title:b",
                parser.parse(
                        new Parsable()
                                .setQuery("select * from sources * where"
                                        + " [{\"segmenter\": {\"version\": \"18.47.39\", \"backend\": \"nonexistant\"}}]"
                                        + " (title contains ([{\"origin\": {\"offset\": 0, \"length\":3, \"original\": \"c d\"}, \"andSegmenting\": true, \"implicitTransforms\": false}]"
                                        + " phrase(\"a\",  \"b\")));"))
                        .toString());
    }

    @Test
    public final void testPhraseSegmentInPhraseNoImplicitTransforms() {
        assertEquals(
                "title:\"a 'e f'\"",
                parser.parse(
                        new Parsable()
                                .setQuery("select * from sources * where [{\"segmenter\": {\"version\": \"18.47.39\", \"backend\": \"nonexistant\"}}]"
                                        + " (title contains phrase(\"a\","
                                        + " ([{\"origin\": {\"offset\": 0, \"length\":3, \"original\": \"c d\"}, \"implicitTransforms\": false}]"
                                        + "  phrase(\"e\", \"f\"))));"))
                        .toString());
    }

}
