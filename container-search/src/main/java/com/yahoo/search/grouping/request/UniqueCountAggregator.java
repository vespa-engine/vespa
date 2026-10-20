// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.yahoo.api.annotations.Beta;

/**
 * Counts distinct nonempty expression values across the inputs. The result has a count, and "exact", which is true
 * when the count is not a HyperLogLog estimate. Values are counted by 32-bit hashes, so two different values
 * with the same hash are counted once even then.
 */
@Beta
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
