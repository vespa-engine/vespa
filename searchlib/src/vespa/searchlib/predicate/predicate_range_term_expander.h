// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/issue.h>
#include <climits>

namespace search::predicate {

/**
 * Helper class for expanding a point in a predicate range query to
 * the hashed labels. Used by PredicateBlueprint.
 */
class PredicateRangeTermExpander {
    int _arity;
    uint16_t _max_positive_levels;
    uint16_t _max_negative_levels;
    int64_t _lower_bound;
    int64_t _upper_bound;

public:
    PredicateRangeTermExpander(int arity,
                               int64_t lower_bound = LLONG_MIN,
                               int64_t upper_bound = LLONG_MAX)
        : _arity(arity),
          _max_positive_levels(1),
          _max_negative_levels(1),
          _lower_bound(lower_bound),
          _upper_bound(upper_bound) {
        uint64_t t = _upper_bound;
        while ((t /= _arity) > 0) ++_max_positive_levels;
        t = uint64_t(0)-_lower_bound;
        while ((t /= _arity) > 0) ++_max_negative_levels;
    }

    template <typename Handler>
    void expand(const vespalib::string &key, int64_t value, Handler &handler);
};


/**
 * Handler must implement handleRange(string) and handleEdge(string, uint64_t).
 */
template <typename Handler>
void PredicateRangeTermExpander::expand(const vespalib::string &key, int64_t signed_value, Handler &handler) {
    if (signed_value < _lower_bound || signed_value > _upper_bound) {
        vespalib::Issue::report("predicate_range_term_expander: Search outside bounds should have been rejected by ValidatePredicateSearcher.");
        return;
    }
    char buffer[21 * 2 + 3 + key.size()];  // 2 numbers + punctuation + key
    int size;
    int prefix_size = sprintf(buffer, "%s=", key.c_str());
    bool negative = signed_value < 0;
    uint64_t value;
    int max_levels;
    if (negative) {
        value = uint64_t(0)-signed_value;
        buffer[prefix_size++] = '-';
        max_levels = _max_negative_levels;
    } else {
        value = signed_value;
        max_levels = _max_positive_levels;
    }

    int64_t edge_interval = (value / _arity) * _arity;
    size = sprintf(buffer + prefix_size, "%" PRIu64, edge_interval);
    handler.handleEdge(vespalib::stringref(buffer, prefix_size + size),
                       value - edge_interval);

    uint64_t level_size = _arity;
    for (int i = 0; i < max_levels; ++i) {
        uint64_t start = (value / level_size) * level_size;
        if (negative) {
            if (start + level_size - 1 > (uint64_t(0)-LLONG_MIN)) {
                break;
            }
            size = sprintf(buffer + prefix_size, "%" PRIu64 "-%" PRIu64,
                           start + level_size - 1, start);
        } else {
            if (start + level_size - 1 > LLONG_MAX) {
                break;
            }
            size = sprintf(buffer + prefix_size, "%" PRIu64 "-%" PRIu64,
                           start, start + level_size - 1);
        }
        handler.handleRange(vespalib::stringref(buffer, prefix_size + size));
        level_size *= _arity;
        if (!level_size) {  // overflow
            break;
        }
    }
}

}
