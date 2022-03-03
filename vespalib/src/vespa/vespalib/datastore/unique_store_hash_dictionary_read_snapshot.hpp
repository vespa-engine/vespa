// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store_hash_dictionary_read_snapshot.h"

namespace vespalib::datastore {

template <typename HashDictionaryT>
UniqueStoreHashDictionaryReadSnapshot<HashDictionaryT>::UniqueStoreHashDictionaryReadSnapshot(const HashDictionaryType &hash)
    : _hash(hash),
      _refs()
{
}

template <typename HashDictionaryT>
void
UniqueStoreHashDictionaryReadSnapshot<HashDictionaryT>::fill()
{
    _hash.foreach_key([this](EntryRef ref) { _refs.push_back(ref); });
}

template <typename HashDictionaryT>
void
UniqueStoreHashDictionaryReadSnapshot<HashDictionaryT>::sort()
{
    auto& comp = _hash.get_default_comparator();
    std::sort(_refs.begin(), _refs.end(), [&comp](EntryRef lhs, EntryRef rhs) { return comp.less(lhs, rhs); });
}

template <typename HashDictionaryT>
size_t
UniqueStoreHashDictionaryReadSnapshot<HashDictionaryT>::count(const EntryComparator& comp) const
{
    auto result = _hash.find(comp, EntryRef());
    return ((result != nullptr) ? 1u : 0u);
}

template <typename HashDictionaryT>
size_t
UniqueStoreHashDictionaryReadSnapshot<HashDictionaryT>::count_in_range(const EntryComparator&, const EntryComparator&) const
{
    return 1u;
}

template <typename HashDictionaryT>
void
UniqueStoreHashDictionaryReadSnapshot<HashDictionaryT>::foreach_key(std::function<void(const AtomicEntryRef&)> callback) const
{
    for (auto ref : _refs) {
        callback(AtomicEntryRef(ref));
    }
}

}
