// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/vespalib/btree/btree.h>
#include <vespa/vespalib/datastore/datastore.h>
#include <vespa/vespalib/datastore/entry_comparator_wrapper.h>
#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/datastore/unique_store_dictionary.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/util/address_space.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <atomic>
#include <set>

namespace vespalib { class asciistream; }
namespace search {

class BufferWriter;

namespace attribute { class Status; }

class EnumStoreBase;

using EnumStoreComparator = datastore::EntryComparator;
using EnumStoreDataStoreType = datastore::DataStoreT<datastore::AlignedEntryRefT<31, 4> >;
using EnumStoreIndex = EnumStoreDataStoreType::RefType;
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

struct CompareEnumIndex
{
    using Index = EnumStoreIndex;

    bool operator()(const Index &lhs, const Index &rhs) const {
        return lhs.ref() < rhs.ref();
    }
};

class EnumStoreDictBase : public datastore::UniqueStoreDictionaryBase {
public:
    using EnumVector = EnumStoreEnumVector;
    using Index = EnumStoreIndex;
    using IndexSet = std::set<Index, CompareEnumIndex>;
    using IndexVector = EnumStoreIndexVector;
    using generation_t = vespalib::GenerationHandler::generation_t;

public:
    EnumStoreDictBase();
    virtual ~EnumStoreDictBase();

    virtual uint32_t getNumUniques() const = 0;
    virtual void writeAllValues(BufferWriter &writer, btree::BTreeNode::Ref rootRef) const = 0;
    virtual ssize_t deserialize(const void *src, size_t available, IndexVector &idx) = 0;

    virtual void fixupRefCounts(const EnumVector &hist) = 0;
    virtual void freeUnusedEnums(const datastore::EntryComparator &cmp,
                                 const datastore::EntryComparator *fcmp) = 0;
    virtual void freeUnusedEnums(const IndexSet& toRemove,
                                 const datastore::EntryComparator& cmp,
                                 const datastore::EntryComparator* fcmp) = 0;
    virtual bool findIndex(const datastore::EntryComparator &cmp, Index &idx) const = 0;
    virtual bool findFrozenIndex(const datastore::EntryComparator &cmp, Index &idx) const = 0;
    virtual std::vector<attribute::IAttributeVector::EnumHandle>
    findMatchingEnums(const datastore::EntryComparator &cmp) const = 0;

    virtual void onReset() = 0;
    virtual btree::BTreeNode::Ref getFrozenRootRef() const = 0;

    virtual EnumPostingTree &getPostingDictionary() = 0;
    virtual const EnumPostingTree &getPostingDictionary() const = 0;
    virtual bool hasData() const = 0;
};


template <typename Dictionary>
class EnumStoreDict : public datastore::UniqueStoreDictionary<Dictionary, EnumStoreDictBase>
{
private:
    using EnumVector = EnumStoreDictBase::EnumVector;
    using Index = EnumStoreDictBase::Index;
    using IndexSet = EnumStoreDictBase::IndexSet;
    using IndexVector = EnumStoreDictBase::IndexVector;
    using ParentUniqueStoreDictionary = datastore::UniqueStoreDictionary<Dictionary, EnumStoreDictBase>;
    using generation_t = EnumStoreDictBase::generation_t;

    EnumStoreBase& _enumStore;

public:
    EnumStoreDict(EnumStoreBase &enumStore);

    ~EnumStoreDict() override;

    const Dictionary &getDictionary() const { return this->_dict; }
    Dictionary &getDictionary() { return this->_dict; }
    
    uint32_t getNumUniques() const override;
    void writeAllValues(BufferWriter &writer, btree::BTreeNode::Ref rootRef) const override;
    ssize_t deserialize(const void *src, size_t available, IndexVector &idx) override;
    void fixupRefCounts(const EnumVector &hist) override;

    void removeUnusedEnums(const IndexSet &unused,
                           const datastore::EntryComparator &cmp,
                           const datastore::EntryComparator *fcmp);

    void freeUnusedEnums(const datastore::EntryComparator &cmp,
                         const datastore::EntryComparator *fcmp) override;

    void freeUnusedEnums(const IndexSet& toRemove,
                         const datastore::EntryComparator& cmp,
                         const datastore::EntryComparator* fcmp) override;

    bool findIndex(const datastore::EntryComparator &cmp, Index &idx) const override;
    bool findFrozenIndex(const datastore::EntryComparator &cmp, Index &idx) const override;
    std::vector<attribute::IAttributeVector::EnumHandle>
    findMatchingEnums(const datastore::EntryComparator &cmp) const override;

    void onReset() override;
    btree::BTreeNode::Ref getFrozenRootRef() const override { return this->get_frozen_root(); }

    EnumPostingTree & getPostingDictionary() override;
    const EnumPostingTree & getPostingDictionary() const override;

    bool hasData() const override;
};


class EnumStoreBase
{
public:
    using DataStoreType = EnumStoreDataStoreType;
    using EnumHandle = attribute::IAttributeVector::EnumHandle;
    using EnumVector = EnumStoreEnumVector;
    using Index = EnumStoreIndex;
    using IndexVector = EnumStoreIndexVector;
    using generation_t = vespalib::GenerationHandler::generation_t;

    using EnumIndexMap = vespalib::hash_map<Index, Index, vespalib::hash<Index>, std::equal_to<Index>,
                                            vespalib::hashtable_base::and_modulator>;

    class EntryBase {
    protected:
        char * _data;
    public:
        EntryBase(void * data) : _data(static_cast<char *>(data)) {}

        uint32_t getRefCount() const {
            return *(reinterpret_cast<uint32_t *>(_data) + 1);
        }

        void incRefCount() {
            uint32_t *dst = reinterpret_cast<uint32_t *>(_data) + 1;
            ++(*dst);
        }

        void decRefCount() {
            uint32_t *dst = reinterpret_cast<uint32_t *>(_data) + 1;
            --(*dst);
        }

        void setRefCount(uint32_t refCount) {
            uint32_t *dst = reinterpret_cast<uint32_t *>(_data) + 1;
            *dst = refCount;
        }

        static uint32_t size() { return 2*sizeof(uint32_t); }
    };

    using IndexSet = std::set<Index, CompareEnumIndex>;

protected:

    class EnumBufferType : public datastore::BufferType<char> {
    private:
        size_t _minSizeNeeded; // lower cap for sizeNeeded
        size_t _deadElems;     // dead elements in active buffer
        bool   _pendingCompact;
        bool   _wantCompact;
    public:
        EnumBufferType();

        size_t calcArraysToAlloc(uint32_t bufferId, size_t sizeNeeded, bool resizing) const override;

        void setSizeNeededAndDead(size_t sizeNeeded, size_t deadElems) {
            _minSizeNeeded = sizeNeeded;
            _deadElems = deadElems;
        }

        void onFree(size_t usedElems) override {
            datastore::BufferType<char>::onFree(usedElems);
            _pendingCompact = _wantCompact;
            _wantCompact = false;
        }

        void setWantCompact() { _wantCompact = true; }
        bool getPendingCompact() const { return _pendingCompact; }
        void clearPendingCompact() { _pendingCompact = false; }
    };

    EnumStoreDictBase    *_enumDict;
    DataStoreType         _store;
    EnumBufferType        _type;
    std::vector<uint32_t> _toHoldBuffers; // used during compaction

    static const uint32_t TYPE_ID = 0;

    EnumStoreBase(uint64_t initBufferSize, bool hasPostings);

    virtual ~EnumStoreBase();

    EntryBase getEntryBase(Index idx) const {
        return EntryBase(const_cast<DataStoreType &>(_store).getEntry<char>(idx));
    }
    datastore::BufferState & getBuffer(uint32_t bufferIdx) {
        return _store.getBufferState(bufferIdx);
    }
    const datastore::BufferState & getBuffer(uint32_t bufferIdx) const {
        return _store.getBufferState(bufferIdx);
    }
    bool validIndex(Index idx) const {
        return (idx.valid() && idx.offset() < _store.getBufferState(idx.bufferId()).size());
    }

    uint32_t getBufferIndex(datastore::BufferState::State status);
    void postCompact();
    bool preCompact(uint64_t bytesNeeded);

public:
    void reset(uint64_t initBufferSize);

    uint32_t getRefCount(Index idx) const { return getEntryBase(idx).getRefCount(); }
    void incRefCount(Index idx)           { getEntryBase(idx).incRefCount(); }
    void decRefCount(Index idx)           { getEntryBase(idx).decRefCount(); }
    
    // Only use when reading from enumerated attribute save files
    void fixupRefCount(Index idx, uint32_t refCount) {
        getEntryBase(idx).setRefCount(refCount);
    } 
    
    template <typename Tree>
    void fixupRefCounts(const EnumVector &hist, Tree &tree);

    uint32_t getNumUniques() const { return _enumDict->getNumUniques(); }

    uint32_t getRemaining() const {
        return _store.getBufferState(_store.getActiveBufferId(TYPE_ID)).remaining();
    }
    uint32_t getCapacity() const {
        return _store.getBufferState(_store.getActiveBufferId(TYPE_ID)).capacity();
    }
    vespalib::MemoryUsage getMemoryUsage() const;
    vespalib::MemoryUsage getTreeMemoryUsage() const { return _enumDict->get_memory_usage(); }

    vespalib::AddressSpace getAddressSpaceUsage() const;

    void transferHoldLists(generation_t generation);
    void trimHoldLists(generation_t firstUsed);

    static void failNewSize(uint64_t minNewSize, uint64_t maxSize);

    // Align buffers and entries to 4 bytes boundary.
    static uint64_t alignBufferSize(uint64_t val) { return Index::align(val); }
    static uint32_t alignEntrySize(uint32_t val) { return Index::align(val); }

    void fallbackResize(uint64_t bytesNeeded);
    bool getPendingCompact() const { return _type.getPendingCompact(); }
    void clearPendingCompact() { _type.clearPendingCompact(); }

    virtual void writeValues(BufferWriter &writer, const Index *idxs, size_t count) const = 0;

    virtual ssize_t deserialize(const void *src, size_t available, size_t &initSpace) = 0;
    virtual ssize_t deserialize(const void *src, size_t available, Index &idx) = 0;
    virtual bool foldedChange(const Index &idx1, const Index &idx2) = 0;

    ssize_t deserialize0(const void *src, size_t available, IndexVector &idx);

    template <typename Tree>
    ssize_t deserialize(const void *src, size_t available, IndexVector &idx, Tree &tree);

    ssize_t deserialize(const void *src, size_t available, IndexVector &idx) {
        return _enumDict->deserialize(src, available, idx);
    }

    virtual void freeUnusedEnum(Index idx, IndexSet &unused) = 0;
    virtual void freeUnusedEnums(bool movePostingIdx) = 0;
    virtual void freeUnusedEnums(const IndexSet& toRemove) = 0;

    void fixupRefCounts(const EnumVector &hist) { _enumDict->fixupRefCounts(hist); }
    void freezeTree() { _enumDict->freeze(); }

    virtual bool performCompaction(uint64_t bytesNeeded, EnumIndexMap & old2New) = 0;

    EnumStoreDictBase &getEnumStoreDict() { return *_enumDict; }
    const EnumStoreDictBase &getEnumStoreDict() const { return *_enumDict; }
    EnumPostingTree &getPostingDictionary() { return _enumDict->getPostingDictionary(); }

    const EnumPostingTree &getPostingDictionary() const {
        return _enumDict->getPostingDictionary();
    }
    const datastore::DataStoreBase &get_data_store_base() const { return _store; }
};

vespalib::asciistream & operator << (vespalib::asciistream & os, const EnumStoreBase::Index & idx);


extern template
class datastore::DataStoreT<datastore::AlignedEntryRefT<31, 4> >;

extern template
class btree::BTreeNodeT<EnumStoreBase::Index, EnumTreeTraits::INTERNAL_SLOTS>;

extern template
class btree::BTreeNodeTT<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated, EnumTreeTraits::INTERNAL_SLOTS>;

extern template
class btree::BTreeNodeTT<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeInternalNode<EnumStoreBase::Index, btree::NoAggregated, EnumTreeTraits::INTERNAL_SLOTS>;

extern template
class btree::BTreeLeafNode<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeLeafNode<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeLeafNodeTemp<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeLeafNodeTemp<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeNodeStore<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                            EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeNodeStore<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated,
                            EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeRoot<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                       const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template
class btree::BTreeRoot<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated,
                       const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template
class btree::BTreeRootT<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                        const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template
class btree::BTreeRootT<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated,
                        const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template
class btree::BTreeRootBase<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                           EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeRootBase<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated,
                           EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeNodeAllocator<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                                EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeNodeAllocator<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated,
                                EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;


extern template
class btree::BTreeIteratorBase<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                               EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS, EnumTreeTraits::PATH_SIZE>;
extern template
class btree::BTreeIteratorBase<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated,
                               EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS, EnumTreeTraits::PATH_SIZE>;

extern template class btree::BTreeConstIterator<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                                                const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template class btree::BTreeConstIterator<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated,
                                                const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template
class btree::BTreeIterator<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                           const datastore::EntryComparatorWrapper, EnumTreeTraits>;
extern template
class btree::BTreeIterator<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated,
                           const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template
class btree::BTree<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                   const datastore::EntryComparatorWrapper, EnumTreeTraits>;
extern template
class btree::BTree<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated,
                   const datastore::EntryComparatorWrapper, EnumTreeTraits>;

}
