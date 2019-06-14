// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# pragma once

#include <vespa/vespalib/datastore/entryref.h>

namespace search::memoryindex {

/**
 * Entry per document in memory index posting list.
 */
class PostingListEntry {
    mutable datastore::EntryRef _features; // reference to compressed features

public:
    PostingListEntry(datastore::EntryRef features)
        : _features(features)
    {
    }

    PostingListEntry()
        : _features()
    {
    }
       datastore::EntryRef get_features() const { return _features; }

    // Reference moved data (used when compacting FeatureStore)
    void update_features(datastore::EntryRef features) const { _features = features; }
};

}
