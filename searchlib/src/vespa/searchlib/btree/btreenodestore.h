// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreenode.h"
#include "btreetraits.h"
#include <vespa/searchlib/datastore/datastore.h>

namespace search
{

namespace btree
{

class BTreeNodeReclaimer
{
public:
    static void reclaim(BTreeNode * node)
    {
        node->unFreeze();
    }
};


template <typename EntryType>
class BTreeNodeBufferType : public datastore::BufferType<EntryType>
{
    typedef datastore::BufferType<EntryType> ParentType;
    using ParentType::_emptyEntry;
    using ParentType::_clusterSize;
    using CleanContext = typename ParentType::CleanContext;
public:
    BTreeNodeBufferType(uint32_t minClusters,
                        uint32_t maxClusters)
        : ParentType(1, minClusters, maxClusters)
    {
        _emptyEntry.freeze();
    }

    virtual void
    initializeReservedElements(void *buffer, size_t reservedElements) override;

    virtual void
    cleanHold(void *buffer, uint64_t offset, uint64_t len, CleanContext cleanCtx) override;
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
    static constexpr size_t MIN_CLUSTERS = 128u;
    DataStoreType _store;
    BTreeNodeBufferType<InternalNodeType> _internalNodeType;
    BTreeNodeBufferType<LeafNodeType> _leafNodeType;

public:
    BTreeNodeStore(void);

    ~BTreeNodeStore(void);

    void
    disableFreeLists() {
        _store.disableFreeLists();
    }

    void
    disableElemHoldList()
    {
        _store.disableElemHoldList();
    }

    static bool
    isValidRef(EntryRef ref)
    {
        return ref.valid();
    }

    bool
    isLeafRef(EntryRef ref) const
    {
        RefType iRef(ref);
        return _store.getTypeId(iRef.bufferId()) == NODETYPE_LEAF;
    }

    const InternalNodeType *
    mapInternalRef(EntryRef ref) const
    {
        RefType iRef(ref);
        return _store.getBufferEntry<InternalNodeType>(iRef.bufferId(),
                                                       iRef.offset());
    }

    InternalNodeType *
    mapInternalRef(EntryRef ref)
    {
        RefType iRef(ref);
        return _store.getBufferEntry<InternalNodeType>(iRef.bufferId(),
                                                       iRef.offset());
    }

    const LeafNodeType *
    mapLeafRef(EntryRef ref) const
    {
        RefType iRef(ref);
        return _store.getBufferEntry<LeafNodeType>(iRef.bufferId(),
                                                   iRef.offset());
    }

    LeafNodeType *
    mapLeafRef(EntryRef ref)
    {
        RefType iRef(ref);
        return _store.getBufferEntry<LeafNodeType>(iRef.bufferId(),
                                                   iRef.offset());
    }

    template <typename NodeType>
    const NodeType *
    mapRef(EntryRef ref) const
    {
        RefType iRef(ref);
        return _store.getBufferEntry<NodeType>(iRef.bufferId(),
                                               iRef.offset());
    }

    template <typename NodeType>
    NodeType *
    mapRef(EntryRef ref)
    {
        RefType iRef(ref);
        return _store.getBufferEntry<NodeType>(iRef.bufferId(),
                                               iRef.offset());
    }

    LeafNodeTypeRefPair
    allocNewLeafNode(void) {
        return _store.allocNewEntry<LeafNodeType>(NODETYPE_LEAF);
    }

    LeafNodeTypeRefPair
    allocLeafNode(void) {
        return _store.allocEntry<LeafNodeType, BTreeNodeReclaimer>(NODETYPE_LEAF);
    }

    LeafNodeTypeRefPair
    allocNewLeafNodeCopy(const LeafNodeType &rhs) {
        return _store.allocNewEntryCopy<LeafNodeType>(NODETYPE_LEAF, rhs);
    }

    LeafNodeTypeRefPair
    allocLeafNodeCopy(const LeafNodeType &rhs) {
        return _store.allocEntryCopy<LeafNodeType, BTreeNodeReclaimer>(NODETYPE_LEAF, rhs);
    }

    InternalNodeTypeRefPair
    allocNewInternalNode(void) {
        return _store.allocNewEntry<InternalNodeType>(NODETYPE_INTERNAL);
    }

    InternalNodeTypeRefPair
    allocInternalNode(void) {
        return _store.allocEntry<InternalNodeType, BTreeNodeReclaimer>(NODETYPE_INTERNAL);
    }

    InternalNodeTypeRefPair
    allocNewInternalNodeCopy(const InternalNodeType &rhs) {
        return _store.allocNewEntryCopy<InternalNodeType>(NODETYPE_INTERNAL, rhs);
    }

    InternalNodeTypeRefPair
    allocInternalNodeCopy(const InternalNodeType &rhs) {
        return _store.allocEntryCopy<InternalNodeType, BTreeNodeReclaimer>(NODETYPE_INTERNAL, rhs);
    }

    void
    holdElem(EntryRef ref)
    {
        _store.holdElem(ref, 1);
    }

    void
    freeElem(EntryRef ref)
    {
        _store.freeElem(ref, 1);
    }

    std::vector<uint32_t>
    startCompact(void);

    void
    finishCompact(const std::vector<uint32_t> &toHold);

    void
    transferHoldLists(generation_t generation)
    {
        _store.transferHoldLists(generation);
    }

    // Inherit doc from DataStoreBase
    datastore::DataStoreBase::MemStats getMemStats() const {
        return _store.getMemStats();
    }

    // Inherit doc from DataStoreBase
    void
    trimHoldLists(generation_t usedGen)
    {
        _store.trimHoldLists(usedGen);
    }

    void
    clearHoldLists(void)
    {
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
    void
    foreach_key(EntryRef ref, FunctionType func) const
    {
        if (!ref.valid())
            return;
        if (isLeafRef(ref)) {
            mapLeafRef(ref)->foreach_key(func);
        } else {
            mapInternalRef(ref)->foreach_key(*this, func);
        }
    }

    template <typename FunctionType>
    void
    foreach(EntryRef ref, FunctionType func) const
    {
        if (!ref.valid())
            return;
        if (isLeafRef(ref)) {
            mapLeafRef(ref)->foreach(func);
        } else {
            mapInternalRef(ref)->foreach(*this, func);
        }
    }
};

extern template class BTreeNodeStore<uint32_t, uint32_t,
                                     NoAggregated,
                                     BTreeDefaultTraits::INTERNAL_SLOTS,
                                     BTreeDefaultTraits::LEAF_SLOTS>;
extern template class BTreeNodeStore<uint32_t, BTreeNoLeafData,
                                     NoAggregated,
                                     BTreeDefaultTraits::INTERNAL_SLOTS,
                                     BTreeDefaultTraits::LEAF_SLOTS>;
extern template class BTreeNodeStore<uint32_t, int32_t,
                                     MinMaxAggregated,
                                     BTreeDefaultTraits::INTERNAL_SLOTS,
                                     BTreeDefaultTraits::LEAF_SLOTS>;

} // namespace btree

namespace datastore {

extern template
std::pair<EntryRefT<22>, btree::BTreeLeafNode<uint32_t, uint32_t, btree::NoAggregated> *>
DataStoreT<EntryRefT<22> >::
allocNewEntryCopy<btree::BTreeLeafNode<uint32_t,
                                uint32_t,
                                btree::NoAggregated> >
(uint32_t, const btree::BTreeLeafNode<uint32_t, uint32_t, btree::NoAggregated> &);

extern template
std::pair<EntryRefT<22>, btree::BTreeLeafNode<uint32_t, btree::BTreeNoLeafData,
                                       btree::NoAggregated> *>
DataStoreT<EntryRefT<22> >::
allocNewEntryCopy<btree::BTreeLeafNode<uint32_t, btree::BTreeNoLeafData, btree::NoAggregated> >(
        uint32_t,
        const btree::BTreeLeafNode<uint32_t, btree::BTreeNoLeafData, btree::NoAggregated> &);

extern template
std::pair<EntryRefT<22>, btree::BTreeInternalNode<uint32_t, btree::NoAggregated> *>
DataStoreT<EntryRefT<22> >::
allocNewEntryCopy<btree::BTreeInternalNode<uint32_t, btree::NoAggregated> >(
        uint32_t, const btree::BTreeInternalNode<uint32_t, btree::NoAggregated> &);

extern template
std::pair<EntryRefT<22>, btree::BTreeLeafNode<uint32_t, int32_t, btree::MinMaxAggregated> *>
DataStoreT<EntryRefT<22> >::
allocNewEntryCopy<btree::BTreeLeafNode<uint32_t,
                                int32_t,
                                btree::MinMaxAggregated> >
(uint32_t, const btree::BTreeLeafNode<uint32_t, int32_t, btree::MinMaxAggregated> &);

extern template
std::pair<EntryRefT<22>, btree::BTreeInternalNode<uint32_t, btree::MinMaxAggregated> *>
DataStoreT<EntryRefT<22> >::
allocNewEntryCopy<btree::BTreeInternalNode<uint32_t, btree::MinMaxAggregated> >(
        uint32_t, const btree::BTreeInternalNode<uint32_t, btree::MinMaxAggregated> &);

extern template
std::pair<EntryRefT<22>, btree::BTreeLeafNode<uint32_t, uint32_t, btree::NoAggregated> *>
DataStoreT<EntryRefT<22> >::
allocEntry<btree::BTreeLeafNode<uint32_t, uint32_t, btree::NoAggregated>,
           btree::BTreeNodeReclaimer>(uint32_t);

extern template
std::pair<EntryRefT<22>, btree::BTreeLeafNode<uint32_t, btree::BTreeNoLeafData,
                                       btree::NoAggregated> *>
DataStoreT<EntryRefT<22> >::
allocEntry<btree::BTreeLeafNode<uint32_t, btree::BTreeNoLeafData, btree::NoAggregated>,
           btree::BTreeNodeReclaimer>(uint32_t);

extern template
std::pair<EntryRefT<22>, btree::BTreeInternalNode<uint32_t, btree::NoAggregated> *>
DataStoreT<EntryRefT<22> >::
allocEntry<btree::BTreeInternalNode<uint32_t, btree::NoAggregated>,
           btree::BTreeNodeReclaimer>(uint32_t);

extern template
std::pair<EntryRefT<22>, btree::BTreeLeafNode<uint32_t, int32_t, btree::MinMaxAggregated> *>
DataStoreT<EntryRefT<22> >::
allocEntry<btree::BTreeLeafNode<uint32_t, int32_t, btree::MinMaxAggregated>,
           btree::BTreeNodeReclaimer>(uint32_t);

extern template
std::pair<EntryRefT<22>, btree::BTreeInternalNode<uint32_t, btree::MinMaxAggregated> *>
DataStoreT<EntryRefT<22> >::
allocEntry<btree::BTreeInternalNode<uint32_t, btree::MinMaxAggregated>,
           btree::BTreeNodeReclaimer>(uint32_t);

extern template
std::pair<EntryRefT<22>, btree::BTreeLeafNode<uint32_t, uint32_t, btree::NoAggregated> *>
DataStoreT<EntryRefT<22> >::
allocEntryCopy<btree::BTreeLeafNode<uint32_t, uint32_t, btree::NoAggregated>,
               btree::BTreeNodeReclaimer>(
                       uint32_t,
                       const btree::BTreeLeafNode<uint32_t, uint32_t,
                       btree::NoAggregated> &);

extern template
std::pair<EntryRefT<22>, btree::BTreeLeafNode<uint32_t, btree::BTreeNoLeafData,
                                       btree::NoAggregated> *>
DataStoreT<EntryRefT<22> >::
allocEntryCopy<btree::BTreeLeafNode<uint32_t, btree::BTreeNoLeafData, btree::NoAggregated>,
               btree::BTreeNodeReclaimer>(
                       uint32_t,
                       const btree::BTreeLeafNode<uint32_t, btree::BTreeNoLeafData,
                       btree::NoAggregated> &);

extern template
std::pair<EntryRefT<22>, btree::BTreeInternalNode<uint32_t, btree::NoAggregated> *>
DataStoreT<EntryRefT<22> >::
allocEntryCopy<btree::BTreeInternalNode<uint32_t, btree::NoAggregated>, btree::BTreeNodeReclaimer>(
        uint32_t, const btree::BTreeInternalNode<uint32_t, btree::NoAggregated> &);

extern template
std::pair<EntryRefT<22>, btree::BTreeLeafNode<uint32_t, int32_t, btree::MinMaxAggregated> *>
DataStoreT<EntryRefT<22> >::
allocEntryCopy<btree::BTreeLeafNode<uint32_t, int32_t, btree::MinMaxAggregated>,
               btree::BTreeNodeReclaimer>(
                       uint32_t,
                       const btree::BTreeLeafNode<uint32_t, int32_t,
                       btree::MinMaxAggregated> &);

extern template
std::pair<EntryRefT<22>, btree::BTreeInternalNode<uint32_t, btree::MinMaxAggregated> *>
DataStoreT<EntryRefT<22> >::
allocEntryCopy<btree::BTreeInternalNode<uint32_t, btree::MinMaxAggregated>,
               btree::BTreeNodeReclaimer>(
        uint32_t, const btree::BTreeInternalNode<uint32_t, btree::MinMaxAggregated> &);


} // namespace datastore

} // namespace search


