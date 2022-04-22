// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers;

import com.yahoo.prelude.query.FuzzyItem;
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

    private final Set<String> validAttributes = new HashSet<>();

    public ValidateFuzzySearcher(AttributesConfig attributesConfig) {
        for (AttributesConfig.Attribute a : attributesConfig.attribute()) {
            if (a.datatype() == AttributesConfig.Attribute.Datatype.STRING) {
                validAttributes.add(a.name());
            }
        }
    }

    @Override
    public Result search(Query query, Execution execution) {
        Optional<ErrorMessage> e = validate(query);
        return e.isEmpty() ? execution.search(query) : new Result(query, e.get());
    }

    private Optional<ErrorMessage> validate(Query query) {
        FuzzyVisitor visitor = new FuzzyVisitor(query.getRanking().getProperties(), validAttributes, query);
        ToolBox.visit(visitor, query.getModel().getQueryTree().getRoot());
        return visitor.errorMessage;
    }

    private static class FuzzyVisitor extends ToolBox.QueryVisitor {

        public Optional<ErrorMessage> errorMessage = Optional.empty();

        private final Set<String> validAttributes;
        private final Query query;

        public FuzzyVisitor(RankProperties rankProperties, Set<String> validAttributes, Query query) {
            this.validAttributes = validAttributes;
            this.query = query;
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
            if (!validAttributes.contains(item.getIndexName())) {
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

        @Override
        public void onExit() {}

    }

}
