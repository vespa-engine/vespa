// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "noaggregated.h"

namespace vespalib::btree {

class NoAggrCalc
{
public:
    constexpr NoAggrCalc() = default;

    constexpr static bool
    hasAggregated()
    {
        return false;
    }

    constexpr static bool aggregate_over_values() { return true; }

    template <typename DataT>
    static inline int32_t
    getVal(const DataT &val)
    {
        (void) val;
        return 0;
    }

    static void
    add(NoAggregated &a, int32_t val)
    {
        (void) a;
        (void) val;
    }

    static void
    add(NoAggregated &a, const NoAggregated &ca)
    {
        (void) a;
        (void) ca;
    }

    static void
    add(NoAggregated &a,
        const NoAggregated &oldca,
        const NoAggregated &ca)
    {
        (void) a;
        (void) oldca;
        (void) ca;
    }

    /* Returns true if recalculation is needed */
    static bool
    remove(NoAggregated &a, int32_t val)
    {
        (void) a;
        (void) val;
        return false;
    }

    /* Returns true if recalculation is needed */
    static bool
    remove(NoAggregated &a, const NoAggregated &oldca, const NoAggregated &ca)
    {
        (void) a;
        (void) oldca;
        (void) ca;
        return false;
    }

    /* Returns true if recalculation is needed */
    static bool
    update(NoAggregated &a, int32_t oldVal, int32_t val)
    {
        (void) a;
        (void) oldVal;
        (void) val;
        return false;
    }

    /* Returns true if recalculation is needed */
    static bool
    update(NoAggregated &a, const NoAggregated &oldca, const NoAggregated &ca)
    {
        (void) a;
        (void) oldca;
        (void) ca;
        return false;
    }
};

}
