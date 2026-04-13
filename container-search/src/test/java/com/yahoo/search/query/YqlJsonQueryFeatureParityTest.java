// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.search.Query;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.yql.VespaSerializer;
import com.yahoo.search.yql.YqlParser;
import com.yahoo.slime.SlimeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests feature parity between YQL and JSON query language ({@link SelectParser}).
 *
 * @author johsol
 */
public class YqlJsonQueryFeatureParityTest {

    private YqlParser yqlParser;
    private SelectParser selectParser;

    @BeforeEach
    void setup() {
        var env = new ParserEnvironment().setIndexFacts(createIndexFacts());
        yqlParser = new YqlParser(env);
        selectParser = new SelectParser(env);
    }

    @Test
    void testSimpleContains() {
        assertWhereParity(
                "title contains 'madonna'",
                "{ 'contains' : ['title', 'madonna'] }");
    }

    @Test
    void testAndQuery() {
        assertWhereParity(
                "title contains 'madonna' and body contains 'saint'",
                "{ 'and' : [ { 'contains' : ['title', 'madonna'] }, { 'contains' : ['body', 'saint'] } ] }");
    }

    @Test
    void testOrQuery() {
        assertWhereParity(
                "title contains 'madonna' or title contains 'saint'",
                "{ 'or' : [ { 'contains' : ['title', 'madonna'] }, { 'contains' : ['title', 'saint'] } ] }");
    }

    @Test
    void testEquals() {
        assertWhereParity("public = true", "{ 'equals' : ['public', true] }");
        assertWhereParity("public = true", "{ 'equals' : { 'field' : 'public', 'value' : true } }");
        assertWhereParity("public = 5", "{ 'equals' : ['public', 5] }");
        assertWhereParity("public = 5", "{ 'equals' : { 'field' : 'public', 'value' : 5 } }");
    }

    @Test
    void testNot() {
        assertWhereParity("!(title contains 'madonna')",
                "{ 'not' : { 'contains' : ['title', 'madonna'] } }");
    }

    @Test
    void testAndNot() {
        assertWhereParity("title contains 'madonna' and !(title contains 'saint')",
                "{ 'and_not' : [ { 'contains' : ['title', 'madonna'] }, { 'contains' : ['title', 'saint'] } ] }");
    }

    @Test
    void testRange() {
        assertWhereParity("range(price, 0L, 500L)",
                "{ 'range' : ['price', { '>=': 0, '<=': 500 }] }");
    }

    @Test
    void testIn() {
        assertWhereParity("field in (42, 22)",
                "{ 'in' : ['field', 42, 22] }");
        assertWhereParity("string in ('a', 'b')",
                "{ 'in' : ['string', 'a', 'b'] }");
    }

    @Test
    void testMatches() {
        assertWhereParity("artist matches 'a\\\\.b'",
                "{ 'matches' : ['artist', 'a\\\\.b'] }");
    }

    @Test
    void testPhrase() {
        assertWhereParity("baz contains phrase('a', 'b')",
                "{ 'contains' : ['baz', { 'phrase' : ['a', 'b'] }] }");
    }

    @Test
    void testNear() {
        assertWhereParity("description contains near({implicitTransforms: false}'a', {implicitTransforms: false}'b')",
                "{ 'contains' : ['description', { 'near' : ['a', 'b'] }] }");
    }

    @Test
    void testOnear() {
        assertWhereParity("description contains onear({implicitTransforms: false}'a', {implicitTransforms: false}'b')",
                "{ 'contains' : ['description', { 'onear' : ['a', 'b'] }] }");
    }

    @Test
    void testEquiv() {
        assertWhereParity("fieldName contains equiv('A', 'B')",
                "{ 'contains' : ['fieldName', { 'equiv' : ['A', 'B'] }] }");
    }

    @Test
    void testFuzzy() {
        assertWhereParity("baz contains fuzzy('a b')",
                "{ 'contains' : ['baz', { 'fuzzy' : ['a b'] }] }");
    }

    @Test
    void testSameElement() {
        // Struct field with explicit contains
        assertWhereParity("baz contains sameElement(f1 contains 'a', f2 contains 'b')",
                "{ 'contains' : ['baz', { 'sameElement' : [ { 'contains' : ['f1', 'a'] }, { 'contains' : ['f2', 'b'] } ] }] }");
        // Struct field with contains and range
        assertWhereParity("baz contains sameElement(f1 contains 'a', range(f2, 0L, 10L))",
                "{ 'contains' : ['baz', { 'sameElement' : [ { 'contains' : ['f1', 'a'] }, { 'range' : ['f2', { '>=': 0, '<=': 10 }] } ] }] }");
    }

    @Test
    void testSameElementShorthand() {
        // Shorthand: {"f1": "a"} is equivalent to {"contains": ["f1", "a"]}
        assertWhereParity("baz contains sameElement(f1 contains 'a', f2 contains 'b')",
                "{ 'contains' : ['baz', { 'sameElement' : [ { 'f1' : 'a', 'f2' : 'b' } ] }] }");
    }

    @Test
    void testSameElementWithAndOrChildren() {
        assertWhereParity("description contains sameElement('a' and 'b')",
                "{ 'contains' : ['description', { 'sameElement' : [ { 'and' : [ 'a', 'b' ] } ] }] }");
        assertWhereParity("description contains sameElement('a' or 'b')",
                "{ 'contains' : ['description', { 'sameElement' : [ { 'or' : [ 'a', 'b' ] } ] }] }");
    }

    @Test
    void testSameElementWithNear() {
        assertWhereParity("description contains sameElement({distance: 3}near({implicitTransforms: false}'a', {implicitTransforms: false}'b'))",
                "{ 'contains' : ['description', { 'sameElement' : [ { 'near' : { 'children' : ['a', 'b'], 'attributes' : { 'distance' : 3 } } } ] }] }");
    }

    @Test
    void testSameElementWithPhrase() {
        assertWhereParity("description contains sameElement(phrase('a', 'b'))",
                "{ 'contains' : ['description', { 'sameElement' : [ { 'phrase' : ['a', 'b'] } ] }] }");
    }

    @Test
    void testSameElementWithRank() {
        assertWhereParity("description contains sameElement(rank('a', 'b'))",
                "{ 'contains' : ['description', { 'sameElement' : [ { 'rank' : [ 'a', 'b' ] } ] }] }");
    }

    @Test
    void testRank() {
        assertWhereParity("rank(a contains 'A', b contains 'B')",
                "{ 'rank' : [ { 'contains' : ['a', 'A'] }, { 'contains' : ['b', 'B'] } ] }");
    }

    @Test
    void testWeakAnd() {
        assertWhereParity("weakAnd(a contains 'A', b contains 'B')",
                "{ 'weakAnd' : [ { 'contains' : ['a', 'A'] }, { 'contains' : ['b', 'B'] } ] }");
    }

    @Test
    void testWeakAndWithAnnotations() {
        assertWhereParity("{scoreThreshold: 41, totalTargetHits: 7}weakAnd(a contains 'A', b contains 'B')",
                "{ 'weakAnd' : { 'children' : [ { 'contains' : ['a', 'A'] }, { 'contains' : ['b', 'B'] } ], 'attributes' : { 'scoreThreshold': 41, 'totalTargetHits': 7 } } }");
    }

    @Test
    void testWand() {
        assertWhereParity("wand(description, {'a': 1, 'b': 2})",
                "{ 'wand' : ['description', { 'a': 1, 'b': 2 }] }");
    }

    @Test
    void testWandWithAnnotations() {
        assertWhereParity("[{'scoreThreshold': 13, 'totalTargetHits': 7}]wand(description, {'a': 1, 'b': 2})",
                "{ 'wand' : { 'children' : ['description', { 'a': 1, 'b': 2 }], 'attributes' : { 'scoreThreshold': 13, 'totalTargetHits': 7 } } }");
    }

    @Test
    void testWeightedSet() {
        assertWhereParity("weightedSet(description, {'a': 1, 'b': 2})",
                "{ 'weightedSet' : ['description', { 'a': 1, 'b': 2 }] }");
    }

    @Test
    void testDotProduct() {
        assertWhereParity("dotProduct(description, {'a': 1, 'b': 2})",
                "{ 'dotProduct' : ['description', { 'a': 1, 'b': 2 }] }");
    }

    @Test
    void testPredicate() {
        assertWhereParity("predicate(predicate_field, {'gender': 'male'}, {'age': 23})",
                "{ 'predicate' : ['predicate_field', { 'gender': 'male' }, { 'age': 23 }] }");
    }

    @Test
    void testGeoLocation() {
        assertWhereParity("geoLocation(workplace, 63.418417, 10.433033, '0.5 deg')",
                "{ 'geoLocation' : ['workplace', 63.418417, 10.433033, '0.5 deg'] }");
    }

    @Test
    void testGeoBoundingBox() {
        assertWhereParity("geoBoundingBox('workplace', -63.418, -10.433, 63.5, 10.5)",
                "{ 'geoBoundingBox' : ['workplace', -63.418, -10.433, 63.5, 10.5] }");
    }

    @Test
    void testNearestNeighbor() {
        assertWhereParity("nearestNeighbor(f1field, q2prop)",
                "{ 'nearestNeighbor' : ['f1field', 'q2prop'] }");
    }

    /** Asserts parity using a where-clause; automatically wraps the YQL in {@code select * from sources * where ...} */
    private void assertWhereParity(String yqlWhereClause, String jsonWhere) {
        assertParity("select * from sources * where " + yqlWhereClause, jsonWhere);
    }

    /** Asserts parity using a full YQL query string and a JSON where-clause. */
    private void assertParity(String yql, String jsonWhere) {
        var yqlTree = yqlParser.parse(new Parsable().setQuery(yql));
        var normalizedJson = SlimeUtils.toJson(SlimeUtils.jsonToSlime(jsonWhere));
        var selectTree = selectParser.parse(new Parsable().setSelect(new Select(normalizedJson, "", new Query())));

        var yqlQuery = new Query();
        yqlQuery.getModel().getQueryTree().setRoot(yqlTree.getRoot());
        var selectQuery = new Query();
        selectQuery.getModel().getQueryTree().setRoot(selectTree.getRoot());

        assertEquals(VespaSerializer.serialize(yqlQuery), VespaSerializer.serialize(selectQuery),
                "YQL and JSON select should produce the same query tree");
    }

    private static IndexFacts createIndexFacts() {
        SearchDefinition sd = new SearchDefinition("default");
        sd.addIndex(new Index("title"));
        sd.addIndex(new Index("body"));
        sd.addIndex(new Index("description"));
        sd.addIndex(new Index("baz"));
        sd.addIndex(new Index("baz.f1"));
        sd.addIndex(new Index("baz.f2"));
        sd.addIndex(new Index("fieldName"));
        sd.addIndex(new Index("artist"));
        sd.addIndex(new Index("a"));
        sd.addIndex(new Index("b"));
        sd.addIndex(new Index("c"));
        sd.addIndex(new Index("public"));
        sd.addIndex(new Index("predicate_field"));
        sd.addIndex(new Index("workplace"));
        sd.addIndex(new Index("f1field"));
        var price = new Index("price");
        price.setInteger(true);
        sd.addIndex(price);
        var field = new Index("field");
        field.setInteger(true);
        sd.addIndex(field);
        var string = new Index("string");
        string.setString(true);
        sd.addIndex(string);
        return new IndexFacts(new IndexModel(sd));
    }

}
