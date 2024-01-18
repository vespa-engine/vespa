// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/weighted_set_term_search.h>

namespace search::attribute {

/**
 * Search iterator for an InTerm, sharing the implementation with WeightedSetTerm.
 *
 * The only difference is that an InTerm never requires unpacking of weights.
 */
class InTermSearch : public queryeval::WeightedSetTermSearch {
public:
    // Whether this iterator is considered a filter, independent of attribute vector settings (ref. rank: filter).
    // Setting this to true ensures that weights are never unpacked.
    static constexpr bool filter_search = true;
};

}
