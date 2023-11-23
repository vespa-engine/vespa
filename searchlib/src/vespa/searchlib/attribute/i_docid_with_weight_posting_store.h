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
    virtual void create(vespalib::datastore::EntryRef idx, std::vector<DocidWithWeightIterator> &dst) const = 0;
    virtual DocidWithWeightIterator create(vespalib::datastore::EntryRef idx) const = 0;
};

}

