// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/btree/btree.h>
#include "unique_store_dictionary_base.h"

#pragma once

namespace search::datastore {

class EntryComparatorWrapper;

/**
 * A dictionary for unique store. Mostly accessed via base class.
 */
template <typename DictionaryT, typename ParentT = UniqueStoreDictionaryBase>
class UniqueStoreDictionary : public ParentT
{
protected:
    using DictionaryType = DictionaryT;
    using DataType = typename DictionaryType::DataType;
    using generation_t = typename ParentT::generation_t;

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
    void build(const std::vector<EntryRef> &refs, const std::vector<uint32_t> &ref_counts, std::function<void(EntryRef)> hold) override;
    EntryRef get_frozen_root() const override;
    void foreach_key(EntryRef root, std::function<void(EntryRef)> callback) const override;
};

}
