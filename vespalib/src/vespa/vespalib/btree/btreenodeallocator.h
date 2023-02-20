// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreenode.h"
#include "btreenodestore.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <vector>

namespace vespalib::btree {

template <typename, typename, typename, size_t, size_t> class BTreeRootBase;

template <typename KeyT,
          typename DataT,
          typename AggrT,
          size_t INTERNAL_SLOTS,
          size_t LEAF_SLOTS>
class BTreeNodeAllocator
{
public:
    using InternalNodeType = BTreeInternalNode<KeyT, AggrT, INTERNAL_SLOTS>;
    using LeafNodeType = BTreeLeafNode<KeyT, DataT, AggrT, LEAF_SLOTS>;
    using InternalNodeTypeRefPair = typename InternalNodeType::RefPair;
    using LeafNodeTypeRefPair = typename LeafNodeType::RefPair;
    using BTreeRootBaseType = BTreeRootBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>;
    using generation_t = vespalib::GenerationHandler::generation_t;
    using NodeStore = BTreeNodeStore<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>;
    using CompactionStrategy = datastore::CompactionStrategy;
    using EntryRef = datastore::EntryRef;
    using DataStoreBase = datastore::DataStoreBase;

private:
    BTreeNodeAllocator(const BTreeNodeAllocator &rhs);

    BTreeNodeAllocator & operator=(const BTreeNodeAllocator &rhs);

    NodeStore _nodeStore;

    using RefVector = vespalib::Array<BTreeNode::Ref>;
    using BTreeRootBaseTypeVector = vespalib::Array<BTreeRootBaseType *>;

    // Nodes that might not be frozen.
    RefVector _internalToFreeze;
    RefVector _leafToFreeze;
    BTreeRootBaseTypeVector _treeToFreeze;

    // Nodes held until freeze is performed
    RefVector _internalHoldUntilFreeze;
    RefVector _leafHoldUntilFreeze;

public:
    BTreeNodeAllocator();
    ~BTreeNodeAllocator();

    void disableFreeLists() {
        _nodeStore.disableFreeLists();
    }

    void disableElemHoldList() {
        _nodeStore.disableElemHoldList();
    }

    /**
     * Allocate internal node.
     */
    InternalNodeTypeRefPair allocInternalNode(uint8_t level);

    /*
     * Allocate leaf node.
     */
    LeafNodeTypeRefPair allocLeafNode();
    InternalNodeTypeRefPair thawNode(BTreeNode::Ref nodeRef, InternalNodeType *node);
    LeafNodeTypeRefPair thawNode(BTreeNode::Ref nodeRef, LeafNodeType *node);
    BTreeNode::Ref thawNode(BTreeNode::Ref node);

    /**
     * hold internal node until freeze/generation constraint is satisfied.
     */
    void holdNode(BTreeNode::Ref nodeRef, InternalNodeType *node);

    /**
     * hold leaf node until freeze/generation constraint is satisfied.
     */
    void holdNode(BTreeNode::Ref nodeRef, LeafNodeType *node);

    /**
     * Mark that tree needs to be frozen.  Tree must be kept alive until
     * freeze operation has completed.
     */
    void needFreeze(BTreeRootBaseType *tree);

    /**
     * Freeze all nodes that are not already frozen.
     */
    void freeze();

    /**
     * Try to free held nodes if nobody can be referencing them.
     */
    void reclaim_memory(generation_t oldest_used_gen);

    /**
     * Transfer nodes from hold1 lists to hold2 lists, they are no
     * longer referenced by new frozen structures, but readers accessing
     * older versions of the frozen structure must leave before elements
     * can be unheld.
     */
    void assign_generation(generation_t current_gen);

    void reclaim_all_memory();

    static bool isValidRef(BTreeNode::Ref ref) { return NodeStore::isValidRef(ref); }

    bool isLeafRef(BTreeNode::Ref ref) const {
        if (!isValidRef(ref))
            return false;
        return _nodeStore.isLeafRef(ref);
    }

    const InternalNodeType *mapInternalRef(BTreeNode::Ref ref) const {
        return _nodeStore.mapInternalRef(ref);
    }

    InternalNodeType *mapInternalRef(BTreeNode::Ref ref) {
        return _nodeStore.mapInternalRef(ref);
    }

    const LeafNodeType *mapLeafRef(BTreeNode::Ref ref) const {
        return _nodeStore.mapLeafRef(ref);
    }

    LeafNodeType *mapLeafRef(BTreeNode::Ref ref) {
        return _nodeStore.mapLeafRef(ref);
    }

    template <typename NodeType>
    const NodeType *mapRef(BTreeNode::Ref ref) const {
        return _nodeStore.template mapRef<NodeType>(ref);
    }

    template <typename NodeType>
    NodeType *mapRef(BTreeNode::Ref ref) {
        return _nodeStore.template mapRef<NodeType>(ref);
    }

    InternalNodeTypeRefPair moveInternalNode(const InternalNodeType *node);
    LeafNodeTypeRefPair moveLeafNode(const LeafNodeType *node);
    uint32_t validLeaves(BTreeNode::Ref ref) const;

    /*
     * Extract level from ref.
     */
    uint32_t getLevel(BTreeNode::Ref ref) const;
    const KeyT &getLastKey(BTreeNode::Ref node) const;
    const AggrT &getAggregated(BTreeNode::Ref node) const;

    vespalib::MemoryUsage getMemoryUsage() const;

    vespalib::string toString(BTreeNode::Ref ref) const;
    vespalib::string toString(const BTreeNode * node) const;

    bool getCompacting(EntryRef ref) const { return _nodeStore.getCompacting(ref); }

    std::unique_ptr<vespalib::datastore::CompactingBuffers> start_compact_worst(const CompactionStrategy& compaction_strategy) { return _nodeStore.start_compact_worst(compaction_strategy); }

    template <typename FunctionType>
    void foreach_key(EntryRef ref, FunctionType func) const {
        _nodeStore.foreach_key(ref, func);
    }

    template <typename FunctionType>
    void foreach(EntryRef ref, FunctionType func) const {
        _nodeStore.foreach(ref, func);
    }

    const NodeStore &getNodeStore() const { return _nodeStore; }
};

extern template class BTreeNodeAllocator<uint32_t, uint32_t, NoAggregated,
                                         BTreeDefaultTraits::INTERNAL_SLOTS,
                                         BTreeDefaultTraits::LEAF_SLOTS>;
extern template class BTreeNodeAllocator<uint32_t, BTreeNoLeafData, NoAggregated,
                                         BTreeDefaultTraits::INTERNAL_SLOTS,
                                         BTreeDefaultTraits::LEAF_SLOTS>;
extern template class BTreeNodeAllocator<uint32_t, int32_t, MinMaxAggregated,
                                         BTreeDefaultTraits::INTERNAL_SLOTS,
                                         BTreeDefaultTraits::LEAF_SLOTS>;

}

