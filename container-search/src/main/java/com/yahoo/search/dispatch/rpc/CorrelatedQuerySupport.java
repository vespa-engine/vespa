// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.CorrelateByItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.search.Query;
import com.yahoo.search.grouping.vespa.GroupingExecutor;
import com.yahoo.searchlib.aggregation.DocumentCountAggregationResult;
import com.yahoo.searchlib.aggregation.UniqueCountAggregationResult;
import com.yahoo.searchlib.expression.CorrelatedPredicateNode;
import com.yahoo.searchlib.expression.OncePerDocumentPredicateNode;
import com.yahoo.vespa.objects.Identifiable;
import com.yahoo.vespa.objects.ObjectVisitor;

/** Detects wire types which older content nodes cannot deserialize safely. */
final class CorrelatedQuerySupport extends ObjectVisitor {
    private boolean required;

    static boolean requiredBy(Query query) {
        if (containsCorrelation(query.getModel().getQueryTree().getRoot())) return true;
        var visitor = new CorrelatedQuerySupport();
        for (var grouping : GroupingExecutor.getGroupingList(query)) visitor.visit("grouping", grouping);
        return visitor.required;
    }

    private static boolean containsCorrelation(Item item) {
        if (item instanceof CorrelateByItem) return true;
        if (item instanceof CompositeItem composite)
            for (var child : composite.items())
                if (containsCorrelation(child)) return true;
        return false;
    }

    @Override
    public void visit(String name, Object value) {
        if (required) return;
        if (value instanceof DocumentCountAggregationResult || value instanceof UniqueCountAggregationResult ||
            value instanceof CorrelatedPredicateNode || value instanceof OncePerDocumentPredicateNode) {
            required = true;
        } else if (value instanceof Identifiable identifiable) {
            identifiable.visitMembers(this);
        } else if (value instanceof Iterable<?> values) {
            for (var member : values) visit(name, member);
        }
    }

    @Override public void openStruct(String name, String type) { }
    @Override public void closeStruct() { }
}
