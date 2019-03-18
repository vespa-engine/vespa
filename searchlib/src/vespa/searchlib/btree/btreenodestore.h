// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreenode.h"
#include "btreetraits.h"
#include <vespa/searchlib/datastore/datastore.h>

namespace search::btree {

class BTreeNodeReclaimer
{
public:
    static void reclaim(BTreeNode * node) {
        node->unFreeze();
    }
};


template <typename EntryType>
class BTreeNodeBufferType : public datastore::BufferType<EntryType>
{
    typedef datastore::BufferType<EntryType> ParentType;
    using ParentType::_emptyEntry;
    using ParentType::_arraySize;
    using CleanContext = typename ParentType::CleanContext;
public:
    BTreeNodeBufferType(uint32_t minArrays, uint32_t maxArrays)
        : ParentType(1, minArrays, maxArrays)
    {
        _emptyEntry.freeze();
    }

    void initializeReservedElements(void *buffer, size_t reservedElements) override;

    void cleanHold(void *buffer, size_t offset, size_t numElems, CleanContext cleanCtx) override;
};


template <typename KeyT,
          typename DataT,
          typename AggrT,
          size_t INTERNAL_SLOTS,
          size_t LEAF_SLOTS>
class BTreeNodeStore
{
public:
    typedef datastore::DataStoreT<datastore::EntryRefT<22> > DataStoreType;
    typedef DataStoreType::RefType RefType;
    typedef BTreeInternalNode<KeyT, AggrT, INTERNAL_SLOTS> InternalNodeType;
    typedef BTreeLeafNode<KeyT, DataT, AggrT, LEAF_SLOTS>  LeafNodeType;
    typedef typename InternalNodeType::RefPair InternalNodeTypeRefPair;
    typedef typename LeafNodeType::RefPair LeafNodeTypeRefPair;
    typedef vespalib::GenerationHandler::generation_t generation_t;
    using EntryRef = datastore::EntryRef;

    enum NodeTypes
    {
        NODETYPE_INTERNAL = 0,
        NODETYPE_LEAF = 1
    };


private:
    static constexpr size_t MIN_BUFFER_ARRAYS = 128u;
    DataStoreType _store;
    BTreeNodeBufferType<InternalNodeType> _internalNodeType;
    BTreeNodeBufferType<LeafNodeType> _leafNodeType;

public:
    BTreeNodeStore();

    ~BTreeNodeStore();

    void disableFreeLists() { _store.disableFreeLists(); }
    void disableElemHoldList() { _store.disableElemHoldList(); }

    static bool isValidRef(EntryRef ref) { return ref.valid(); }

    bool isLeafRef(EntryRef ref) const {
        RefType iRef(ref);
        return _store.getTypeId(iRef.bufferId()) == NODETYPE_LEAF;
    }

    const InternalNodeType *mapInternalRef(EntryRef ref) const {
        RefType iRef(ref);
        return _store.getBufferEntry<InternalNodeType>(iRef.bufferId(), iRef.offset());
    }

    InternalNodeType *mapInternalRef(EntryRef ref) {
        RefType iRef(ref);
        return _store.getBufferEntry<InternalNodeType>(iRef.bufferId(), iRef.offset());
    }

    const LeafNodeType *mapLeafRef(EntryRef ref) const {
        RefType iRef(ref);
        return _store.getBufferEntry<LeafNodeType>(iRef.bufferId(), iRef.offset());
    }

    LeafNodeType *mapLeafRef(EntryRef ref) {
        RefType iRef(ref);
        return _store.getBufferEntry<LeafNodeType>(iRef.bufferId(), iRef.offset());
    }

    template <typename NodeType>
    const NodeType *mapRef(EntryRef ref) const {
        RefType iRef(ref);
        return _store.getBufferEntry<NodeType>(iRef.bufferId(), iRef.offset());
    }

    template <typename NodeType>
    NodeType *mapRef(EntryRef ref) {
        RefType iRef(ref);
        return _store.getBufferEntry<NodeType>(iRef.bufferId(), iRef.offset());
    }

    LeafNodeTypeRefPair allocNewLeafNode() {
        return _store.allocator<LeafNodeType>(NODETYPE_LEAF).alloc();
    }

    LeafNodeTypeRefPair allocLeafNode() {
        return _store.freeListAllocator<LeafNodeType, BTreeNodeReclaimer>(NODETYPE_LEAF).alloc();
    }

    LeafNodeTypeRefPair allocNewLeafNodeCopy(const LeafNodeType &rhs) {
        return _store.allocator<LeafNodeType>(NODETYPE_LEAF).alloc(rhs);
    }

    LeafNodeTypeRefPair allocLeafNodeCopy(const LeafNodeType &rhs) {
        return _store.freeListAllocator<LeafNodeType, BTreeNodeReclaimer>(NODETYPE_LEAF).alloc(rhs);
    }

    InternalNodeTypeRefPair allocNewInternalNode() {
        return _store.allocator<InternalNodeType>(NODETYPE_INTERNAL).alloc();
    }

    InternalNodeTypeRefPair allocInternalNode() {
        return _store.freeListAllocator<InternalNodeType, BTreeNodeReclaimer>(NODETYPE_INTERNAL).alloc();
    }

    InternalNodeTypeRefPair allocNewInternalNodeCopy(const InternalNodeType &rhs) {
        return _store.allocator<InternalNodeType>(NODETYPE_INTERNAL).alloc(rhs);
    }

    InternalNodeTypeRefPair allocInternalNodeCopy(const InternalNodeType &rhs) {
        return _store.freeListAllocator<InternalNodeType, BTreeNodeReclaimer>(NODETYPE_INTERNAL).alloc(rhs);
    }

    void holdElem(EntryRef ref) {
        _store.holdElem(ref, 1);
    }

    void freeElem(EntryRef ref) {
        _store.freeElem(ref, 1);
    }

    std::vector<uint32_t> startCompact();

    void finishCompact(const std::vector<uint32_t> &toHold);

    void transferHoldLists(generation_t generation) {
        _store.transferHoldLists(generation);
    }

    // Inherit doc from DataStoreBase
    datastore::DataStoreBase::MemStats getMemStats() const {
        return _store.getMemStats();
    }

    // Inherit doc from DataStoreBase
    void trimHoldLists(generation_t usedGen) {
        _store.trimHoldLists(usedGen);
    }

    void clearHoldLists() {
        _store.clearHoldLists();
    }

    // Inherit doc from DataStoreBase
    MemoryUsage getMemoryUsage() const {
        return _store.getMemoryUsage();
    }

    // Inherit doc from DataStoreT
    bool getCompacting(EntryRef ref) const {
        return _store.getCompacting(ref);
    }

    template <typename FunctionType>
    void foreach_key(EntryRef ref, FunctionType func) const {
        if (!ref.valid())
            return;
        if (isLeafRef(ref)) {
            mapLeafRef(ref)->foreach_key(func);
        } else {
            mapInternalRef(ref)->foreach_key(*this, func);
        }
    }

    template <typename FunctionType>
    void foreach(EntryRef ref, FunctionType func) const {
        if (!ref.valid())
            return;
        if (isLeafRef(ref)) {
            mapLeafRef(ref)->foreach(func);
        } else {
            mapInternalRef(ref)->foreach(*this, func);
        }
    }
};

extern template class BTreeNodeStore<uint32_t, uint32_t, NoAggregated,
                                     BTreeDefaultTraits::INTERNAL_SLOTS,
                                     BTreeDefaultTraits::LEAF_SLOTS>;
extern template class BTreeNodeStore<uint32_t, BTreeNoLeafData, NoAggregated,
                                     BTreeDefaultTraits::INTERNAL_SLOTS,
                                     BTreeDefaultTraits::LEAF_SLOTS>;
extern template class BTreeNodeStore<uint32_t, int32_t, MinMaxAggregated,
                                     BTreeDefaultTraits::INTERNAL_SLOTS,
                                     BTreeDefaultTraits::LEAF_SLOTS>;

}
