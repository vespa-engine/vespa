// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreenode.h"
#include "btreetraits.h"
#include <vespa/vespalib/datastore/datastore.h>

namespace vespalib::datastore { class CompactingBuffers; }

namespace vespalib::btree {

class BTreeNodeReclaimer
{
public:
    static void reclaim(BTreeNode * node) {
        node->unFreeze();
    }
};

template <typename ToFreeze>
struct FrozenBtreeNode : public ToFreeze {
    FrozenBtreeNode() : ToFreeze() { this->freeze(); }
};

template <typename EntryType>
class BTreeNodeBufferType : public datastore::BufferType<EntryType, FrozenBtreeNode<EntryType>>
{
    using ParentType = datastore::BufferType<EntryType, FrozenBtreeNode<EntryType>>;
    using ParentType::empty_entry;
    using ParentType::_arraySize;
    using ElemCount = typename ParentType::ElemCount;
    using CleanContext = typename ParentType::CleanContext;
public:
    BTreeNodeBufferType(uint32_t minArrays, uint32_t maxArrays)
        : ParentType(1, minArrays, maxArrays)
    { }

    void initializeReservedElements(void *buffer, ElemCount reservedElements) override;

    void cleanHold(void *buffer, size_t offset, ElemCount numElems, CleanContext cleanCtx) override;
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
    using CompactionStrategy = datastore::CompactionStrategy;

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
        return _store.getEntry<InternalNodeType>(iRef);
    }

    InternalNodeType *mapInternalRef(EntryRef ref) {
        RefType iRef(ref);
        return _store.getEntry<InternalNodeType>(iRef);
    }

    const LeafNodeType *mapLeafRef(EntryRef ref) const {
        RefType iRef(ref);
        return _store.getEntry<LeafNodeType>(iRef);
    }

    LeafNodeType *mapLeafRef(EntryRef ref) {
        RefType iRef(ref);
        return _store.getEntry<LeafNodeType>(iRef);
    }

    template <typename NodeType>
    const NodeType *mapRef(EntryRef ref) const {
        RefType iRef(ref);
        return _store.getEntry<NodeType>(iRef);
    }

    template <typename NodeType>
    NodeType *mapRef(EntryRef ref) {
        RefType iRef(ref);
        return _store.getEntry<NodeType>(iRef);
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

    std::unique_ptr<vespalib::datastore::CompactingBuffers> start_compact_worst(const CompactionStrategy& compaction_strategy);

    void assign_generation(generation_t current_gen) {
        _store.assign_generation(current_gen);
    }

    // Inherit doc from DataStoreBase
    datastore::MemoryStats getMemStats() const {
        return _store.getMemStats();
    }

    // Inherit doc from DataStoreBase
    void reclaim_memory(generation_t oldest_used_gen) {
        _store.reclaim_memory(oldest_used_gen);
    }

    void reclaim_all_memory() {
        _store.reclaim_all_memory();
    }

    // Inherit doc from DataStoreBase
    vespalib::MemoryUsage getMemoryUsage() const {
        return _store.getMemoryUsage();
    }

    // Inherit doc from DataStoreT
    bool getCompacting(EntryRef ref) const {
        return _store.getCompacting(ref);
    }

    bool has_held_buffers() const {
        return _store.has_held_buffers();
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

extern template class BTreeNodeBufferType<BTreeInternalNode<uint32_t, NoAggregated, BTreeDefaultTraits::INTERNAL_SLOTS>>;
extern template class BTreeNodeBufferType<BTreeInternalNode<uint32_t, MinMaxAggregated, BTreeDefaultTraits::INTERNAL_SLOTS>>;

extern template class BTreeNodeBufferType<BTreeLeafNode<uint32_t, uint32_t, NoAggregated, BTreeDefaultTraits::LEAF_SLOTS>>;
extern template class BTreeNodeBufferType<BTreeLeafNode<uint32_t, BTreeNoLeafData, NoAggregated, BTreeDefaultTraits::LEAF_SLOTS>>;
extern template class BTreeNodeBufferType<BTreeLeafNode<uint32_t, int32_t, MinMaxAggregated, BTreeDefaultTraits::LEAF_SLOTS>>;

}

namespace vespalib::datastore {

using namespace btree;

extern template class BufferType<BTreeInternalNode<uint32_t, NoAggregated, BTreeDefaultTraits::INTERNAL_SLOTS>,
                                 FrozenBtreeNode<BTreeInternalNode<uint32_t, NoAggregated, BTreeDefaultTraits::INTERNAL_SLOTS>>>;
extern template class BufferType<BTreeInternalNode<uint32_t, MinMaxAggregated, BTreeDefaultTraits::INTERNAL_SLOTS>,
                                 FrozenBtreeNode<BTreeInternalNode<uint32_t, MinMaxAggregated, BTreeDefaultTraits::INTERNAL_SLOTS>>>;

extern template class BufferType<BTreeLeafNode<uint32_t, uint32_t, NoAggregated, BTreeDefaultTraits::LEAF_SLOTS>,
                                 FrozenBtreeNode<BTreeLeafNode<uint32_t, uint32_t, NoAggregated, BTreeDefaultTraits::LEAF_SLOTS>>>;
extern template class BufferType<BTreeLeafNode<uint32_t, BTreeNoLeafData, NoAggregated, BTreeDefaultTraits::LEAF_SLOTS>,
                                 FrozenBtreeNode<BTreeLeafNode<uint32_t, BTreeNoLeafData, NoAggregated, BTreeDefaultTraits::LEAF_SLOTS>>>;
extern template class BufferType<BTreeLeafNode<uint32_t, int32_t, MinMaxAggregated, BTreeDefaultTraits::LEAF_SLOTS>,
                                 FrozenBtreeNode<BTreeLeafNode<uint32_t, int32_t, MinMaxAggregated, BTreeDefaultTraits::LEAF_SLOTS>>>;

extern template class BufferType<BTreeKeyData<uint32_t, uint32_t>>;
extern template class BufferType<BTreeKeyData<uint32_t, int32_t>>;
extern template class BufferType<BTreeKeyData<uint32_t, BTreeNoLeafData>>;

}
