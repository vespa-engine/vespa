// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.HasIndexItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.ToolBox;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;

import static com.yahoo.search.grouping.GroupingQueryParser.SELECT_PARAMETER_PARSING;

/**
 * Validation of query operators against the schema which is searched
 *
 * @author bratseth
 */
@After(SELECT_PARAMETER_PARSING)
@Before(PhaseNames.BACKEND)
public class QueryValidator extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {
        IndexFacts.Session session = execution.context().getIndexFacts().newSession(query);
        ToolBox.visit(new ItemValidator(session), query.getModel().getQueryTree().getRoot());
        return execution.search(query);
    }

    private static class ItemValidator extends ToolBox.QueryVisitor {

        IndexFacts.Session session;

        public ItemValidator(IndexFacts.Session session) {
            this.session = session;
        }

        @Override
        public boolean visit(Item item) {
            if (item instanceof HasIndexItem) {
                String indexName = ((HasIndexItem)item).getIndexName();
                if (session.getIndex(indexName).isTensor())
                    throw new IllegalArgumentException("Cannot search '" + indexName + "': It is a tensor field");
            }
            return true;
        }

        @Override
        public void onExit() { }

    }

}
