// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.yql.YqlParser;
import com.yahoo.vespa.config.search.AttributesConfig.Attribute;
import com.yahoo.vespa.config.search.AttributesConfig;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author alexeyche
 */
public class ValidateFuzzySearcherTestCase {
    ValidateFuzzySearcher searcher;

    List<String> attributes;

    public ValidateFuzzySearcherTestCase() {
        int i = 0;
        attributes = new ArrayList<>();
        StringBuilder attributeConfig = new StringBuilder();
        for (Attribute.Datatype.Enum attr: Attribute.Datatype.Enum.values()) {
            for (Attribute.Collectiontype.Enum ctype: Attribute.Collectiontype.Enum.values()) {
                String attributeName = attr.name().toLowerCase() + "_" + ctype.name().toLowerCase();

                attributeConfig.append("attribute[" + i + "].name ");
                attributeConfig.append(attributeName);
                attributeConfig.append("\n");

                attributeConfig.append("attribute[" + i + "].datatype ");
                attributeConfig.append(attr.name());
                attributeConfig.append("\n");

                attributeConfig.append("attribute[" + i + "].collectiontype ");
                attributeConfig.append(ctype.name());
                attributeConfig.append("\n");

                i += 1;
                attributes.add(attributeName);
            }
        }

        searcher = new ValidateFuzzySearcher(ConfigGetter.getConfig(
                AttributesConfig.class,
                "raw: " +
                        "attribute[" + attributes.size() + "]\n" +
                        attributeConfig));
    }

    private String makeQuery(String attribute, String query, int maxEditDistance, int prefixLength) {
        return "select * from sources * where " + attribute +
                " contains ({maxEditDistance:" + maxEditDistance + ", prefixLength:" + prefixLength +"}" +
                "fuzzy(\"" + query + "\"))";
    }

    private String makeQuery(String attribute, String query) {
        return makeQuery(attribute, query, 2, 0);
    }


    @Test
    public void testQueriesToAllAttributes() {
        final Set<String> validAttributes = Set.of("string_single", "string_array", "string_weightedset");

        for (String attribute: attributes) {
            String q = makeQuery(attribute, "fuzzy");
            Result r = doSearch(searcher, q);
            if (validAttributes.contains(attribute)) {
                assertNull(r.hits().getError());
            } else {
                assertErrMsg("FUZZY(fuzzy,2,0) " + attribute + ":fuzzy field is not a string attribute", r);
            }
        }
    }

    @Test
    public void testInvalidEmptyStringQuery() {
        String q = makeQuery("string_single", "");
        Result r = doSearch(searcher, q);
        assertErrMsg("FUZZY(,2,0) string_single: fuzzy query must be non-empty", r);
    }

    @Test
    public void testInvalidQueryWrongMaxEditDistance() {
        String q = makeQuery("string_single", "fuzzy", -1, 0);
        Result r = doSearch(searcher, q);
        assertErrMsg("FUZZY(fuzzy,-1,0) string_single:fuzzy has invalid maxEditDistance -1: Must be >= 0", r);
    }

    @Test
    public void testInvalidQueryWrongPrefixLength() {
        String q = makeQuery("string_single", "fuzzy", 2, -1);
        Result r = doSearch(searcher, q);
        assertErrMsg("FUZZY(fuzzy,2,-1) string_single:fuzzy has invalid prefixLength -1: Must be >= 0", r);
    }

    @Test
    public void testInvalidQueryWrongAttributeName() {
        String q = makeQuery("wrong_name", "fuzzy");
        Result r = doSearch(searcher, q);
        assertErrMsg("FUZZY(fuzzy,2,0) wrong_name:fuzzy field is not a string attribute", r);
    }

    private static void assertErrMsg(String message, Result r) {
        assertEquals(ErrorMessage.createIllegalQuery(message), r.hits().getError());
    }

    private static Result doSearch(ValidateFuzzySearcher searcher, String yqlQuery) {
        QueryTree queryTree = new YqlParser(new ParserEnvironment()).parse(new Parsable().setQuery(yqlQuery));
        Query query = new Query();
        query.getModel().getQueryTree().setRoot(queryTree.getRoot());
        SearchDefinition searchDefinition = new SearchDefinition("document");
        IndexFacts indexFacts = new IndexFacts(new IndexModel(searchDefinition));
        return new Execution(searcher, Execution.Context.createContextStub(indexFacts)).search(query);
    }
}
