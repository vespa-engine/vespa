// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.searchers;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.prelude.query.ToolBox;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.vespa.GroupingExecutor;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.yolean.chain.Before;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Validates any SameElementItem query items.
 *
 * @author bratseth
 */
@Before(GroupingExecutor.COMPONENT_NAME) // Must happen before query.prepare()
public class ValidateSameElementSearcher extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {
        Optional<ErrorMessage> e = validate(query, execution);
        return e.isEmpty() ? execution.search(query) : new Result(query, e.get());
    }

    private Optional<ErrorMessage> validate(Query query, Execution execution) {
        SameElementVisitor visitor = new SameElementVisitor(query, execution);
        ToolBox.visit(visitor, query.getModel().getQueryTree().getRoot());
        return visitor.errorMessage;
    }

    private static class SameElementVisitor extends ToolBox.QueryVisitor {

        public Optional<ErrorMessage> errorMessage = Optional.empty();

        private final Query query;
        private final Execution execution;

        public SameElementVisitor(Query query, Execution execution) {
            this.query = query;
            this.execution = execution;
        }

        @Override
        public boolean visit(Item item) {
            if (item instanceof SameElementItem sameElement) {
                String error = ensureValid(sameElement);
                if (error != null)
                    errorMessage = Optional.of(ErrorMessage.createIllegalQuery(error));
            }
            return true;
        }

        /**
         * Checks, and if necessary, makes this item valid.
         *
         * @return an error message if it is invalid, null if valid
         */
        private String ensureValid(SameElementItem sameItem) {
            if (sameItem.items().stream().noneMatch(child -> child instanceof AndItem)) return null; // shortcut

            List<Item> flattened = flattenedItems(sameItem);
            removeItems(sameItem);
            flattened.forEach(item -> sameItem.addItem(item));
            return null;
        }

        private List<Item> flattenedItems(SameElementItem sameItem) {
            List<Item> flattened = new ArrayList<>();
            for (Item child : sameItem.items()) {
                if (child instanceof AndItem and)
                    flattened.addAll(and.items());
                else
                    flattened.add(child);
            }
            return flattened;
        }

        private void removeItems(SameElementItem sameItem) {
            for (int i = sameItem.getItemCount() - 1; i >= 0; i--)
                sameItem.removeItem(i);
        }

    }

}
