// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

/**
 * FIXME: Really ugly hack to force class loading for subclasses of Identifiable.
 * This should be fixed by doing the all class registration in a single place (similar to how its done in C++).
 */
public class ForceLoad {

    static {
        String pkg = "com.yahoo.searchlib.aggregation";
        String[] classes = {
                "XorAggregationResult",
                "SumAggregationResult",
                "Group",
                "HitsAggregationResult",
                "AggregationResult",
                "FS4Hit",
                "VdsHit",
                "Grouping",
                "Hit",
                "MinAggregationResult",
                "GroupingLevel",
                "MaxAggregationResult",
                "CountAggregationResult",
                "AverageAggregationResult",
                "ExpressionCountAggregationResult",
                "hll.SparseSketch",
                "hll.NormalSketch"
        };
        com.yahoo.system.ForceLoad.forceLoad(pkg, classes, ForceLoad.class.getClassLoader());
    }

    public static boolean forceLoad() {
        return true;
    }

}
