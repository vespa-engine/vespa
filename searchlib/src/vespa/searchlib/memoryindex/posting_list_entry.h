// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# pragma once

#include <vespa/vespalib/datastore/entryref.h>

namespace search::memoryindex {

/**
 * Entry per document in memory index posting list.
 */
template <bool has_interleaved_features>
class PostingListEntry {
    mutable datastore::EntryRef _features; // reference to compressed features

public:
    explicit PostingListEntry(datastore::EntryRef features)
        : _features(features)
    {
    }

    PostingListEntry()
        : _features()
    {
    }

    datastore::EntryRef get_features() const { return _features; }

    /*
     * Reference moved features (used when compacting FeatureStore).
     * The moved features must have the same content as the original
     * features.
     */
    void update_features(datastore::EntryRef features) const { _features = features; }
};

}
