// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform.test;

import com.google.common.collect.ImmutableList;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.search.Query;
import com.yahoo.prelude.querytransform.LiteralBoostSearcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.test.QueryTestCase;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the complete field match query transformer
 *
 * @author Steinar Knutsen
 */
public class LiteralBoostSearcherTestCase {

    @Test
    void testSimpleQueryWithBoost() {
        assertEquals("RANK (WEAKAND(100) abc) default_literal:abc",
                transformQuery("?query=abc&source=cluster1&restrict=type1"));
    }

    @Test
    void testSimpleQueryNoBoost() {
        assertEquals("WEAKAND(100) abc",
                transformQuery("?query=abc&source=cluster1&restrict=type2"));
    }

    @Test
    void testQueryWithExplicitIndex() {
        assertEquals("RANK (WEAKAND(100) absolute:abc) absolute_literal:abc",
                transformQuery("?query=absolute:abc&source=cluster1&restrict=type1"));
    }

    @Test
    void testQueryWithExplicitIndexNoBoost() {
        assertEquals("WEAKAND(100) absolute:abc",
                transformQuery("?query=absolute:abc&source=cluster1&restrict=type2"));
    }

    @Test
    void testQueryWithNegativeBranch() {
        assertEquals("RANK (+(AND abc def) -ghi) " +
                "default_literal:abc default_literal:def",
                transformQuery("?query=abc and def andnot ghi&type=adv&source=cluster1&restrict=type1"));
    }

    @Test
    void testJumbledQuery() {
        assertEquals
                ("RANK (OR (+(OR abc def) -ghi) jkl) " +
                        "default_literal:abc default_literal:def default_literal:jkl",
                        transformQuery("?query=abc or def andnot ghi or jkl&type=adv&source=cluster1&restrict=type1"));
    }

    @Test
    void testTermindexQuery() {
        assertEquals("RANK (+(WEAKAND(100) a b d) -c) default_literal:a " +
                "default_literal:b default_literal:d",
                transformQuery("?query=a b -c d&source=cluster1&restrict=type1"));
    }

    @Test
    void testQueryWithoutBoost() {
        assertEquals("RANK (AND nonexistant a nonexistant b) default_literal:nonexistant default_literal:a default_literal:nonexistant default_literal:b",
                transformQuery("?query=nonexistant:a nonexistant:b&source=cluster1&restrict=type1&type=all"));
    }

    private String transformQuery(String rawQuery) {
        Query query = new Query(QueryTestCase.httpEncode(rawQuery));
        new Execution(new LiteralBoostSearcher(), Execution.Context.createContextStub(createIndexFacts())).search(query);
        return query.getModel().getQueryTree().getRoot().toString();
    }

    private IndexFacts createIndexFacts() {
        Map<String, List<String>> clusters = new LinkedHashMap<>();
        clusters.put("cluster1", Arrays.asList("type1", "type2", "type3"));
        clusters.put("cluster2", Arrays.asList("type4", "type5"));
        Collection<SearchDefinition> searchDefs = ImmutableList.of(
                createSearchDefinitionWithFields("type1", true),
                createSearchDefinitionWithFields("type2", false),
                new SearchDefinition("type3"),
                new SearchDefinition("type4"),
                new SearchDefinition("type5"));
        return new IndexFacts(new IndexModel(clusters, searchDefs));
    }

    private SearchDefinition createSearchDefinitionWithFields(String name, boolean literalBoost) {
        SearchDefinition type = new SearchDefinition(name);
        Index defaultIndex = new Index("default");
        defaultIndex.setLiteralBoost(literalBoost);
        type.addIndex(defaultIndex);
        Index absoluteIndex = new Index("absolute");
        absoluteIndex.setLiteralBoost(literalBoost);
        type.addIndex(absoluteIndex);
        return type;
    }

}
