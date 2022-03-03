// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_unique_store_dictionary_read_snapshot.h"

namespace vespalib::datastore {

/**
 * Class that provides a read snapshot of a unique store dictionary using a btree.
 *
 * A generation guard that must be taken and held while the snapshot is considered valid.
 */
template <typename BTreeDictionaryT>
class UniqueStoreBTreeDictionaryReadSnapshot : public IUniqueStoreDictionaryReadSnapshot {
private:
    using BTreeDictionaryType = BTreeDictionaryT;
    using FrozenView = typename BTreeDictionaryType::FrozenView;
    FrozenView _frozen_view;
    
public:
    UniqueStoreBTreeDictionaryReadSnapshot(FrozenView frozen_view);
    void fill() override;
    void sort() override;
    size_t count(const EntryComparator& comp) const override;
    size_t count_in_range(const EntryComparator& low, const EntryComparator& high) const override;
    void foreach_key(std::function<void(const AtomicEntryRef&)> callback) const override;
};

}
