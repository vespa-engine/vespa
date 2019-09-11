// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_enum_store.h"
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/vespalib/datastore/entry_comparator_wrapper.h>
#include <vespa/vespalib/datastore/unique_store_dictionary.h>

namespace search {

class BufferWriter;

using EnumTreeTraits = btree::BTreeTraits<16, 16, 10, true>;

using EnumTree = btree::BTree<IEnumStore::Index, btree::BTreeNoLeafData,
                              btree::NoAggregated,
                              const datastore::EntryComparatorWrapper,
                              EnumTreeTraits>;

using EnumPostingTree = btree::BTree<IEnumStore::Index, uint32_t,
                                     btree::NoAggregated,
                                     const datastore::EntryComparatorWrapper,
                                     EnumTreeTraits>;

/**
 * Interface for the dictionary used by an enum store.
 */
class IEnumStoreDictionary : public datastore::IUniqueStoreDictionary {
public:
    using EnumVector = IEnumStore::EnumVector;
    using Index = IEnumStore::Index;
    using IndexSet = IEnumStore::IndexSet;
    using IndexVector = IEnumStore::IndexVector;
    using generation_t = vespalib::GenerationHandler::generation_t;

public:
    virtual ~IEnumStoreDictionary() = default;

    virtual uint32_t getNumUniques() const = 0;

    virtual void fixupRefCounts(const EnumVector& hist) = 0;
    virtual void freeUnusedEnums(const datastore::EntryComparator& cmp) = 0;
    virtual void freeUnusedEnums(const IndexSet& toRemove,
                                 const datastore::EntryComparator& cmp) = 0;
    virtual bool findIndex(const datastore::EntryComparator& cmp, Index& idx) const = 0;
    virtual bool findFrozenIndex(const datastore::EntryComparator& cmp, Index& idx) const = 0;
    virtual std::vector<attribute::IAttributeVector::EnumHandle>
    findMatchingEnums(const datastore::EntryComparator& cmp) const = 0;

    virtual void onReset() = 0;

    virtual EnumPostingTree& getPostingDictionary() = 0;
    virtual const EnumPostingTree& getPostingDictionary() const = 0;
    virtual bool hasData() const = 0;
};

}
