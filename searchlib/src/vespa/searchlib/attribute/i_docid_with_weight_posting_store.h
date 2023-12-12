// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_direct_posting_store.h"

namespace search {

/**
 * Interface providing access to dictionary lookups and underlying posting lists that contains {docid, weight} tuples.
 *
 * This posting store type is supported by multi-value attributes with fast-search.
 */
class IDocidWithWeightPostingStore : public IDirectPostingStore {
public:
    using IteratorType = DocidWithWeightIterator;

    virtual void create(vespalib::datastore::EntryRef idx, std::vector<DocidWithWeightIterator> &dst) const = 0;
    virtual DocidWithWeightIterator create(vespalib::datastore::EntryRef idx) const = 0;

    /**
     * Returns true when posting list iterators with weight are present for all terms.
     *
     * This means posting list iterators exist in addition to eventual bitvector posting lists.
     */
    virtual bool has_always_weight_iterator() const noexcept = 0;
};



}

