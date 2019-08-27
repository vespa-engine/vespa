// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/vespalib/datastore/entry_comparator_wrapper.h>
#include <vespa/vespalib/datastore/unique_store_dictionary.h>
#include <set>

namespace search {

class BufferWriter;

using EnumStoreIndex = datastore::AlignedEntryRefT<31, 4>;
using EnumStoreIndexVector = vespalib::Array<EnumStoreIndex>;
using EnumStoreEnumVector = vespalib::Array<uint32_t>;

using EnumTreeTraits = btree::BTreeTraits<16, 16, 10, true>;

using EnumTree = btree::BTree<EnumStoreIndex, btree::BTreeNoLeafData,
                              btree::NoAggregated,
                              const datastore::EntryComparatorWrapper,
                              EnumTreeTraits>;

using EnumPostingTree = btree::BTree<EnumStoreIndex, datastore::EntryRef,
                                     btree::NoAggregated,
                                     const datastore::EntryComparatorWrapper,
                                     EnumTreeTraits>;

struct CompareEnumIndex {
    using Index = EnumStoreIndex;

    bool operator()(const Index &lhs, const Index &rhs) const {
        return lhs.ref() < rhs.ref();
    }
};

/**
 * Interface for the dictionary used by an enum store.
 */
class IEnumStoreDictionary : public datastore::UniqueStoreDictionaryBase {
public:
    using EnumVector = EnumStoreEnumVector;
    using Index = EnumStoreIndex;
    using IndexSet = std::set<Index, CompareEnumIndex>;
    using IndexVector = EnumStoreIndexVector;
    using generation_t = vespalib::GenerationHandler::generation_t;

public:
    virtual ~IEnumStoreDictionary() = default;

    virtual uint32_t getNumUniques() const = 0;
    virtual void writeAllValues(BufferWriter& writer, btree::BTreeNode::Ref rootRef) const = 0;
    virtual ssize_t deserialize(const void* src, size_t available, IndexVector& idx) = 0;

    virtual void fixupRefCounts(const EnumVector& hist) = 0;
    virtual void freeUnusedEnums(const datastore::EntryComparator& cmp,
                                 const datastore::EntryComparator* fcmp) = 0;
    virtual void freeUnusedEnums(const IndexSet& toRemove,
                                 const datastore::EntryComparator& cmp,
                                 const datastore::EntryComparator* fcmp) = 0;
    virtual bool findIndex(const datastore::EntryComparator& cmp, Index& idx) const = 0;
    virtual bool findFrozenIndex(const datastore::EntryComparator& cmp, Index& idx) const = 0;
    virtual std::vector<attribute::IAttributeVector::EnumHandle>
    findMatchingEnums(const datastore::EntryComparator& cmp) const = 0;

    virtual void onReset() = 0;
    virtual btree::BTreeNode::Ref getFrozenRootRef() const = 0;

    virtual EnumPostingTree& getPostingDictionary() = 0;
    virtual const EnumPostingTree& getPostingDictionary() const = 0;
    virtual bool hasData() const = 0;
};

}
