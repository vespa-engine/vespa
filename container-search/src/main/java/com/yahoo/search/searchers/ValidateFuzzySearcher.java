// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers;

import com.yahoo.prelude.query.FuzzyItem;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.ToolBox;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.vespa.GroupingExecutor;
import com.yahoo.search.query.ranking.RankProperties;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.yolean.chain.Before;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Validates any FuzzyItem query items.
 *
 * @author alexeyche
 */
@Before(GroupingExecutor.COMPONENT_NAME) // Must happen before query.prepare()
public class ValidateFuzzySearcher extends Searcher {

    public ValidateFuzzySearcher() {
    }

    @Override
    public Result search(Query query, Execution execution) {
        Optional<ErrorMessage> e = validate(query, execution.context().getIndexFacts().newSession(query));
        return e.isEmpty() ? execution.search(query) : new Result(query, e.get());
    }

    private Optional<ErrorMessage> validate(Query query, IndexFacts.Session indexFacts) {
        FuzzyVisitor visitor = new FuzzyVisitor(indexFacts);
        ToolBox.visit(visitor, query.getModel().getQueryTree().getRoot());
        return visitor.errorMessage;
    }

    private static class FuzzyVisitor extends ToolBox.QueryVisitor {

        public Optional<ErrorMessage> errorMessage = Optional.empty();

        private final IndexFacts.Session indexFacts;

        public FuzzyVisitor(IndexFacts.Session indexFacts) {
            this.indexFacts = indexFacts;
        }

        @Override
        public boolean visit(Item item) {
            if (item instanceof FuzzyItem) {
                String error = validate((FuzzyItem)item);
                if (error != null)
                    errorMessage = Optional.of(ErrorMessage.createIllegalQuery(error));
            }
            return true;
        }

        /** Returns an error message if this is invalid, or null if it is valid */
        private String validate(FuzzyItem item) {
            String indexName = item.getIndexName();
            Index index = getIndexFromUnionOfDocumentTypes(indexName);
            if (!index.isAttribute() || !index.isString()) {
                return item + " field is not a string attribute";
            }
            if (item.getPrefixLength() < 0) {
                return item + " has invalid prefixLength " + item.getPrefixLength() + ": Must be >= 0";
            }
            if (item.getMaxEditDistance() < 0) {
                return item + " has invalid maxEditDistance " + item.getMaxEditDistance() + ": Must be >= 0";
            }
            if (item.stringValue().isEmpty()) {
                return item + " fuzzy query must be non-empty";
            }
            return null;
        }

        private Index getIndexFromUnionOfDocumentTypes(String indexName) {
            return indexFacts.getIndex(indexName);
        }

        @Override
        public void onExit() {}

    }

}
