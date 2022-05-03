// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# pragma once

#include <vespa/vespalib/datastore/atomic_entry_ref.h>

namespace search::memoryindex {

/**
 * Class storing interleaved features for a posting list entry.
 */
class InterleavedFeatures {
protected:
    uint16_t _num_occs;
    uint16_t _field_length;

public:
    InterleavedFeatures()
        : _num_occs(0),
          _field_length(1)
    {
    }
    InterleavedFeatures(uint16_t num_occs, uint16_t field_length)
        : _num_occs(num_occs),
          _field_length(field_length)
    {
    }
    uint16_t get_num_occs() const { return _num_occs; }
    uint16_t get_field_length() const { return _field_length; }
};

/**
 * Empty class used when posting list entry does not have interleaved features.
 */
class NoInterleavedFeatures {
public:
    NoInterleavedFeatures() {}
    NoInterleavedFeatures(uint16_t num_occs, uint16_t field_length) {
        (void) num_occs;
        (void) field_length;
    }
    uint16_t get_num_occs() const { return 0; }
    uint16_t get_field_length() const { return 1; }
};

/**
 * Entry per document in memory index posting list.
 */
template <bool interleaved_features>
class PostingListEntry : public std::conditional_t<interleaved_features, InterleavedFeatures, NoInterleavedFeatures> {
    using ParentType = std::conditional_t<interleaved_features, InterleavedFeatures, NoInterleavedFeatures>;
    mutable vespalib::datastore::AtomicEntryRef _features; // reference to compressed features

public:
    explicit PostingListEntry(vespalib::datastore::EntryRef features, uint16_t num_occs, uint16_t field_length)
        : ParentType(num_occs, field_length),
          _features(features)
    {
    }

    PostingListEntry()
        : ParentType(),
          _features()
    {
    }

    vespalib::datastore::EntryRef get_features() const noexcept { return _features.load_acquire(); }
    vespalib::datastore::EntryRef get_features_relaxed() const noexcept { return _features.load_relaxed(); }

    /*
     * Reference moved features (used when compacting FeatureStore).
     * The moved features must have the same content as the original
     * features.
     */
    void update_features(vespalib::datastore::EntryRef features) const { _features.store_release(features); }
};

template class PostingListEntry<false>;
template class PostingListEntry<true>;

}
