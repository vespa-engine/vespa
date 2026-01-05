// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.searchers;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.EquivItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NearItem;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.RankItem;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.prelude.query.ToolBox;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.vespa.GroupingExecutor;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.yolean.chain.Before;

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

        private boolean inSameElement = false;

        public SameElementVisitor(Query query, Execution execution) {
        }

        @Override
        public boolean visit(Item item) {
            if (inSameElement && ! isValidSameItemChild(item)) {
                errorMessage = Optional.of(ErrorMessage.createIllegalQuery("SameElementItem cannot contain '" + item + "'"));
                return false;
            }
            if (item instanceof SameElementItem)
                inSameElement = true;
            return true;
        }

        @Override
        public void onExit(Item item) {
            if (item instanceof SameElementItem)
                inSameElement = false;
        }


            /** Returns an error message if it is invalid, null if valid. */
        private String valid(SameElementItem sameItem) {
            for (Item child : sameItem.items()) {
                if ( ! isValidSameItemChild(child))
                    return "SameElementItem cannot contain '" + child + "'";
            }
            return null;
        }

        private boolean isValidSameItemChild(Item child) {
            if ( ! (child instanceof CompositeItem)) return true;
            if (child instanceof AndItem) return true;
            if (child instanceof OrItem) return true;
            if (child instanceof NearItem) return true;
            if (child instanceof EquivItem) return true;
            if (child instanceof RankItem) return true;
            if (child instanceof PhraseItem) return true;
            if (child instanceof NotItem) return true;
            return false;
        }

    }

}
