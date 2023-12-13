// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_direct_posting_store.h"

namespace search {

/**
 * Interface providing access to dictionary lookups and underlying posting lists that contains only docids.
 *
 * This posting store type is supported by some single-value attributes with fast-search.
 */
class IDocidPostingStore : public IDirectPostingStore {
public:
    using IteratorType = DocidIterator;

    virtual void create(vespalib::datastore::EntryRef idx, std::vector<DocidIterator>& dst) const = 0;
    virtual DocidIterator create(vespalib::datastore::EntryRef idx) const = 0;
};

}
