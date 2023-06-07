// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.HasIndexItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.PrefixItem;
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
        ToolBox.visit(new TermSearchValidator(session), query.getModel().getQueryTree().getRoot());
        ToolBox.visit(new PrefixSearchValidator(session), query.getModel().getQueryTree().getRoot());
        return execution.search(query);
    }

    private abstract static class TermValidator extends ToolBox.QueryVisitor {

        final IndexFacts.Session session;

        public TermValidator(IndexFacts.Session session) {
            this.session = session;
        }

        @Override
        public void onExit() { }

    }

    private static class TermSearchValidator extends TermValidator {

        public TermSearchValidator(IndexFacts.Session session) {
            super(session);
        }

        @Override
        public boolean visit(Item item) {
            if (item instanceof HasIndexItem indexItem) {
                if (session.getIndex(indexItem.getIndexName()).isTensor())
                    throw new IllegalArgumentException("Cannot search for terms in '" + indexItem.getIndexName() + "': It is a tensor field");
            }
            return true;
        }

    }

    private static class PrefixSearchValidator extends TermValidator {

        public PrefixSearchValidator(IndexFacts.Session session) {
            super(session);
        }

        @Override
        public boolean visit(Item item) {
            if (item instanceof PrefixItem prefixItem) {
                Index index = session.getIndex(prefixItem.getIndexName());
                if ( ! index.isAttribute())
                    throw new IllegalArgumentException("'" + prefixItem.getIndexName() + "' is not an attribute field: Prefix matching is not supported");
                if (index.isIndex()) // index overrides attribute
                    throw new IllegalArgumentException("'" + prefixItem.getIndexName() + "' is an index field: Prefix matching is not supported even when it is also an attribute");
            }
            return true;
        }

    }

}
