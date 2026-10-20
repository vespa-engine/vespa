// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.CorrelateByItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.Query;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.request.AndPredicate;
import com.yahoo.search.grouping.request.CorrelatedPredicate;
import com.yahoo.search.grouping.request.FilterExpression;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.grouping.request.NotPredicate;
import com.yahoo.search.grouping.request.OrPredicate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks that each correlated() grouping filter refers to exactly one correlateBy with its label in the query,
 * and only to variables which that correlateBy binds. The content nodes cannot evaluate other filters.
 */
class CorrelatedFilterValidator {

    private CorrelatedFilterValidator() { }

    /** Throws an IllegalInputException describing the first correlated() filter which does not match the query. */
    static void validate(Query query) {
        List<CorrelatedPredicate> filters = new ArrayList<>();
        for (GroupingRequest request : query.getSelect().getGrouping())
            collectFilters(request.getRootOperation(), filters);
        if (filters.isEmpty()) return;

        Map<String, List<CorrelateByItem>> correlations = new HashMap<>();
        collectCorrelations(query.getModel().getQueryTree(), correlations);
        for (CorrelatedPredicate filter : filters)
            validate(filter, correlations.getOrDefault(filter.getLabel(), List.of()));
    }

    private static void validate(CorrelatedPredicate filter, List<CorrelateByItem> correlations) {
        String label = filter.getLabel();
        if (correlations.isEmpty())
            throw new IllegalInputException("Grouping filter " + filter + " refers to label '" + label +
                                            "', but the query has no correlateBy with that label. " +
                                            "Label it as ({label:\"" + label + "\"}correlateBy(...))");
        if (correlations.size() > 1)
            throw new IllegalInputException("Grouping filter " + filter + " refers to label '" + label +
                                            "', which is used by " + correlations.size() +
                                            " correlateBy items in the query. Give each a different label");
        Set<String> bound = variables(correlations.get(0));
        for (String variable : filter.getBindings().keySet()) {
            if ( ! bound.contains(variable))
                throw new IllegalInputException("Grouping filter " + filter + " uses variable '" + variable +
                                                "', which the correlateBy labeled '" + label + "' does not bind. " +
                                                "It binds " + bound);
        }
    }

    private static Set<String> variables(CorrelateByItem correlation) {
        Set<String> variables = new TreeSet<>();
        for (int i = 0; i < correlation.getItemCount(); ++i)
            variables.addAll(correlation.getBindings(i).keySet());
        return variables;
    }

    private static void collectFilters(GroupingOperation operation, List<CorrelatedPredicate> filters) {
        if (operation.getFilterBy() != null)
            collectFilters(operation.getFilterBy(), filters);
        for (GroupingOperation child : operation.getChildren())
            collectFilters(child, filters);
    }

    private static void collectFilters(FilterExpression filter, List<CorrelatedPredicate> filters) {
        if (filter instanceof CorrelatedPredicate correlated)
            filters.add(correlated);
        else if (filter instanceof AndPredicate and)
            and.getArgs().forEach(arg -> collectFilters(arg, filters));
        else if (filter instanceof OrPredicate or)
            or.getArgs().forEach(arg -> collectFilters(arg, filters));
        else if (filter instanceof NotPredicate not)
            collectFilters(not.getExpression(), filters);
    }

    private static void collectCorrelations(Item item, Map<String, List<CorrelateByItem>> correlations) {
        if (item instanceof CorrelateByItem correlation && correlation.getLabel() != null)
            correlations.computeIfAbsent(correlation.getLabel(), __ -> new ArrayList<>()).add(correlation);
        if (item instanceof CompositeItem composite) {
            for (Item child : composite.items())
                collectCorrelations(child, correlations);
        }
    }

}
