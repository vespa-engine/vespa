// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.select;

import com.yahoo.json.Jackson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.ExactStringItem;
import com.yahoo.prelude.query.FuzzyItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.RegExpItem;
import com.yahoo.prelude.query.SegmentingRule;
import com.yahoo.prelude.query.Substring;
import com.yahoo.prelude.query.SubstringItem;
import com.yahoo.prelude.query.SuffixItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WordAlternativesItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.Query;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.request.AllOperation;
import com.yahoo.search.grouping.request.AttributeValue;
import com.yahoo.search.grouping.request.CountAggregator;
import com.yahoo.search.grouping.request.EachOperation;
import com.yahoo.search.grouping.request.MaxAggregator;
import com.yahoo.search.grouping.request.MinAggregator;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.Select;
import com.yahoo.search.query.SelectParser;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.yql.VespaGroupingStep;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Query.Select
 *
 * @author henrhoi
 * @author bratseth
 */
public class SelectTestCase {

    private static final ObjectMapper jsonMapper = Jackson.mapper();

    private final SelectParser parser = new SelectParser(new ParserEnvironment());

    //------------------------------------------------------------------- "where" tests

    @Test
    void test_contains() {
        ObjectNode json = jsonMapper.createObjectNode();
        ArrayNode arrayNode = jsonMapper.createArrayNode();
        arrayNode.add("default").add("foo");
        json.set("contains", arrayNode);
        assertParse(json.toString(), "default:foo");
    }

    @Test
    void test() {
        assertParse("{'contains' : ['title', 'madonna']}",
                "title:madonna");
    }


    @Test
    void testDottedFieldNames() {
        assertParse("{ 'contains' : ['my.nested.title', 'madonna']}",
                "my.nested.title:madonna");
    }

    @Test
    void testOr() {
        ObjectNode json_two_or = jsonMapper.createObjectNode();
        ObjectNode json_three_or = jsonMapper.createObjectNode();
        ArrayNode contains1 = jsonMapper.createArrayNode().add("title").add("madonna");
        ArrayNode contains2 = jsonMapper.createArrayNode().add("title").add("saint");
        ArrayNode contains3 = jsonMapper.createArrayNode().add("title").add("angel");

        ObjectNode contains_json1 = jsonMapper.createObjectNode();
        ObjectNode contains_json2 = jsonMapper.createObjectNode();
        ObjectNode contains_json3 = jsonMapper.createObjectNode();
        contains_json1.set("contains", contains1);
        contains_json2.set("contains", contains2);
        contains_json3.set("contains", contains3);

        json_two_or.set("or", jsonMapper.createArrayNode().add(contains_json1).add(contains_json2));
        json_three_or.set("or", jsonMapper.createArrayNode().add(contains_json1).add(contains_json2).add(contains_json3));

        assertParse(json_two_or.toString(), "OR title:madonna title:saint");
        assertParse(json_three_or.toString(), "OR title:madonna title:saint title:angel");
    }

    @Test
    void testAnd() {
        ObjectNode json_two_and = jsonMapper.createObjectNode();
        ObjectNode json_three_and = jsonMapper.createObjectNode();
        ArrayNode contains1 = jsonMapper.createArrayNode().add("title").add("madonna");
        ArrayNode contains2 = jsonMapper.createArrayNode().add("title").add("saint");
        ArrayNode contains3 = jsonMapper.createArrayNode().add("title").add("angel");

        ObjectNode contains_json1 = jsonMapper.createObjectNode();
        ObjectNode contains_json2 = jsonMapper.createObjectNode();
        ObjectNode contains_json3 = jsonMapper.createObjectNode();
        contains_json1.set("contains", contains1);
        contains_json2.set("contains", contains2);
        contains_json3.set("contains", contains3);

        json_two_and.set("and", jsonMapper.createArrayNode().add(contains_json1).add(contains_json2));
        json_three_and.set("and", jsonMapper.createArrayNode().add(contains_json1).add(contains_json2).add(contains_json3));

        assertParse(json_two_and.toString(), "AND title:madonna title:saint");
        assertParse(json_three_and.toString(), "AND title:madonna title:saint title:angel");
    }

    @Test
    void testAndNot() {
        ObjectNode json_and_not = jsonMapper.createObjectNode();
        ArrayNode contains1 = jsonMapper.createArrayNode().add("title").add("madonna");
        ArrayNode contains2 = jsonMapper.createArrayNode().add("title").add("saint");

        ObjectNode contains_json1 = jsonMapper.createObjectNode();
        ObjectNode contains_json2 = jsonMapper.createObjectNode();
        contains_json1.set("contains", contains1);
        contains_json2.set("contains", contains2);

        json_and_not.set("and_not", jsonMapper.createArrayNode().add(contains_json1).add(contains_json2));

        assertParse(json_and_not.toString(),
                "+title:madonna -title:saint");
    }

    @Test
    void testLessThan() {
        ObjectNode range_json = jsonMapper.createObjectNode();
        ObjectNode operators = jsonMapper.createObjectNode();
        operators.put("<", 500);

        ArrayNode range = jsonMapper.createArrayNode().add("price").add(operators);

        range_json.set("range", range);

        assertParse(range_json.toString(),
                "price:<500");
    }

    @Test
    void testGreaterThan() {
        ObjectNode range_json = jsonMapper.createObjectNode();
        ObjectNode operators = jsonMapper.createObjectNode();
        operators.put(">", 500);

        ArrayNode range = jsonMapper.createArrayNode().add("price").add(operators);

        range_json.set("range", range);

        assertParse(range_json.toString(),
                "price:>500");
    }

    @Test
    void testLessThanOrEqual() {
        ObjectNode range_json = jsonMapper.createObjectNode();
        ObjectNode operators = jsonMapper.createObjectNode();
        operators.put("<=", 500);

        ArrayNode range = jsonMapper.createArrayNode().add("price").add(operators);

        range_json.set("range", range);

        assertParse(range_json.toString(),
                "price:[;500]");
    }

    @Test
    void testGreaterThanOrEqual() {
        ObjectNode range_json = jsonMapper.createObjectNode();
        ObjectNode operators = jsonMapper.createObjectNode();
        operators.put(">=", 500);

        ArrayNode range = jsonMapper.createArrayNode().add("price").add(operators);

        range_json.set("range", range);

        assertParse(range_json.toString(),
                "price:[500;]");
    }

    @Test
    void testEquality() {
        ObjectNode range_json = jsonMapper.createObjectNode();
        ObjectNode operators = jsonMapper.createObjectNode();
        operators.put("=", 500);

        ArrayNode range = jsonMapper.createArrayNode().add("price").add(operators);

        range_json.set("range", range);

        assertParse(range_json.toString(),
                "price:500");
    }

    @Test
    void testNegativeLessThan() {
        ObjectNode range_json = jsonMapper.createObjectNode();
        ObjectNode operators = jsonMapper.createObjectNode();
        operators.put("<", -500);

        ArrayNode range = jsonMapper.createArrayNode().add("price").add(operators);

        range_json.set("range", range);

        assertParse(range_json.toString(),
                "price:<-500");
    }

    @Test
    void testNegativeGreaterThan() {
        ObjectNode range_json = jsonMapper.createObjectNode();
        ObjectNode operators = jsonMapper.createObjectNode();
        operators.put(">", -500);

        ArrayNode range = jsonMapper.createArrayNode().add("price").add(operators);

        range_json.set("range", range);

        assertParse(range_json.toString(),
                "price:>-500");
    }

    @Test
    void testNegativeLessThanOrEqual() {
        ObjectNode range_json = jsonMapper.createObjectNode();
        ObjectNode operators = jsonMapper.createObjectNode();
        operators.put("<=", -500);

        ArrayNode range = jsonMapper.createArrayNode().add("price").add(operators);

        range_json.set("range", range);

        assertParse(range_json.toString(),
                "price:[;-500]");
    }

    @Test
    void testNegativeGreaterThanOrEqual() {
        ObjectNode range_json = jsonMapper.createObjectNode();
        ObjectNode operators = jsonMapper.createObjectNode();
        operators.put(">=", -500);

        ArrayNode range = jsonMapper.createArrayNode().add("price").add(operators);

        range_json.set("range", range);

        assertParse(range_json.toString(),
                "price:[-500;]");
    }

    @Test
    void testNegativeEquality() {
        ObjectNode range_json = jsonMapper.createObjectNode();
        ObjectNode operators = jsonMapper.createObjectNode();
        operators.put("=", -500);

        ArrayNode range = jsonMapper.createArrayNode().add("price").add(operators);

        range_json.set("range", range);

        assertParse(range_json.toString(),
                "price:-500");
    }

    @Test
    void testAnnotatedLessThan() {
        String jsonString = "{ \"range\": { \"children\" : [\"price\", {\"<\" : -500}], \"attributes\" : {\"filter\" : true} } }";
        assertParse(jsonString, "|price:<-500");
    }

    @Test
    void testAnnotatedGreaterThan() {
        String jsonString = "{ \"range\": { \"children\" : [\"price\", {\">\" : 500}], \"attributes\" : {\"filter\" : true} } }";
        assertParse(jsonString, "|price:>500");
    }

    @Test
    void testAnnotatedLessThanOrEqual() {
        String jsonString = "{ \"range\": { \"children\" : [\"price\", {\"<=\" : -500}], \"attributes\" : {\"filter\" : true} } }";
        assertParse(jsonString, "|price:[;-500]");
    }

    @Test
    void testAnnotatedGreaterThanOrEqual() {
        String jsonString = "{ \"range\": { \"children\" : [\"price\", {\">=\" : 500}], \"attributes\" : {\"filter\" : true} } }";
        assertParse(jsonString, "|price:[500;]");
    }


    @Test
    void testAnnotatedEquality() {
        String jsonString = "{ \"range\": { \"children\" : [\"price\", {\"=\" : -500}], \"attributes\" : {\"filter\" : true} } }";
        assertParse(jsonString, "|price:-500");
    }

    @Test
    void testTermAnnotations() {
        assertEquals("merkelapp",
                getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"label\" : \"merkelapp\"} } }").getLabel());
        assertEquals("another",
                getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"annotations\" : {\"cox\" : \"another\"} } } }").getAnnotation("cox"));
        assertEquals(23.0, getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"significance\" : 23.0 } } }").getSignificance(), 1E-6);
        assertEquals(150, getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"weight\" : 150 } } }").getWeight());
        assertFalse(getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"usePositionData\" : false } } }").usePositionData());
        assertTrue(getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"filter\" : true } } }").isFilter());
        assertFalse(getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"ranked\" : false } } }").isRanked());
        Substring origin = getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"origin\": {\"original\": \"abc\", \"offset\": 1, \"length\": 2}} } }").getOrigin();
        assertEquals("abc", origin.string);
        assertEquals(1, origin.start);
        assertEquals(3, origin.end);
    }

    @Test
    void testSameElement() {
        assertParse("{ \"contains\": [ \"baz\", {\"sameElement\" : [ { \"contains\" : [\"f1\", \"a\"] }, { \"contains\" : [\"f2\", \"b\"] } ]} ] }",
                "baz:{f1:a f2:b}");

        assertParse("{ \"contains\": [ \"baz\", {\"sameElement\" : [ { \"contains\" : [\"f1\", \"a\"] }, {\"range\":[\"f2\",{\"=\":10}] } ]} ] }",
                "baz:{f1:a f2:10}");

        assertParse("{ \"contains\": [ \"baz\", {\"sameElement\" : [ { \"contains\" : [\"key\", \"a\"] }, {\"range\":[\"value.f2\",{\"=\":10}] } ]} ] }",
                "baz:{key:a value.f2:10}");
    }

    @Test
    void testPhrase() {
        assertParse("{ \"contains\": [ \"baz\", {\"phrase\" : [ \"a\", \"b\"] } ] }",
                "baz:\"a b\"");
    }

    @Test
    void testNestedPhrase() {
        assertParse("{ \"contains\": [ \"baz\", {\"phrase\" : [ \"a\", \"b\", {\"phrase\" : [ \"c\", \"d\"] }] } ] }",
                "baz:\"a b c d\"");
    }

    @Test
    void testStemming() {
        assertTrue(getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"stem\" : false} } }").isStemmed());
        assertFalse(getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"stem\" : true} } }").isStemmed());
        assertFalse(getRootWord("{ \"contains\": [\"baz\", \"colors\"] }").isStemmed());
    }

    @Test
    void testRaw() {
        Item root = parseWhere("{ \"contains\":[ \"baz\", \"yoni jo dima\" ] }").getRoot();
        assertTrue(root instanceof WordItem);
        assertFalse(root instanceof ExactStringItem);
        assertEquals("yoni jo dima", ((WordItem) root).getWord());

        root = parseWhere("{ \"contains\": { \"children\" : [\"baz\", \"yoni jo dima\"], \"attributes\" : {\"grammar\" : \"raw\"} } }").getRoot();
        assertTrue(root instanceof WordItem);
        assertFalse(root instanceof ExactStringItem);
        assertEquals("yoni jo dima", ((WordItem) root).getWord());
    }

    @Test
    void testAccentDropping() {
        assertFalse(getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"accentDrop\" : false} } }").isNormalizable());
        assertTrue(getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"accentDrop\" : true} } }").isNormalizable());
        assertTrue(getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"colors\"] } }").isNormalizable());
    }

    @Test
    void testCaseNormalization() {
        assertTrue(getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"normalizeCase\" : false} } }").isLowercased());
        assertFalse(getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"normalizeCase\" : true} } }").isLowercased());
        assertFalse(getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"colors\"] } }").isLowercased());
    }

    @Test
    void testSegmentingRule() {
        assertEquals(SegmentingRule.PHRASE,
                getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"andSegmenting\" : false} } }").getSegmentingRule());
        assertEquals(SegmentingRule.BOOLEAN_AND,
                getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"andSegmenting\" : true} } }").getSegmentingRule());
        assertEquals(SegmentingRule.LANGUAGE_DEFAULT,
                getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"colors\"] } }").getSegmentingRule());
    }

    @Test
    void testNfkc() {
        assertEquals("a\u030a", getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"a\\u030a\"], \"attributes\" : {\"nfkc\" : false} } }").getWord());
        assertEquals("\u00e5", getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"a\\u030a\"], \"attributes\" : {\"nfkc\" : true} } }").getWord());
        assertEquals("a\u030a", getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"a\\u030a\"] } } ").getWord(), "No NKFC by default");
    }

    @Test
    void testImplicitTransforms() {
        assertFalse(getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"cox\"], \"attributes\" : {\"implicitTransforms\" : false} } }").isFromQuery());
        assertTrue(getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"cox\"], \"attributes\" : {\"implicitTransforms\" : true} } }").isFromQuery());
        assertTrue(getRootWord("{ \"contains\": { \"children\" : [\"baz\", \"cox\"] } }").isFromQuery());
    }

    @Test
    void testConnectivity() {
        QueryTree parsed = parseWhere("{ \"and\": [ {\"contains\" : { \"children\" : [\"title\", \"madonna\"], \"attributes\" : {\"id\": 1, \"connectivity\": {\"id\": 3, \"weight\": 7.0}} } }, " +
                "{ \"contains\" : { \"children\" : [\"title\", \"saint\"], \"attributes\" : {\"id\": 2} } }, " +
                "{ \"contains\" : { \"children\" : [\"title\", \"angel\"], \"attributes\" : {\"id\": 3} } } ] }");
        assertEquals("AND title:madonna title:saint title:angel", parsed.toString());

        AndItem root = (AndItem) parsed.getRoot();
        WordItem first = (WordItem) root.getItem(0);
        WordItem second = (WordItem) root.getItem(1);
        WordItem third = (WordItem) root.getItem(2);
        assertEquals(third, first.getConnectedItem());
        assertEquals(first.getConnectivity(), 7.0d, 1E-6);
        assertNull(second.getConnectedItem());

        assertParseFail("{ \"and\": [ {\"contains\" : { \"children\" : [\"title\", \"madonna\"], \"attributes\" : {\"id\": 1, \"connectivity\": {\"id\": 4, \"weight\": 7.0}} } }, " +
                "{ \"contains\" : { \"children\" : [\"title\", \"saint\"], \"attributes\" : {\"id\": 2} } }, " +
                "{ \"contains\" : { \"children\" : [\"title\", \"angel\"], \"attributes\" : {\"id\": 3} } } ] }",
                new IllegalArgumentException("Item 'title:madonna' was specified to connect to item with ID 4, " +
                        "which does not exist in the query."));
    }

    @Test
    void testAnnotatedPhrase() {
        QueryTree parsed = parseWhere("{ \"contains\": [\"baz\", { \"phrase\": { \"children\": [\"a\", \"b\"], \"attributes\": { \"label\": \"hello world\" } } }] }");
        assertEquals("baz:\"a b\"", parsed.toString());
        PhraseItem phrase = (PhraseItem) parsed.getRoot();
        assertEquals("hello world", phrase.getLabel());
    }

    @Test
    void testRange() {
        QueryTree parsed = parseWhere("{ \"range\": [\"baz\", { \">=\": 1, \"<=\": 8 }] }");
        assertEquals("baz:[1;8]", parsed.toString());
    }

    @Test
    void testNegativeRange() {
        QueryTree parsed = parseWhere("{ \"range\": [\"baz\", { \">=\": -8, \"<=\": -1 }] }");
        assertEquals("baz:[-8;-1]", parsed.toString());
    }

    @Test
    void testRangeIllegalArguments() {
        assertParseFail("{ \"range\": [\"baz\", { \">=\": \"cox\", \"<=\": -1 }] }",
                new IllegalArgumentException("Expected a numeric argument to range, but got the string 'cox'"));
    }

    @Test
    void testNear() {
        assertParse("{ \"contains\": [\"description\", { \"near\": [\"a\", \"b\"] }] }",
                "NEAR(2) description:a description:b");
        assertParse("{ \"contains\": [\"description\", { \"near\": { \"children\": [\"a\", \"b\"], \"attributes\": { \"distance\": 100 } } } ] }",
                "NEAR(100) description:a description:b");
    }

    @Test
    void testOrderedNear() {
        assertParse("{ \"contains\": [\"description\", { \"onear\": [\"a\", \"b\"] }] }",
                "ONEAR(2) description:a description:b");
        assertParse("{ \"contains\": [\"description\", { \"onear\": { \"children\": [\"a\", \"b\"], \"attributes\": { \"distance\": 100 } } } ] }",
                "ONEAR(100) description:a description:b");
    }

    @Test
    void testWand() {
        assertParse("{ \"wand\": [\"description\", { \"a\": 1, \"b\": 2 }] }",
                "WAND(10,0.0,1.0) description{[1]:\"a\",[2]:\"b\"}");
        assertParse("{ \"wand\": { \"children\": [\"description\", { \"a\": 1, \"b\": 2 }], \"attributes\": { \"scoreThreshold\": 13.3, \"targetHits\": 7, \"thresholdBoostFactor\": 2.3 } } }",
                "WAND(7,13.3,2.3) description{[1]:\"a\",[2]:\"b\"}");
    }

    @Test
    void testNumericWand() {
        String numWand = "WAND(10,0.0,1.0) description{[1]:\"11\",[2]:\"37\"}";
        assertParse("{ \"wand\" : [\"description\", [[11,1], [37,2]] ]}", numWand);
        assertParseFail("{ \"wand\" : [\"description\", 12] }",
                new IllegalArgumentException("Expected ARRAY or OBJECT, got LONG."));
    }

    @Test
    void testWeightedSet() {
        assertParse("{ \"weightedSet\" : [\"description\", {\"a\":1, \"b\":2} ]}",
                "WEIGHTEDSET description{[1]:\"a\",[2]:\"b\"}");
        assertParseFail("{ \"weightedSet\" : [\"description\", {\"a\":\"g\", \"b\":2} ]}",
                new IllegalArgumentException("Expected an integer argument, but got the string 'g'"));
        assertParseFail("{ \"weightedSet\" : [\"description\" ]}",
                new IllegalArgumentException("Expected 2 arguments, got 1."));
    }

    @Test
    void testDotProduct() {
        assertParse("{ \"dotProduct\" : [\"description\", {\"a\":1, \"b\":2} ]}",
                "DOTPRODUCT description{[1]:\"a\",[2]:\"b\"}");
        assertParse("{ \"dotProduct\" : [\"description\", {\"a\":2} ]}",
                "DOTPRODUCT description{[2]:\"a\"}");
    }

    @Test
    void testPredicate() {
        assertParse("{ \"predicate\" : [\"predicate_field\", {\"gender\":\"male\", \"hobby\":[\"music\", \"hiking\"]}, {\"age\":23} ]}",
                "PREDICATE_QUERY_ITEM gender=male, hobby=music, hobby=hiking, age:23");
        assertParse("{ \"predicate\" : [\"predicate_field\", 0, \"void\" ]}",
                "PREDICATE_QUERY_ITEM ");
    }

    @Test
    void testRank() {
        assertParse("{ \"rank\": [{ \"contains\": [\"a\", \"A\"] }, { \"contains\": [\"b\", \"B\"] } ] }",
                "RANK a:A b:B");
        assertParse("{ \"rank\": [{ \"contains\": [\"a\", \"A\"] }, { \"contains\": [\"b\", \"B\"] }, { \"contains\": [\"c\", \"C\"] } ] }",
                "RANK a:A b:B c:C");
        assertParse("{ \"rank\": [{ \"contains\": [\"a\", \"A\"] }, { \"or\": [{ \"contains\": [\"b\", \"B\"] }, { \"contains\": [\"c\", \"C\"] }] }] }",
                "RANK a:A (OR b:B c:C)");
    }

    @Test
    void testGeoLocation() {
        assertParse("{ \"geoLocation\": [ \"workplace\", 63.418417, 10.433033, \"0.5 deg\" ] }",
                "GEO_LOCATION workplace:(2,10433033,63418417,500000,0,1,0,1921876103)");
        assertParse("{ \"geoLocation\": [ \"headquarters\", \"37.416383\", \"-122.024683\", \"100 miles\" ] }",
                "GEO_LOCATION headquarters:(2,-122024683,37416383,1450561,0,1,0,3411238761)");
        assertParse("{ \"geoLocation\": [ \"home\", \"E10.433033\", \"N63.418417\", \"5km\" ] }",
                "GEO_LOCATION home:(2,10433033,63418417,45066,0,1,0,1921876103)");
        assertParse("{ \"geoLocation\": [ \"workplace\", -12.0, -34.0, \"-77 deg\" ] }",
                "GEO_LOCATION workplace:(2,-34000000,-12000000,-1,0,1,0,4201111954)");
    }

    @Test
    void testNearestNeighbor() {
        assertParse("{ \"nearestNeighbor\": [ \"f1field\", \"q2prop\" ] }",
                "NEAREST_NEIGHBOR {field=f1field,queryTensorName=q2prop,hnsw.exploreAdditionalHits=0,distanceThreshold=Infinity,approximate=true,targetHits=0}");

        assertParse("{ \"nearestNeighbor\": { \"children\" : [ \"f3field\", \"q4prop\" ], \"attributes\" : {\"targetHits\": 37, \"hnsw.exploreAdditionalHits\": 42, \"distanceThreshold\": 100100.25 } }}",
                "NEAREST_NEIGHBOR {field=f3field,queryTensorName=q4prop,hnsw.exploreAdditionalHits=42,distanceThreshold=100100.25,approximate=true,targetHits=37}");
    }

    @Test
    void testWeakAnd() {
        assertParse("{ \"weakAnd\": [{ \"contains\": [\"a\", \"A\"] }, { \"contains\": [\"b\", \"B\"] } ] }",
                "WEAKAND(100) a:A b:B");
        assertParse("{ \"weakAnd\": { \"children\" : [{ \"contains\": [\"a\", \"A\"] }, { \"contains\": [\"b\", \"B\"] } ], \"attributes\" : {\"targetHits\": 37} }}",
                "WEAKAND(37) a:A b:B");

        QueryTree tree = parseWhere("{ \"weakAnd\": { \"children\" : [{ \"contains\": [\"a\", \"A\"] }, { \"contains\": [\"b\", \"B\"] } ] }}");
        assertEquals("WEAKAND(100) a:A b:B", tree.toString());
        assertEquals(WeakAndItem.class, tree.getRoot().getClass());
    }

    @Test
    void testEquiv() {
        assertParse("{ \"contains\" : [\"fieldName\", {\"equiv\" : [\"A\",\"B\"]}]}",
                "EQUIV fieldName:A fieldName:B");

        assertParse("{ \"contains\" : [\"fieldName\", {\"equiv\" : [\"ny\",{\"phrase\" : [ \"new\",\"york\" ] } ] } ] }",
                "EQUIV fieldName:ny fieldName:\"new york\"");

        assertParseFail("{ \"contains\" : [\"fieldName\", {\"equiv\" : [\"ny\"] } ] }",
                new IllegalArgumentException("Expected 2 or more arguments, got 1."));
        assertParseFail("{ \"contains\" : [\"fieldName\", {\"equiv\" : [\"ny\",{\"nalle\" : [ \"void\" ] } ] } ] }",
                new IllegalArgumentException("Expected operator phrase, got nalle."));
        assertParseFail("{ \"contains\" : [\"fieldName\", {\"equiv\" : [\"ny\", 42]}]}",
                new IllegalArgumentException("The word of a word item can not be empty"));
    }

    @Test
    void testAffixItems() {
        assertRootClass("{ \"contains\" : { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"suffix\": true} } }",
                SuffixItem.class);


        assertRootClass("{ \"contains\" : { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"prefix\": true} } }",
                PrefixItem.class);
        assertRootClass("{ \"contains\" : { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"substring\": true} } }",
                SubstringItem.class);
        assertParseFail("{ \"contains\" : { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"suffix\": true, \"prefix\" : true} } }",
                new IllegalArgumentException("Only one of prefix, substring and suffix can be set."));
        assertParseFail("{ \"contains\" : { \"children\" : [\"baz\", \"colors\"], \"attributes\" : {\"suffix\": true, \"substring\" : true} } }",
                new IllegalArgumentException("Only one of prefix, substring and suffix can be set."));
    }

    @Test
    void testLongNumberInSimpleExpression() {
        assertParse("{ \"range\" : [ \"price\", { \"=\" : 8589934592 }]}",
                "price:8589934592");
    }

    @Test
    void testNegativeLongNumberInSimpleExpression() {
        assertParse("{ \"range\" : [ \"price\", { \"=\" : -8589934592 }]}",
                "price:-8589934592");
    }

    @Test
    void testNegativeHitLimit() {
        assertParse(
                "{ \"range\" : { \"children\":[ \"foo\", { \">=\" : 0, \"<=\" : 1 }], \"attributes\" : {\"hitLimit\": -38 } } }",
                "foo:[0;1;-38]");
    }

    @Test
    void testRangeSearchHitPopulationOrdering() {
        assertParse("{ \"range\" : { \"children\":[ \"foo\", { \">=\" : 0, \"<=\" : 1 }], \"attributes\" : {\"hitLimit\": 38 ,\"ascending\": true} } }", "foo:[0;1;38]");
        assertParse("{ \"range\" : { \"children\":[ \"foo\", { \">=\" : 0, \"<=\" : 1 }], \"attributes\" : {\"hitLimit\": 38 ,\"ascending\": false} } }", "foo:[0;1;-38]");
        assertParse("{ \"range\" : { \"children\":[ \"foo\", { \">=\" : 0, \"<=\" : 1 }], \"attributes\" : {\"hitLimit\": 38 ,\"descending\": true} } }", "foo:[0;1;-38]");
        assertParse("{ \"range\" : { \"children\":[ \"foo\", { \">=\" : 0, \"<=\" : 1 }], \"attributes\" : {\"hitLimit\": 38 ,\"descending\": false} } }", "foo:[0;1;38]");

        boolean gotExceptionFromParse = false;
        try {
            parseWhere("{ \"range\" : { \"children\":[ \"foo\", { \">=\" : 0, \"<=\" : 1 }], \"attributes\" : {\"hitLimit\": 38, \"ascending\": true, \"descending\": false} } }");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getCause().getMessage().contains("both ascending and descending ordering set"),
                    "Expected information about abuse of settings.");
            gotExceptionFromParse = true;
        }
        assertTrue(gotExceptionFromParse);
    }

    // NB: Uses operator-keys to set bounds, not annotations
    @Test
    void testOpenIntervals() {
        assertParse("{ \"range\" : { \"children\":[ \"title\", { \">=\" : 0.0, \"<=\" : 500.0 }] } }" +
                "select * from sources * where range(title, 0.0, 500.0)",
                "title:[0.0;500.0]");
        assertParse(
                "{ \"range\" : { \"children\":[ \"title\", { \">\" : 0.0, \"<\" : 500.0 }] } }",
                "title:<0.0;500.0>");
        assertParse(
                "{ \"range\" : { \"children\":[ \"title\", { \">\" : 0.0, \"<=\" : 500.0 }]  } }",
                "title:<0.0;500.0]");
        assertParse(
                "{ \"range\" : { \"children\":[ \"title\", { \">=\" : 0.0, \"<\" : 500.0 }] } }",
                "title:[0.0;500.0>");
    }

    @Test
    void testEquals() {
        assertParse("{\"equals\": [\"public\",true]}", "public:true");
        assertParse("{\"equals\": [\"public\",5]}", "public:5");
    }

    @Test
    void testRegexp() {
        QueryTree x = parseWhere("{ \"matches\" : [\"foo\", \"a b\"]}");
        Item root = x.getRoot();
        assertSame(RegExpItem.class, root.getClass());
        assertEquals("a b", ((RegExpItem) root).stringValue());
    }

    @Test
    void testWordAlternatives() {
        QueryTree x = parseWhere("{\"contains\" : [\"foo\", {\"alternatives\" : [{\"trees\": 1.0, \"tree\": 0.7}]}]}");
        Item root = x.getRoot();
        assertSame(WordAlternativesItem.class, root.getClass());
        WordAlternativesItem alternatives = (WordAlternativesItem) root;
        checkWordAlternativesContent(alternatives);
    }

    @Test
    void testFuzzy() {
        QueryTree x = parseWhere("{ \"contains\": [\"description\", { \"fuzzy\": [\"a b\"] }] }");
        Item root = x.getRoot();
        assertSame(FuzzyItem.class, root.getClass());
        var fuzzy = (FuzzyItem) root;
        assertEquals("description", fuzzy.getIndexName());
        assertEquals("a b", fuzzy.stringValue());
        assertEquals(FuzzyItem.DEFAULT_MAX_EDIT_DISTANCE, fuzzy.getMaxEditDistance());
        assertEquals(FuzzyItem.DEFAULT_PREFIX_LENGTH, fuzzy.getPrefixLength());
        assertFalse(fuzzy.isPrefixMatch());
    }

    @Test
    void fuzzy_with_annotations() {
        var where = """
                {
                  "contains": ["description", {
                    "fuzzy": {
                      "children": ["a b"],
                      "attributes": {
                        "maxEditDistance": 3,
                        "prefixLength": 10,
                        "prefix": true
                      }
                    }
                  }]
                }
                """;
        QueryTree x = parseWhere(where);
        Item root = x.getRoot();
        assertSame(FuzzyItem.class, root.getClass());
        var fuzzy = (FuzzyItem) root;
        assertEquals("description", fuzzy.getIndexName());
        assertEquals("a b", fuzzy.stringValue());
        assertEquals(3, fuzzy.getMaxEditDistance());
        assertEquals(10, fuzzy.getPrefixLength());
        assertTrue(fuzzy.isPrefixMatch());
    }

    //------------------------------------------------------------------- grouping tests

    @Test
    void testGrouping() {
        String grouping = "[ { \"all\" : { \"group\" : \"time.year(a)\", \"each\" : { \"output\" : \"count()\" } } } ]";
        String expected = "[[]all(group(time.year(a)) each(output(count())))]";
        assertGrouping(expected, parseGrouping(grouping));
    }

    @Test
    void testMultipleGroupings() {
        String grouping = "[ { \"all\" : { \"group\" : \"a\", \"each\" : { \"output\" : \"count()\"}}}, { \"all\" : { \"group\" : \"b\", \"each\" : { \"output\" : \"count()\"}}} ]";
        String expected = "[[]all(group(a) each(output(count()))), []all(group(b) each(output(count())))]";

        assertGrouping(expected, parseGrouping(grouping));
    }

    @Test
    void testGroupingWithPredefinedBuckets() {
        String grouping = "[ { \"all\" : { \"group\" : { \"predefined\" : [ \"foo\", { \"bucket\": [1,2]}, { \"bucket\": [3,4]} ] } } } ]";
        String expected = "[[]all(group(predefined(foo, bucket[1, 2>, bucket[3, 4>)))]";
        assertGrouping(expected, parseGrouping(grouping));
    }

    @Test
    void testMultipleOutputs() {
        String grouping = "[ { \"all\" : { \"group\" : \"b\", \"each\" : {\"output\": [ \"count()\", \"avg(foo)\" ] } } } ]";
        String expected = "[[]all(group(b) each(output(count(), avg(foo))))]";
        assertGrouping(expected, parseGrouping(grouping));
    }

    //------------------------------------------------------------------- Other tests

    @Test
    void testOverridingOtherQueryTree() {
        Query query = new Query("?query=default:query");
        assertEquals("WEAKAND(100) default:query", query.getModel().getQueryTree().toString());
        assertEquals(Query.Type.WEAKAND, query.getModel().getType());

        query.getSelect().setWhereString("{\"contains\" : [\"default\", \"select\"] }");
        assertEquals("default:select", query.getModel().getQueryTree().toString());
        assertEquals(Query.Type.SELECT, query.getModel().getType());
    }

    @Test
    void testOverridingWhereQueryTree() {
        Query query = new Query("?query=default:query");
        query.getSelect().setWhereString("{\"contains\" : [\"default\", \"select\"] }");
        assertEquals("default:select", query.getModel().getQueryTree().toString());
        assertEquals(Query.Type.SELECT, query.getModel().getType());

        query.getModel().setQueryString("default:query");
        query.getModel().setType("all");
        assertEquals("default:query", query.getModel().getQueryTree().toString());
        assertEquals(Query.Type.ALL, query.getModel().getType());
    }

    @Test
    void testProgrammaticAssignment() {
        Query query = new Query();
        query.getSelect().setGroupingString("[ { \"all\" : { \"group\" : \"time.year(a)\", \"each\" : { \"output\" : \"count()\" } } } ]");
        assertEquals(1, query.getSelect().getGrouping().size());
        assertEquals("all(group(time.year(a)) each(output(count())))", query.getSelect().getGrouping().get(0).getRootOperation().toString());

        // Setting from string resets the grouping expression
        query.getSelect().setGroupingString("[ { \"all\" : { \"group\" : \"time.dayofmonth(a)\", \"each\" : { \"output\" : \"count()\" } } } ]");
        assertEquals(1, query.getSelect().getGrouping().size());
        assertEquals("all(group(time.dayofmonth(a)) each(output(count())))", query.getSelect().getGrouping().get(0).getRootOperation().toString());
    }

    @Test
    void testConstructionAndClone() {
        Query query = new Query();
        query.getSelect().setWhereString("{\"contains\" : [\"default\", \"select\"] }");
        query.getSelect().setGroupingString("[ { \"all\" : { \"group\" : \"time.dayofmonth(a)\", \"each\" : { \"output\" : \"count()\" } } } ]");
        GroupingRequest secondRequest = GroupingRequest.newInstance(query);
        assertEquals("default:select", query.getModel().getQueryTree().toString());
        assertEquals(2, query.getSelect().getGrouping().size());
        assertEquals("all(group(time.dayofmonth(a)) each(output(count())))", query.getSelect().getGrouping().get(0).toString());

        Query clone = query.clone();
        assertEquals(clone.getSelect().getGroupingExpressionString(), query.getSelect().getGroupingExpressionString());
        assertNotSame(query.getSelect(), clone.getSelect());
        assertNotSame(query.getSelect().getGrouping(), clone.getSelect().getGrouping());
        assertNotSame(query.getSelect().getGrouping().get(0), clone.getSelect().getGrouping().get(0));
        assertNotSame(query.getSelect().getGrouping().get(1), clone.getSelect().getGrouping().get(1));
        assertEquals(query.getSelect().getWhereString(), clone.getSelect().getWhereString());
        assertEquals(query.getSelect().getGroupingString(), clone.getSelect().getGroupingString());
        assertEquals(query.getSelect().getGrouping().get(0).toString(), clone.getSelect().getGrouping().get(0).toString());
        assertEquals(query.getSelect().getGrouping().get(1).toString(), clone.getSelect().getGrouping().get(1).toString());
    }

    @Test
    void testCloneWithGroupingExpressionString() {
        Query query = new Query();
        query.getSelect().setGroupingExpressionString("all(group(foo) each(output(count())))");

        Query clone = query.clone();
        assertEquals(clone.getSelect().getGroupingExpressionString(), query.getSelect().getGroupingExpressionString());
    }

    @Test
    void testProgrammaticBuilding() {
        String expected =
                "all(group(myfield) max(10000) each(" +
                        "output(min(foo), max(bar)) " +
                        "all(group(foo) max(10000) output(count()))" +
                        "))";
        Query query = new Query();
        GroupingRequest grouping = GroupingRequest.newInstance(query);
        AllOperation root = new AllOperation();
        root.setGroupBy(new AttributeValue("myfield"));
        root.setMax(10000);
        EachOperation each = new EachOperation();
        each.addOutput(new MinAggregator(new AttributeValue("foo")));
        each.addOutput(new MaxAggregator(new AttributeValue("bar")));
        AllOperation all = new AllOperation();
        all.setGroupBy(new AttributeValue("foo"));
        all.setMax(10000);
        all.addOutput(new CountAggregator());
        each.addChild(all);
        root.addChild(each);
        grouping.setRootOperation(root);

        assertEquals(expected, query.getSelect().getGrouping().get(0).toString());
    }

    //------------------------------------------------------------------- Assert methods

    private void assertParse(String where, String expectedQueryTree) {
        String queryTree = parseWhere(where).toString();
        assertEquals(expectedQueryTree, queryTree);
    }

    private void assertParseFail(String where, Throwable expectedException) {
        try {
            parseWhere(where).toString();
            fail("Parse succeeded: " + where);
        } catch (Throwable outer) {
            assertEquals(IllegalInputException.class, outer.getClass());
            assertEquals("Illegal JSON query", outer.getMessage());
            Throwable cause = outer.getCause();
            assertEquals(expectedException.getClass(), cause.getClass());
            assertEquals(expectedException.getMessage(), cause.getMessage());
        }
    }

    private void assertRootClass(String where, Class<? extends Item> expectedRootClass) {
        assertEquals(expectedRootClass, parseWhere(where).getRoot().getClass());
    }

    private void assertGrouping(String expected, List<VespaGroupingStep> steps) {
        List<String> actual = new ArrayList<>(steps.size());
        for (VespaGroupingStep step : steps) {
            actual.add(step.continuations().toString() +
                    step.getOperation());
        }
        assertEquals(expected, actual.toString());
    }

    //------------------------------------------------------------------- Parse methods

    private QueryTree parseWhere(String where) {
        Select select = new Select(where, "", new Query());

        return parser.parse(new Parsable().setSelect(select));
    }

    private List<VespaGroupingStep> parseGrouping(String grouping) {
        return parser.getGroupingSteps(grouping);
    }

    //------------------------------------------------------------------- Other methods

    private WordItem getRootWord(String yqlQuery) {
        Item root = parseWhere(yqlQuery).getRoot();
        assertTrue(root instanceof WordItem);
        return (WordItem)root;
    }

    private void checkWordAlternativesContent(WordAlternativesItem alternatives) {
        boolean seenTree = false;
        boolean seenForest = false;
        final String forest = "trees";
        final String tree = "tree";
        assertEquals(2, alternatives.getAlternatives().size());
        for (WordAlternativesItem.Alternative alternative : alternatives.getAlternatives()) {
            if (tree.equals(alternative.word)) {
                assertFalse(seenTree, "Duplicate term introduced");
                seenTree = true;
                assertEquals(.7d, alternative.exactness, 1e-15d);
            } else if (forest.equals(alternative.word)) {
                assertFalse(seenForest, "Duplicate term introduced");
                seenForest = true;
                assertEquals(1.0d, alternative.exactness, 1e-15d);
            } else {
                fail("Unexpected term: " + alternative.word);
            }
        }
    }

}
