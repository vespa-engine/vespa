// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

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
        var env = new ParserEnvironment();
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

    /** Asserts parity using a where-clause; automatically wraps the YQL in {@code select * from sources * where ...} */
    private void assertWhereParity(String yqlWhereClause, String jsonWhere) {
        assertParity("select * from sources * where " + yqlWhereClause, jsonWhere);
    }

    /** Asserts parity using a full YQL query string and a JSON where-clause (single quotes are replaced with double quotes). */
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

}
