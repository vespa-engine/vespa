// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/btree/btree.h>
#include "unique_store_dictionary_base.h"

#pragma once

namespace search::datastore {

class EntryComparatorWrapper;

/*
 * A dictionary for unique store. Mostly accessed via base class.
 */
class UniqueStoreDictionary : public UniqueStoreDictionaryBase
{
public:
    using DictionaryTraits = btree::BTreeTraits<32, 32, 7, true>;
    using Dictionary = btree::BTree<EntryRef, uint32_t,
                                    btree::NoAggregated,
                                    EntryComparatorWrapper,
                                    DictionaryTraits>;

private:
    Dictionary _dict;

public:
    UniqueStoreDictionary();
    ~UniqueStoreDictionary() override;
    void freeze() override;
    void transfer_hold_lists(generation_t generation) override;
    void trim_hold_lists(generation_t firstUsed) override;
    UniqueStoreAddResult add(const EntryComparator& comp, std::function<EntryRef(void)> insertEntry) override;
    EntryRef find(const EntryComparator& comp) override;
    bool remove(const EntryComparator& comp, EntryRef ref) override;
    void move_entries(ICompactable& compactable) override;
    uint32_t get_num_uniques() const override;
    vespalib::MemoryUsage get_memory_usage() const override;
    void build(const std::vector<EntryRef> &refs, const std::vector<uint32_t> &ref_counts, std::function<void(EntryRef)> hold) override;
    EntryRef get_frozen_root() const override;
    void foreach_key(EntryRef root, std::function<void(EntryRef)> callback) const override;
};

}
