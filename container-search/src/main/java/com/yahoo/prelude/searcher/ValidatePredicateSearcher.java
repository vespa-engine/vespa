// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher;

import java.util.Optional;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.PredicateQueryItem;
import com.yahoo.prelude.query.ToolBox;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.querytransform.BooleanSearcher;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;

import java.util.Collection;

/**
 * Checks that predicate queries don't use values outside the defined upper/lower bounds.
 *
 * @author <a href="mailto:magnarn@yahoo-inc.com">Magnar Nedland</a>
 */
@After(BooleanSearcher.PREDICATE)
public class ValidatePredicateSearcher extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {
        Optional<ErrorMessage> e = validate(query, execution.context().getIndexFacts().newSession(query));
        if (e.isPresent()) {
            Result r = new Result(query);
            r.hits().addError(e.get());
            return r;
        }
        return execution.search(query);
    }

    private Optional<ErrorMessage> validate(Query query, IndexFacts.Session indexFacts) {
        ValidatePredicateVisitor visitor = new ValidatePredicateVisitor(indexFacts);
        ToolBox.visit(visitor, query.getModel().getQueryTree().getRoot());
        return visitor.errorMessage;
    }

    private static class ValidatePredicateVisitor extends ToolBox.QueryVisitor {

        private final IndexFacts.Session indexFacts;

        public Optional<ErrorMessage> errorMessage = Optional.empty();

        public ValidatePredicateVisitor(IndexFacts.Session indexFacts) {
            this.indexFacts = indexFacts;
        }

        @Override
        public boolean visit(Item item) {
            if (item instanceof PredicateQueryItem) {
                visit((PredicateQueryItem) item);
            }
            return true;
        }

        private void visit(PredicateQueryItem item) {
            Index index = getIndexFromUnionOfDocumentTypes(item);
            for (PredicateQueryItem.RangeEntry entry : item.getRangeFeatures()) {
                long value = entry.getValue();
                if (value < index.getPredicateLowerBound() || value > index.getPredicateUpperBound()) {
                    errorMessage = Optional.of(ErrorMessage.createIllegalQuery(
                            String.format("%s=%d outside configured predicate bounds.", entry.getKey(), value)));
                }
            }
        }

        private Index getIndexFromUnionOfDocumentTypes(PredicateQueryItem item) {
            return indexFacts.getIndex(item.getIndexName());
        }

        @Override
        public void onExit() {}
    }
}
