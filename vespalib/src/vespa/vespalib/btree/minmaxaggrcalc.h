// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "minmaxaggregated.h"

namespace vespalib::btree {

class MinMaxAggrCalc
{
public:
    constexpr MinMaxAggrCalc() = default;
    constexpr static bool hasAggregated() { return true; }
    constexpr static bool aggregate_over_values() { return true; }
    static int32_t getVal(int32_t val) { return val; }
    static void add(MinMaxAggregated &a, int32_t val) { a.add(val); }
    static void add(MinMaxAggregated &a, const MinMaxAggregated &ca) { a.add(ca); }
    static void add(MinMaxAggregated &a, const MinMaxAggregated &oldca, const MinMaxAggregated &ca) { a.add(oldca, ca); }

    /* Returns true if recalculation is needed */
    static bool
    remove(MinMaxAggregated &a, int32_t val)
    {
        return a.remove(val);
    }

    /* Returns true if recalculation is needed */
    static bool
    remove(MinMaxAggregated &a, const MinMaxAggregated &oldca,
           const MinMaxAggregated &ca)
    {
        return a.remove(oldca, ca);
    }

    /* Returns true if recalculation is needed */
    static bool
    update(MinMaxAggregated &a, int32_t oldVal, int32_t val)
    {
        return a.update(oldVal, val);
    }

    /* Returns true if recalculation is needed */
    static bool
    update(MinMaxAggregated &a, const MinMaxAggregated &oldca,
           const MinMaxAggregated &ca)
    {
        return a.update(oldca, ca);
    }
};

}
