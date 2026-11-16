// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "minmaxaggregated.h"

namespace vespalib::btree {

template <typename T> class MinMaxAggrCalcT {
public:
    using Aggregated = MinMaxAggregatedT<T>;

    constexpr MinMaxAggrCalcT() = default;
    constexpr static bool hasAggregated() { return true; }
    constexpr static bool aggregate_over_values() { return true; }
    static T getVal(T val) { return val; }
    static void add(Aggregated& a, T val) { a.add(val); }
    static void add(Aggregated& a, const Aggregated& ca) { a.add(ca); }
    static void add(Aggregated& a, const Aggregated& oldca, const Aggregated& ca) { a.add(oldca, ca); }

    /* Returns true if recalculation is needed */
    static bool remove(Aggregated& a, T val) { return a.remove(val); }

    /* Returns true if recalculation is needed */
    static bool remove(Aggregated& a, const Aggregated& oldca, const Aggregated& ca) { return a.remove(oldca, ca); }

    /* Returns true if recalculation is needed */
    static bool update(Aggregated& a, T oldVal, T val) { return a.update(oldVal, val); }

    /* Returns true if recalculation is needed */
    static bool update(Aggregated& a, const Aggregated& oldca, const Aggregated& ca) { return a.update(oldca, ca); }
};

using MinMaxAggrCalc = MinMaxAggrCalcT<int32_t>;
using FloatMinMaxAggrCalc = MinMaxAggrCalcT<float>;

} // namespace vespalib::btree
