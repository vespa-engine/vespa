// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher.test;

import com.google.common.util.concurrent.MoreExecutors;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.prelude.searcher.ValidatePredicateSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.yql.YqlParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Magnar Nedland
 */
public class ValidatePredicateSearcherTestCase {

    @Test
    public void testValidQuery() {
        ValidatePredicateSearcher searcher = new ValidatePredicateSearcher();
        String q = "select * from sources * where predicate(predicate_field,0,{\"age\":20L});";
        Result r = doSearch(searcher, q, "predicate-bounds [0..99]");
        assertNull(r.hits().getError());
    }

    @Test
    public void testQueryOutOfBounds() {
        ValidatePredicateSearcher searcher = new ValidatePredicateSearcher();
        String q = "select * from sources * where predicate(predicate_field,0,{\"age\":200L});";
        Result r = doSearch(searcher, q, "predicate-bounds [0..99]");
        assertEquals(ErrorMessage.createIllegalQuery("age=200 outside configured predicate bounds."), r.hits().getError());
    }

    @Test
    public void queryFailsWhenPredicateFieldIsUsedInTermSearch() {
        ValidatePredicateSearcher searcher = new ValidatePredicateSearcher();
        String q = "select * from sources * where predicate_field CONTAINS \"true\";";
        Result r = doSearch(searcher, q, "predicate-bounds [0..99]");
        assertEquals(ErrorMessage.createIllegalQuery("Index 'predicate_field' is predicate attribute and can only be used in conjunction with a predicate query operator."), r.hits().getError());
    }

    private static Result doSearch(ValidatePredicateSearcher searcher, String yqlQuery, String command) {
        QueryTree queryTree = new YqlParser(new ParserEnvironment()).parse(new Parsable().setQuery(yqlQuery));
        Query query = new Query();
        query.getModel().getQueryTree().setRoot(queryTree.getRoot());

        SearchDefinition searchDefinition = new SearchDefinition("document");
        Index index = new Index("predicate_field");
        index.setPredicate(true);
        index.addCommand(command);
        searchDefinition.addIndex(index);
        IndexFacts indexFacts = new IndexFacts(new IndexModel(searchDefinition));
        Execution.Context context = new Execution.Context(null, indexFacts, null, new RendererRegistry(MoreExecutors.directExecutor()), new SimpleLinguistics());
        return new Execution(searcher, context).search(query);
    }

}
