// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "predicate_interval.h"
#include <vespa/vespalib/data/memory.h>
#include <climits>
#include <vector>
#include <unordered_map>

namespace vespalib::slime { struct Inspector; }

namespace search::predicate {

struct RangeFeature {
    vespalib::Memory label;
    int64_t from;
    int64_t to;
};

constexpr uint32_t MIN_INTERVAL = 0x0001;
constexpr uint32_t MAX_INTERVAL = 0xffff;

struct PredicateTreeAnnotations {
    PredicateTreeAnnotations(uint32_t mf=0, uint16_t ir=MAX_INTERVAL);
    ~PredicateTreeAnnotations();
    uint32_t min_feature;
    uint16_t interval_range;
    std::unordered_map<uint64_t, std::vector<Interval>> interval_map;
    std::unordered_map<uint64_t, std::vector<IntervalWithBounds>> bounds_map;

    std::vector<uint64_t> features;
    std::vector<RangeFeature> range_features;
};

/**
 * Annotates a predicate document, represented by a slime object, with
 * intervals used for matching with the interval algorithm.
 */
struct PredicateTreeAnnotator {
    static void annotate(const vespalib::slime::Inspector &in,
                         PredicateTreeAnnotations &result,
                         int64_t lower_bound=LLONG_MIN,
                         int64_t upper_bound=LLONG_MAX);
};

}  // namespace predicate
