// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store_builder.h"
#include "i_unique_store_dictionary.h"
#include "datastore.hpp"

namespace vespalib::datastore {

template <typename Allocator>
UniqueStoreBuilder<Allocator>::UniqueStoreBuilder(Allocator& allocator, IUniqueStoreDictionary& dict, uint32_t uniqueValuesHint)
    : _allocator(allocator),
      _dict(dict),
      _refs(),
      _refCounts()
{
    _refs.reserve(uniqueValuesHint);
    _refs.push_back(EntryRef());
}

template <typename Allocator>
UniqueStoreBuilder<Allocator>::~UniqueStoreBuilder() = default;

template <typename Allocator>
void
UniqueStoreBuilder<Allocator>::setupRefCounts()
{
    _refCounts.resize(_refs.size());
}

template <typename Allocator>
void
UniqueStoreBuilder<Allocator>::makeDictionary()
{
    auto ref_count_itr = _refCounts.cbegin();
    for (auto ref : _refs) {
        auto& wrapped_entry = _allocator.get_wrapped(ref);
        wrapped_entry.set_ref_count(*ref_count_itr);
        ++ref_count_itr;
    }
    _dict.build(_refs, _refCounts, [this](EntryRef ref) { _allocator.hold(ref); });
}

}

