// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_direct_posting_store.h"

#include <vespa/vespalib/btree/minmaxaggregated.h>

namespace search {

/**
 * Direct (low-level) iterator over a btree posting list that contains {docid, float weight} tuples.
 */
using DocidWithFloatWeightIterator = attribute::PostingListTraits<float>::const_iterator;

/**
 * Interface providing access to dictionary lookups and underlying posting lists that contains
 * {docid, float weight} tuples.
 *
 * The min_weight and max_weight fields of LookupResult are not set by this posting store type,
 * use get_weight_range() instead.
 */
class IDocidWithFloatWeightPostingStore : public IDirectPostingStore {
public:
    using IteratorType = DocidWithFloatWeightIterator;

    virtual void create(vespalib::datastore::EntryRef idx, std::vector<DocidWithFloatWeightIterator>& dst) const = 0;
    virtual DocidWithFloatWeightIterator create(vespalib::datastore::EntryRef idx) const = 0;
    // Returns the min and max weight in the given posting list.
    // NOTE: This reads the current (non-frozen) posting list, and is thus only safe to call
    // from the attribute writer thread. Query threads must instead use getAggregated() on a
    // (frozen) iterator returned by create().
    virtual vespalib::btree::FloatMinMaxAggregated
    get_weight_range(vespalib::datastore::EntryRef posting_idx) const = 0;
};

} // namespace search
