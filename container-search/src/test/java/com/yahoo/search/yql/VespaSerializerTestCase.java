// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.search.Query;
import com.yahoo.search.grouping.Continuation;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.request.AllOperation;
import com.yahoo.search.grouping.request.AttributeFunction;
import com.yahoo.search.grouping.request.CountAggregator;
import com.yahoo.search.grouping.request.EachOperation;
import com.yahoo.search.grouping.request.GroupingOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.yahoo.prelude.query.AndSegmentItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.MarkerWordItem;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.PhraseSegmentItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VespaSerializerTestCase {

    private static final String SELECT = "select ignoredfield from sourceA where ";
    private YqlParser parser;

    @BeforeEach
    public void setUp() throws Exception {
        ParserEnvironment env = new ParserEnvironment();
        parser = new YqlParser(env);
    }

    @AfterEach
    public void tearDown() throws Exception {
        parser = null;
    }

    @Test
    void requireThatGroupingRequestsAreSerialized() {
        Query query = new Query();
        query.getModel().getQueryTree().setRoot(new WordItem("foo"));
        assertEquals("default contains ({implicitTransforms: false}\"foo\")",
                VespaSerializer.serialize(query));

        newGroupingRequest(query, new AllOperation().setGroupBy(new AttributeFunction("a"))
                .addChild(new EachOperation().addOutput(new CountAggregator())));
        assertEquals("default contains ({implicitTransforms: false}\"foo\") " +
                "| all(group(attribute(a)) each(output(count())))",
                VespaSerializer.serialize(query));

        newGroupingRequest(query, new AllOperation().setGroupBy(new AttributeFunction("b"))
                .addChild(new EachOperation().addOutput(new CountAggregator())));
        assertEquals("default contains ({implicitTransforms: false}\"foo\") " +
                "| all(group(attribute(a)) each(output(count()))) " +
                "| all(group(attribute(b)) each(output(count())))",
                VespaSerializer.serialize(query));
    }

    @Test
    void requireThatGroupingContinuationsAreSerialized() {
        Query query = new Query();
        query.getModel().getQueryTree().setRoot(new WordItem("foo"));
        assertEquals("default contains ({implicitTransforms: false}\"foo\")",
                VespaSerializer.serialize(query));

        newGroupingRequest(query, new AllOperation().setGroupBy(new AttributeFunction("a"))
                        .addChild(new EachOperation().addOutput(new CountAggregator())),
                Continuation.fromString("BCBCBCBEBG"),
                Continuation.fromString("BCBKCBACBKCCK"));
        assertEquals("default contains ({implicitTransforms: false}\"foo\") " +
                "| { continuations:['BCBCBCBEBG', 'BCBKCBACBKCCK'] }" +
                "all(group(attribute(a)) each(output(count())))",
                VespaSerializer.serialize(query));

        newGroupingRequest(query, new AllOperation().setGroupBy(new AttributeFunction("b"))
                        .addChild(new EachOperation().addOutput(new CountAggregator())),
                Continuation.fromString("BCBBBBBDBF"),
                Continuation.fromString("BCBJBPCBJCCJ"));
        assertEquals("default contains ({implicitTransforms: false}\"foo\") " +
                "| { continuations:['BCBCBCBEBG', 'BCBKCBACBKCCK'] }" +
                "all(group(attribute(a)) each(output(count()))) " +
                "| { continuations:['BCBBBBBDBF', 'BCBJBPCBJCCJ'] }" +
                "all(group(attribute(b)) each(output(count())))",
                VespaSerializer.serialize(query));
    }

    @Test
    void testAnd() {
        parseAndConfirm("(description contains \"a\" AND title contains \"that\")");
    }

    private void parseAndConfirm(String expected) {
        parseAndConfirm(expected, expected);
    }

    private void parseAndConfirm(String expected, String toParse) {
        QueryTree item = parser.parse(new Parsable().setQuery(SELECT + toParse + ";"));
        String q = VespaSerializer.serialize(item.getRoot());
        assertEquals(expected, q);
    }

    @Test
    void testAndNot() {
        parseAndConfirm("(description contains \"a\") AND !(title contains \"that\")");
    }

    @Test
    void testEquiv() {
        parseAndConfirm("title contains equiv(\"a\", \"b\")");
    }

    @Test
    void testGeoLocation() {
        parseAndConfirm("geoLocation(workplace, 63.418417, 10.433033, \"0.5 deg\")");
        parseAndConfirm("geoLocation(headquarters, 37.41638, -122.024683, \"180.0 deg\")");
        parseAndConfirm("geoLocation(home, -17.0, 42.0, \"0.0 deg\")");
        parseAndConfirm("geoLocation(workplace, -12.0, -34.0, \"-1.0 deg\")");
    }

    @Test
    void testNear() {
        parseAndConfirm("title contains near(\"a\", \"b\")");
        parseAndConfirm("title contains ({distance: 50}near(\"a\", \"b\"))");
    }

    @Test
    void testNearestNeighbor() {
        parseAndConfirm("{label: \"foo\", targetNumHits: 1000}nearestNeighbor(semantic_embedding, my_property)");
        parseAndConfirm("{targetNumHits: 42}nearestNeighbor(semantic_embedding, my_property)");
        parseAndConfirm("{targetNumHits: 1, hnsw.exploreAdditionalHits: 76}nearestNeighbor(semantic_embedding, my_property)");
        parseAndConfirm("{targetNumHits: 2, approximate: false}nearestNeighbor(semantic_embedding, my_property)");
        parseAndConfirm("{targetNumHits: 3, hnsw.exploreAdditionalHits: 67, approximate: false}nearestNeighbor(semantic_embedding, my_property)");
        parseAndConfirm("{targetNumHits: 4, distanceThreshold: 100100.25}nearestNeighbor(semantic_embedding, my_property)");
    }

    @Test
    void testTrueAndFalse() {
        parseAndConfirm("true");
        parseAndConfirm("false");
    }

    @Test
    void testNumbers() {
        parseAndConfirm("title = 500");
        parseAndConfirm("title > 500");
        parseAndConfirm("title < 500");
    }

    @Test
    void testBoolean() {
        parseAndConfirm("flag = true");
        parseAndConfirm("flag = false");
    }

    @Test
    void testAnnotatedNumbers() {
        parseAndConfirm("title = ({filter: true}500)");
        parseAndConfirm("title > ({filter: true}500)");
        parseAndConfirm("title < ({filter: true}(-500))");
        parseAndConfirm("title <= ({filter: true}(-500))", "([{filter: true}](-500)) >= title");
        parseAndConfirm("title <= ({filter: true}(-500))");
    }

    @Test
    void testRange() {
        parseAndConfirm("range(title, 1, 500)");
    }

    @Test
    void testAnnotatedRange() {
        parseAndConfirm("{filter: true}range(title, 1, 500)");
    }

    @Test
    void testOrderedNear() {
        parseAndConfirm("title contains onear(\"a\", \"b\")");
    }

    @Test
    void testOr() {
        parseAndConfirm("(description contains \"a\" OR title contains \"that\")");
    }

    @Test
    void testDotProduct() {
        parseAndConfirm("dotProduct(description, {\"a\": 1, \"b\": 2})");
    }

    @Test
    void testPredicate() {
        parseAndConfirm("predicate(boolean,{\"gender\":\"male\"},{\"age\":25L})");
        parseAndConfirm("predicate(boolean,{\"gender\":\"male\",\"hobby\":\"music\",\"hobby\":\"hiking\"}," +
                "{\"age\":25L})",
                "predicate(boolean,{\"gender\":\"male\",\"hobby\":[\"music\",\"hiking\"]},{\"age\":25})");
        parseAndConfirm("predicate(boolean,{\"0x3\":{\"gender\":\"male\"},\"0x1\":{\"hobby\":\"music\"},\"0x1\":{\"hobby\":\"hiking\"}},{\"0x80ffffffffffffff\":{\"age\":23L}})",
                "predicate(boolean,{\"0x3\":{\"gender\":\"male\"},\"0x1\":{\"hobby\":[\"music\",\"hiking\"]}},{\"0x80ffffffffffffff\":{\"age\":23L}})");
        parseAndConfirm("predicate(boolean,0,0)");
        parseAndConfirm("predicate(boolean,0,0)", "predicate(boolean,null,void)");
        parseAndConfirm("predicate(boolean,0,0)", "predicate(boolean,{},{})");
    }

    @Test
    void testPhrase() {
        parseAndConfirm("description contains phrase(\"a\", \"b\")");
    }

    @Test
    void testAnnotatedPhrase() {
        parseAndConfirm("description contains ({id: 1}phrase(\"a\", \"b\"))");
    }

    @Test
    void testAnnotatedNear() {
        parseAndConfirm("description contains ({distance: 37}near(\"a\", \"b\"))");
    }

    @Test
    void testAnnotatedOnear() {
        parseAndConfirm("description contains ({distance: 37}onear(\"a\", \"b\"))");
    }

    @Test
    void testAnnotatedEquiv() {
        parseAndConfirm("description contains ({id: 1}equiv(\"a\", \"b\"))");
    }

    @Test
    void testAnnotatedPhraseSegment() {
        PhraseSegmentItem phraseSegment = new PhraseSegmentItem("abc", true, false);
        phraseSegment.addItem(new WordItem("a", "indexNamePlaceholder"));
        phraseSegment.addItem(new WordItem("b", "indexNamePlaceholder"));
        phraseSegment.setIndexName("someIndexName");
        phraseSegment.setLabel("labeled");
        phraseSegment.lock();
        String q = VespaSerializer.serialize(phraseSegment);
        assertEquals("someIndexName contains ({origin: {original: \"abc\", offset: 0, length: 3}, label: \"labeled\"}phrase(\"a\", \"b\"))", q);
    }

    @Test
    void testSameElement() {
        SameElementItem sameElement = new SameElementItem("ss");
        sameElement.addItem(new WordItem("a", "f1"));
        sameElement.addItem(new WordItem("b", "f2"));
        assertEquals("ss:{f1:a f2:b}", sameElement.toString());
        assertEquals("ss contains sameElement(f1 contains ({implicitTransforms: false}\"a\"), f2 contains ({implicitTransforms: false}\"b\"))", VespaSerializer.serialize(sameElement));

    }

    @Test
    void testAnnotatedAndSegment() {
        AndSegmentItem andSegment = new AndSegmentItem("abc", true, false);
        andSegment.addItem(new WordItem("a", "indexNamePlaceholder"));
        andSegment.addItem(new WordItem("b", "indexNamePlaceholder"));
        andSegment.setLabel("labeled");
        String q = VespaSerializer.serialize(andSegment);
        assertEquals("indexNamePlaceholder contains ({origin: {original: \"abc\", offset: 0, length: 3}, andSegmenting: true}phrase(\"a\", \"b\"))", q);

        andSegment.setIndexName("someIndexName");
        andSegment.lock();
        q = VespaSerializer.serialize(andSegment);
        assertEquals("someIndexName contains ({origin: {original: \"abc\", offset: 0, length: 3}, andSegmenting: true}phrase(\"a\", \"b\"))", q);
    }

    @Test
    void testPhraseWithAnnotations() {
        parseAndConfirm("description contains phrase(({id: 15}\"a\"), \"b\")");
    }

    @Test
    void testPhraseSegmentInPhrase() {
        parseAndConfirm("description contains phrase(\"a\", \"b\", ({origin: {original: \"c d\", offset: 0, length: 3}}phrase(\"c\", \"d\")))");
    }

    @Test
    void testRank() {
        parseAndConfirm("rank(a contains \"A\", b contains \"B\")");
    }

    @Test
    void testWand() {
        parseAndConfirm("wand(description, {\"a\": 1, \"b\": 2})");
    }

    @Test
    void testWeakAnd() {
        parseAndConfirm("weakAnd(a contains \"A\", b contains \"B\")");
    }

    @Test
    void testAnnotatedWeakAnd() {
        parseAndConfirm("({" + YqlParser.TARGET_NUM_HITS + ": 10}weakAnd(a contains \"A\", b contains \"B\"))");
    }

    @Test
    void testWeightedSet() {
        parseAndConfirm("weightedSet(description, {\"a\": 1, \"b\": 2})");
    }

    @Test
    void testAnnotatedWord() {
        parseAndConfirm("description contains ({andSegmenting: true}\"a\")");
        parseAndConfirm("description contains ({weight: 37}\"a\")");
        parseAndConfirm("description contains ({id: 37}\"a\")");
        parseAndConfirm("description contains ({filter: true}\"a\")");
        parseAndConfirm("description contains ({ranked: false}\"a\")");
        parseAndConfirm("description contains ({significance: 37.0}\"a\")");
        parseAndConfirm("description contains ({implicitTransforms: false}\"a\")");
        parseAndConfirm("(description contains ({connectivity: {id: 2, weight: 0.42}, id: 1}\"a\") AND description contains ({id: 2}\"b\"))");
    }

    @Test
    void testPrefix() {
        parseAndConfirm("description contains ({prefix: true}\"a\")");
    }

    @Test
    void testSuffix() {
        parseAndConfirm("description contains ({suffix: true}\"a\")");
    }

    @Test
    void testSubstring() {
        parseAndConfirm("description contains ({substring: true}\"a\")");
    }

    @Test
    void testExoticItemTypes() {
        Item item = MarkerWordItem.createEndOfHost();
        String q = VespaSerializer.serialize(item);
        assertEquals("default contains ({implicitTransforms: false}\"$\")", q);
    }

    @Test
    void testEmptyIndex() {
        Item item = new WordItem("nalle", true);
        String q = VespaSerializer.serialize(item);
        assertEquals("default contains \"nalle\"", q);
    }


    @Test
    void testLongAndNot() {
        NotItem item = new NotItem();
        item.addItem(new WordItem("a"));
        item.addItem(new WordItem("b"));
        item.addItem(new WordItem("c"));
        item.addItem(new WordItem("d"));
        String q = VespaSerializer.serialize(item);
        assertEquals("(default contains ({implicitTransforms: false}\"a\")) AND !(default contains ({implicitTransforms: false}\"b\") OR default contains ({implicitTransforms: false}\"c\") OR default contains ({implicitTransforms: false}\"d\"))", q);
    }

    @Test
    void testPhraseAsOperatorArgument() {
        // flattening phrases is a feature, not a bug
        parseAndConfirm("description contains phrase(\"a\", \"b\", \"c\")",
                "description contains phrase(\"a\", phrase(\"b\", \"c\"))");
        parseAndConfirm("description contains equiv(\"a\", phrase(\"b\", \"c\"))");
    }

    private static void newGroupingRequest(Query query, GroupingOperation grouping, Continuation... continuations) {
        GroupingRequest request = GroupingRequest.newInstance(query);
        request.setRootOperation(grouping);
        request.continuations().addAll(Arrays.asList(continuations));
    }

    @Test
    void testNumberTypeInt() {
        parseAndConfirm("title = 500");
        parseAndConfirm("title > 500");
        parseAndConfirm("title < (-500)");
        parseAndConfirm("title >= (-500)");
        parseAndConfirm("title <= (-500)");
        parseAndConfirm("range(title, 0, 500)");
    }

    @Test
    void testNumberTypeLong() {
        parseAndConfirm("title = 549755813888L");
        parseAndConfirm("title > 549755813888L");
        parseAndConfirm("title < (-549755813888L)");
        parseAndConfirm("title >= (-549755813888L)");
        parseAndConfirm("title <= (-549755813888L)");
        parseAndConfirm("range(title, -549755813888L, 549755813888L)");
    }

    @Test
    void testNumberTypeFloat() {
        parseAndConfirm("title = 500.0"); // silly
        parseAndConfirm("title > 500.0");
        parseAndConfirm("title < (-500.0)");
        parseAndConfirm("title >= (-500.0)");
        parseAndConfirm("title <= (-500.0)");
        parseAndConfirm("range(title, 0.0, 500.0)");
    }

    @Test
    void testAnnotatedLong() {
        parseAndConfirm("title >= ({id: 2014}(-549755813888L))");
    }

    @Test
    void testHitLimit() {
        parseAndConfirm("title <= ({hitLimit: 89}(-500))");
        parseAndConfirm("title <= ({hitLimit: 89}(-500))");
        parseAndConfirm("{hitLimit: 89}range(title, 1, 500)");
    }

    @Test
    void testOpenIntervals() {
        parseAndConfirm("range(title, 0.0, 500.0)");
        parseAndConfirm("({bounds: \"open\"}range(title, 0.0, 500.0))");
        parseAndConfirm("({bounds: \"leftOpen\"}range(title, 0.0, 500.0))");
        parseAndConfirm("({bounds: \"rightOpen\"}range(title, 0.0, 500.0))");
        parseAndConfirm("({id: 500, bounds: \"rightOpen\"}range(title, 0.0, 500.0))");
    }

    @Test
    void testRegExp() {
        parseAndConfirm("foo matches \"a b\"");
    }

    @Test
    void testWordAlternatives() {
        parseAndConfirm("foo contains" + " ({origin: {original: \" trees \", offset: 1, length: 5}}"
                + "alternatives({\"trees\": 1.0, \"tree\": 0.7}))");
    }

    @Test
    void testWordAlternativesInPhrase() {
        parseAndConfirm("foo contains phrase(\"forest\","
                + " ({origin: {original: \" trees \", offset: 1, length: 5}}"
                + "alternatives({\"trees\": 1.0, \"tree\": 0.7}))"
                + ")");
    }

    @Test
    void testFuzzy() {
        parseAndConfirm("foo contains fuzzy(\"a\")");
    }

    @Test
    void testFuzzyAnnotations() {
        parseAndConfirm("foo contains ({maxEditDistance:3,prefixLength:5}fuzzy(\"a\"))");
    }

}
