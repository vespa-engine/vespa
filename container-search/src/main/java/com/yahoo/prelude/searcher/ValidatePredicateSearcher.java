// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.PredicateQueryItem;
import com.yahoo.prelude.query.SimpleIndexedItem;
import com.yahoo.prelude.query.ToolBox;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.querytransform.BooleanSearcher;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that predicate queries don't use values outside the defined upper/lower bounds.
 *
 * @author Magnar Nedland
 */
@After(BooleanSearcher.PREDICATE)
public class ValidatePredicateSearcher extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {
        List<ErrorMessage> errorMessages = validate(query, execution.context().getIndexFacts().newSession(query));
        if (!errorMessages.isEmpty()) {
            Result r = new Result(query);
            errorMessages.forEach(msg -> r.hits().addError(msg));
            return r;
        }
        return execution.search(query);
    }

    private List<ErrorMessage> validate(Query query, IndexFacts.Session indexFacts) {
        ValidatePredicateVisitor visitor = new ValidatePredicateVisitor(indexFacts);
        ToolBox.visit(visitor, query.getModel().getQueryTree().getRoot());
        return visitor.errorMessages;
    }

    private static class ValidatePredicateVisitor extends ToolBox.QueryVisitor {

        private final IndexFacts.Session indexFacts;

        final List<ErrorMessage> errorMessages = new ArrayList<>();

        public ValidatePredicateVisitor(IndexFacts.Session indexFacts) {
            this.indexFacts = indexFacts;
        }

        @Override
        public boolean visit(Item item) {
            if (item instanceof PredicateQueryItem) {
                visit((PredicateQueryItem) item);
            }
            if (item instanceof SimpleIndexedItem) {
                visit((SimpleIndexedItem) item);
            }
            return true;
        }

        private void visit(PredicateQueryItem item) {
            Index index = getIndexFromUnionOfDocumentTypes(item.getIndexName());
            if (!index.isPredicate()) {
                errorMessages.add(ErrorMessage.createIllegalQuery(String.format("Index '%s' is not a predicate attribute.", index.getName())));
            }
            for (PredicateQueryItem.RangeEntry entry : item.getRangeFeatures()) {
                long value = entry.getValue();
                if (value < index.getPredicateLowerBound() || value > index.getPredicateUpperBound()) {
                    errorMessages.add(
                            ErrorMessage.createIllegalQuery(String.format("%s=%d outside configured predicate bounds.", entry.getKey(), value)));
                }
            }
        }

        private void visit(SimpleIndexedItem item) {
            String indexName = item.getIndexName();
            Index index = getIndexFromUnionOfDocumentTypes(indexName);
            if (index.isPredicate()) {
                errorMessages.add(
                        ErrorMessage.createIllegalQuery(String.format("Index '%s' is predicate attribute and can only be used in conjunction with a predicate query operator.", indexName)));
            }
        }

        private Index getIndexFromUnionOfDocumentTypes(String indexName) {
            return indexFacts.getIndex(indexName);
        }

        @Override
        public void onExit() {}
    }

}
