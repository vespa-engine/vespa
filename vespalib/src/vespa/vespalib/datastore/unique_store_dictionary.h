// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/btree/btree.h>
#include "i_unique_store_dictionary.h"

#pragma once

namespace vespalib::datastore {

class EntryComparatorWrapper;

/**
 * A dictionary for unique store. Mostly accessed via base class.
 */
template <typename DictionaryT, typename ParentT = IUniqueStoreDictionary>
class UniqueStoreDictionary : public ParentT {
protected:
    using DictionaryType = DictionaryT;
    using DataType = typename DictionaryType::DataType;
    using FrozenView = typename DictionaryType::FrozenView;
    using ReadSnapshot = typename ParentT::ReadSnapshot;
    using generation_t = typename ParentT::generation_t;

    class ReadSnapshotImpl : public ReadSnapshot {
    private:
        FrozenView _frozen_view;

    public:
        ReadSnapshotImpl(FrozenView frozen_view);
        size_t count(const EntryComparator& comp) const override;
        size_t count_in_range(const EntryComparator& low, const EntryComparator& high) const override;
        void foreach_key(std::function<void(EntryRef)> callback) const override;
    };

    DictionaryType _dict;

public:
    UniqueStoreDictionary();
    ~UniqueStoreDictionary() override;
    void freeze() override;
    void transfer_hold_lists(generation_t generation) override;
    void trim_hold_lists(generation_t firstUsed) override;
    UniqueStoreAddResult add(const EntryComparator& comp, std::function<EntryRef(void)> insertEntry) override;
    EntryRef find(const EntryComparator& comp) override;
    void remove(const EntryComparator& comp, EntryRef ref) override;
    void move_entries(ICompactable& compactable) override;
    uint32_t get_num_uniques() const override;
    vespalib::MemoryUsage get_memory_usage() const override;
    void build(vespalib::ConstArrayRef<EntryRef>, vespalib::ConstArrayRef<uint32_t> ref_counts, std::function<void(EntryRef)> hold) override;
    void build(vespalib::ConstArrayRef<EntryRef> refs) override;
    void build_with_payload(vespalib::ConstArrayRef<EntryRef>, vespalib::ConstArrayRef<uint32_t> payloads) override;
    std::unique_ptr<ReadSnapshot> get_read_snapshot() const override;
};

}
