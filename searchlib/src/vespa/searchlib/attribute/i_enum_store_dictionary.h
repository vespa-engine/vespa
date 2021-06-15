// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_enum_store.h"
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/vespalib/datastore/entry_comparator_wrapper.h>
#include <vespa/vespalib/datastore/unique_store_dictionary.h>

namespace search {

class BufferWriter;

using EnumTreeTraits = vespalib::btree::BTreeTraits<16, 16, 10, true>;

using EnumTree = vespalib::btree::BTree<IEnumStore::Index, vespalib::btree::BTreeNoLeafData,
                              vespalib::btree::NoAggregated,
                              const vespalib::datastore::EntryComparatorWrapper,
                              EnumTreeTraits>;

using EnumPostingTree = vespalib::btree::BTree<IEnumStore::Index, uint32_t,
                                     vespalib::btree::NoAggregated,
                                     const vespalib::datastore::EntryComparatorWrapper,
                                     EnumTreeTraits>;

/**
 * Interface for the dictionary used by an enum store.
 */
class IEnumStoreDictionary : public vespalib::datastore::IUniqueStoreDictionary {
public:
    using EntryRef = vespalib::datastore::EntryRef;
    using EntryComparator = vespalib::datastore::EntryComparator;
    using EnumVector = IEnumStore::EnumVector;
    using Index = IEnumStore::Index;
    using IndexList = IEnumStore::IndexList;
    using IndexVector = IEnumStore::IndexVector;
    using generation_t = vespalib::GenerationHandler::generation_t;

public:
    virtual ~IEnumStoreDictionary() = default;

    virtual void free_unused_values(const EntryComparator& cmp) = 0;
    virtual void free_unused_values(const IndexList& to_remove, const EntryComparator& cmp) = 0;
    virtual bool find_index(const EntryComparator& cmp, Index& idx) const = 0;
    virtual bool find_frozen_index(const EntryComparator& cmp, Index& idx) const = 0;
    virtual std::vector<attribute::IAttributeVector::EnumHandle>
    find_matching_enums(const EntryComparator& cmp) const = 0;

    virtual EntryRef get_frozen_root() const = 0;
    virtual std::pair<Index, EntryRef> find_posting_list(const EntryComparator& cmp, EntryRef root) const = 0;
    virtual void collect_folded(Index idx, EntryRef root, const std::function<void(EntryRef)>& callback) const = 0;
    virtual Index remap_index(Index idx) = 0;
    virtual void clear_all_posting_lists(std::function<void(EntryRef)> clearer) = 0;
    virtual void update_posting_list(Index idx, const EntryComparator& cmp, std::function<EntryRef(EntryRef)> updater) = 0;
    virtual bool normalize_posting_lists(std::function<EntryRef(EntryRef)> normalize) = 0;
    virtual const EnumPostingTree& get_posting_dictionary() const = 0;
};

}
