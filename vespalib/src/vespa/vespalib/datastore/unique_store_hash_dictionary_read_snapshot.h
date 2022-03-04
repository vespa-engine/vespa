// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_unique_store_dictionary_read_snapshot.h"

namespace vespalib::datastore {

/**
 * Class that provides a read snapshot of a unique store dictionary using a hash.
 *
 * A generation guard that must be taken and held while the snapshot is considered valid.
 *
 * fill() must be called by the writer thread.
 * sort() must be called if order of refs should correspond to sorted dictionary order.
 *
 */
template <typename HashDictionaryT>
class UniqueStoreHashDictionaryReadSnapshot : public IUniqueStoreDictionaryReadSnapshot {
private:
    using HashDictionaryType = HashDictionaryT;
    const HashDictionaryType& _hash;
    std::vector<EntryRef>     _refs;

public:
    UniqueStoreHashDictionaryReadSnapshot(const HashDictionaryType &hash);
    void fill() override;
    void sort() override;
    size_t count(const EntryComparator& comp) const override;
    size_t count_in_range(const EntryComparator& low, const EntryComparator& high) const override;
    void foreach_key(std::function<void(const AtomicEntryRef&)> callback) const override;
};

}
