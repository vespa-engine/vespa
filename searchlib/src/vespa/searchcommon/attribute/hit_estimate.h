// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <stdint.h>

namespace search::attribute {

/**
 * Class encapsulating the estimated number of hits an attribute search context will provide.
 *
 * E.g. for attributes without fast-search an estimate is not known.
 * Instead the total number of values to match against is returned as the estimate.
 * This is always at least the docid limit space.
 */
class HitEstimate {
private:
    uint32_t _est_hits;
    bool _unknown;

    HitEstimate(uint32_t est_hits_in, bool unknown_in) : _est_hits(est_hits_in), _unknown(unknown_in) {}

public:
    explicit HitEstimate(uint32_t est_hits_in) : HitEstimate(est_hits_in, false) {}
    static HitEstimate unknown(uint32_t total_value_count) { return HitEstimate(total_value_count, true); }
    uint32_t est_hits() const { return _est_hits; }
    bool is_unknown() const { return _unknown; }
};

}

