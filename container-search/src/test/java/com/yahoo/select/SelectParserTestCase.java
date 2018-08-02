// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.select;

import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.Substring;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.Select;
import com.yahoo.search.query.SelectParser;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


/**
 * Specification for the conversion of Select expressions to Vespa search queries.
 *
 * @author henrhoi
 */

public class SelectParserTestCase {

    private final SelectParser parser = new SelectParser(new ParserEnvironment());

    @Test
    public void test_contains() throws Exception {
        JSONObject json = new JSONObject();
        List<String> contains = Arrays.asList("default", "henrik");
        json.put("contains", contains);

        System.out.println(json.toString());
        assertParse(json.toString(), "default:henrik");
    }

    @Test
    public void test() {
        assertParse("{'contains' : ['title', 'madonna']}",
                "title:madonna");
    }


    @Test
    public void testDottedFieldNames() {
        assertParse("{ 'contains' : ['my.nested.title', 'madonna']}",
                "my.nested.title:madonna");
    }



    @Test
    public void testOr() throws Exception {
        JSONObject json_two_or = new JSONObject();
        JSONObject json_three_or = new JSONObject();
        List<String> contains1 = Arrays.asList("title", "madonna");
        List<String> contains2 = Arrays.asList("title", "saint");
        List<String> contains3 = Arrays.asList("title", "angel");

        JSONObject contains_json1 = new JSONObject();
        JSONObject contains_json2 = new JSONObject();
        JSONObject contains_json3 = new JSONObject();
        contains_json1.put("contains", contains1);
        contains_json2.put("contains", contains2);
        contains_json3.put("contains", contains3);

        json_two_or.put("or", Arrays.asList(contains_json1, contains_json2));
        json_three_or.put("or", Arrays.asList(contains_json1, contains_json2, contains_json3));

        assertParse(json_two_or.toString(), "OR title:madonna title:saint");
        assertParse(json_three_or.toString(), "OR title:madonna title:saint title:angel");
    }

    @Test
    public void testAnd() throws Exception{
        JSONObject json_two_and = new JSONObject();
        JSONObject json_three_and = new JSONObject();
        List<String> contains1 = Arrays.asList("title", "madonna");
        List<String> contains2 = Arrays.asList("title", "saint");
        List<String> contains3 = Arrays.asList("title", "angel");

        JSONObject contains_json1 = new JSONObject();
        JSONObject contains_json2 = new JSONObject();
        JSONObject contains_json3 = new JSONObject();
        contains_json1.put("contains", contains1);
        contains_json2.put("contains", contains2);
        contains_json3.put("contains", contains3);

        json_two_and.put("and", Arrays.asList(contains_json1, contains_json2));
        json_three_and.put("and", Arrays.asList(contains_json1, contains_json2, contains_json3));

        assertParse(json_two_and.toString(), "AND title:madonna title:saint");
        assertParse(json_three_and.toString(), "AND title:madonna title:saint title:angel");
    }

    @Test
    public void testAndNot() throws JSONException {
        JSONObject json_and_not = new JSONObject();
        List<String> contains1 = Arrays.asList("title", "madonna");
        List<String> contains2 = Arrays.asList("title", "saint");

        JSONObject contains_json1 = new JSONObject();
        JSONObject contains_json2 = new JSONObject();
        contains_json1.put("contains", contains1);
        contains_json2.put("contains", contains2);

        json_and_not.put("and_not", Arrays.asList(contains_json1, contains_json2));

        System.out.println(json_and_not.toString());
        assertParse(json_and_not.toString(),
                "+title:madonna -title:saint");
    }


    @Test
    public void testLessThan() throws JSONException {
        JSONObject range_json = new JSONObject();
        JSONObject operators = new JSONObject();
        operators.put("<", 500);

        List<Object> range = Arrays.asList("price", operators);

        range_json.put("range", range);

        System.out.println(range_json.toString());
        assertParse(range_json.toString(),
                "price:<500");
    }

    @Test
    public void testGreaterThan() throws JSONException {
        JSONObject range_json = new JSONObject();
        JSONObject operators = new JSONObject();
        operators.put(">", 500);

        List<Object> range = Arrays.asList("price", operators);

        range_json.put("range", range);

        System.out.println(range_json.toString());
        assertParse(range_json.toString(),
                "price:>500");
    }


    @Test
    public void testLessThanOrEqual() throws JSONException {
        JSONObject range_json = new JSONObject();
        JSONObject operators = new JSONObject();
        operators.put("<=", 500);

        List<Object> range = Arrays.asList("price", operators);

        range_json.put("range", range);

        System.out.println(range_json.toString());
        assertParse(range_json.toString(),
                "price:[;500]");
    }

    @Test
    public void testGreaterThanOrEqual() throws JSONException {
        JSONObject range_json = new JSONObject();
        JSONObject operators = new JSONObject();
        operators.put(">=", 500);

        List<Object> range = Arrays.asList("price", operators);

        range_json.put("range", range);

        System.out.println(range_json.toString());
        assertParse(range_json.toString(),
                "price:[500;]");
    }

    @Test
    public void testEquality() throws JSONException {
        JSONObject range_json = new JSONObject();
        JSONObject operators = new JSONObject();
        operators.put("=", 500);

        List<Object> range = Arrays.asList("price", operators);

        range_json.put("range", range);

        System.out.println(range_json.toString());
        assertParse(range_json.toString(),
                "price:500");
    }

    @Test
    public void testNegativeLessThan() throws JSONException {
        JSONObject range_json = new JSONObject();
        JSONObject operators = new JSONObject();
        operators.put("<", -500);

        List<Object> range = Arrays.asList("price", operators);

        range_json.put("range", range);

        System.out.println(range_json.toString());
        assertParse(range_json.toString(),
                "price:<-500");
    }

    @Test
    public void testNegativeGreaterThan() throws JSONException {
        JSONObject range_json = new JSONObject();
        JSONObject operators = new JSONObject();
        operators.put(">", -500);

        List<Object> range = Arrays.asList("price", operators);

        range_json.put("range", range);

        System.out.println(range_json.toString());
        assertParse(range_json.toString(),
                "price:>-500");
    }

    @Test
    public void testNegativeLessThanOrEqual() throws JSONException {
        JSONObject range_json = new JSONObject();
        JSONObject operators = new JSONObject();
        operators.put("<=", -500);

        List<Object> range = Arrays.asList("price", operators);

        range_json.put("range", range);

        System.out.println(range_json.toString());
        assertParse(range_json.toString(),
                "price:[;-500]");
    }

    @Test
    public void testNegativeGreaterThanOrEqual() throws JSONException {
        JSONObject range_json = new JSONObject();
        JSONObject operators = new JSONObject();
        operators.put(">=", -500);

        List<Object> range = Arrays.asList("price", operators);

        range_json.put("range", range);

        System.out.println(range_json.toString());
        assertParse(range_json.toString(),
                "price:[-500;]");
    }

    @Test
    public void testNegativeEquality() throws JSONException {
        JSONObject range_json = new JSONObject();
        JSONObject operators = new JSONObject();
        operators.put("=", -500);

        List<Object> range = Arrays.asList("price", operators);

        range_json.put("range", range);

        System.out.println(range_json.toString());
        assertParse(range_json.toString(),
                "price:-500");
    }

    @Test
    public void testAnnotatedLessThan() {
        String jsonString = "{ \"range\": { \"children\" : [\"price\", {\"<\" : -500}], \"attributes\" : {\"filter\" : true} } }";
        System.out.println(jsonString);

        assertParse(jsonString, "|price:<-500");
    }

    @Test
    public void testAnnotatedGreaterThan() {
        String jsonString = "{ \"range\": { \"children\" : [\"price\", {\">\" : 500}], \"attributes\" : {\"filter\" : true} } }";
        System.out.println(jsonString);

        assertParse(jsonString, "|price:>500");
    }

    @Test
    public void testAnnotatedLessThanOrEqual() {
        String jsonString = "{ \"range\": { \"children\" : [\"price\", {\"<=\" : -500}], \"attributes\" : {\"filter\" : true} } }";
        System.out.println(jsonString);

        assertParse(jsonString, "|price:[;-500]");
    }

    @Test
    public void testAnnotatedGreaterThanOrEqual() {
        String jsonString = "{ \"range\": { \"children\" : [\"price\", {\">=\" : 500}], \"attributes\" : {\"filter\" : true} } }";
        System.out.println(jsonString);

        assertParse(jsonString, "|price:[500;]");
    }


    @Test
    public void testAnnotatedEquality() {
        String jsonString = "{ \"range\": { \"children\" : [\"price\", {\"=\" : -500}], \"attributes\" : {\"filter\" : true} } }";
        System.out.println(jsonString);

        assertParse(jsonString, "|price:-500");
    }

    @Test
    public void testTermAnnotations() {
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
    public void testSameElement() {
        //System.out.println("{ \"contains\": [ \"baz\", {\"sameElement\" : [ { \"contains\" : [\"f1\", \"a\"] }, { \"contains\" : [\"f2\", \"b\"] } ]} ] }");
       // assertParse("{ \"contains\": [ \"baz\", {\"sameElement\" : [ { \"contains\" : [\"f1\", \"a\"] }, { \"contains\" : [\"f2\", \"b\"] } ]} ] }",
                //"baz:{f1:a f2:b}");
        //assertParse("select foo from bar where baz contains sameElement(f1 contains \"a\", f2 = 10);",
        //        "baz:{f1:a f2:10}");
        //assertParse("select foo from bar where baz contains sameElement(key contains \"a\", value.f2 = 10);",
        //        "baz:{key:a value.f2:10}");
    }












    /** Other methods */
    private WordItem getRootWord(String yqlQuery) {
        Item root = parseWhere(yqlQuery).getRoot();
        assertTrue(root instanceof WordItem);
        return (WordItem)root;
    }












    /** Assert-methods */
    private void assertParse(String where, String expectedQueryTree) {
        String queryTree = parseWhere(where).toString();
        System.out.println(queryTree);
        assertEquals(expectedQueryTree, queryTree);
    }


    /** Parse-methods*/

    private QueryTree parseWhere(String where) {
        Select select = new Select(where, "");

        return parser.parse(new Parsable().setSelect(select));
    }

    private QueryTree parseGrouping(String grouping) {
        Select select = new Select("", grouping);

        return parser.parse(new Parsable().setSelect(select));
    }

    private QueryTree parse(String where, String grouping) {
        Select select = new Select(where, grouping);

        return parser.parse(new Parsable().setSelect(select));
    }





}
