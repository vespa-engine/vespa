// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/** Counts distinct nonempty expression values across the inputs. Returns count and exactness fields. */
public class UniqueCountAggregator extends AggregatorNode {

    public UniqueCountAggregator(GroupingExpression expression) {
        this(null, null, expression);
    }

    private UniqueCountAggregator(String label, Integer level, GroupingExpression expression) {
        super("uniquecount", label, level, expression);
    }

    @Override
    public UniqueCountAggregator copy() {
        return new UniqueCountAggregator(getLabel(), getLevelOrNull(), getExpression().copy());
    }

}
