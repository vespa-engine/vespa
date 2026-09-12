// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "postingstore.h"

#include <vespa/searchlib/common/growablebitvector.h>
#include <vespa/vespalib/util/doom.h>

namespace search::attribute {

template <typename DataT>
template <typename FunctionType>
void PostingStore<DataT>::foreach_frozen_key(EntryRef ref, FunctionType func, const vespalib::Doom& doom) const {
    constexpr size_t chunk_size = 4096;
    if (doom.soft_doom()) {
        return;
    }
    if (frozenSize(ref) <= chunk_size) {
        foreach_frozen_key(ref, func);
    } else if (has_btree(ref)) {
        auto it = beginFrozen(ref);
        while (it.valid() && !doom.soft_doom()) {
            auto end = it;
            end += chunk_size;
            it.foreach_key_range(end, func);
            it = end;
        }
    } else {
        const auto& bv = getBitVectorEntry(RefType(ref))->_bv->reader();
        for (uint32_t docid = bv.getFirstTrueBit(1); docid < bv.size() && !doom.soft_doom();
             docid = bv.getNextTrueBit(docid + 1))
        {
            func(docid);
        }
    }
}

template <typename DataT>
template <typename FunctionType>
void PostingStore<DataT>::foreach_frozen_key(EntryRef ref, FunctionType func) const {
    if (!ref.valid())
        return;
    RefType  iRef(ref);
    uint32_t typeId = getTypeId(iRef);
    uint32_t clusterSize = getClusterSize(typeId);
    if (clusterSize == 0) {
        if (isBitVector(typeId)) {
            const BitVectorEntry* bve = getBitVectorEntry(iRef);
            EntryRef              ref2(bve->_tree);
            RefType               iRef2(ref2);
            if (iRef2.valid()) {
                assert(isBTree(iRef2));
                const BTreeType* tree = getTreeEntry(iRef2);
                _allocator.getNodeStore().foreach_key(tree->getFrozenRoot(), func);
            } else {
                const BitVector* bv = &bve->_bv->reader();
                uint32_t         docIdLimit = bv->size();
                uint32_t         docId = bv->getFirstTrueBit(1);
                while (docId < docIdLimit) {
                    func(docId);
                    docId = bv->getNextTrueBit(docId + 1);
                }
            }
        } else {
            assert(isBTree(typeId));
            const BTreeType* tree = getTreeEntry(iRef);
            _allocator.getNodeStore().foreach_key(tree->getFrozenRoot(), func);
        }
    } else {
        const KeyDataType* p = getKeyDataEntry(iRef, clusterSize);
        const KeyDataType* pe = p + clusterSize;
        for (; p != pe; ++p) {
            func(p->_key);
        }
    }
}

template <typename DataT>
template <typename FunctionType>
void PostingStore<DataT>::foreach_frozen(EntryRef ref, FunctionType func) const {
    if (!ref.valid())
        return;
    RefType  iRef(ref);
    uint32_t typeId = getTypeId(iRef);
    uint32_t clusterSize = getClusterSize(typeId);
    if (clusterSize == 0) {
        if (isBitVector(typeId)) {
            const BitVectorEntry* bve = getBitVectorEntry(iRef);
            EntryRef              ref2(bve->_tree);
            RefType               iRef2(ref2);
            if (iRef2.valid()) {
                assert(isBTree(iRef2));
                const BTreeType* tree = getTreeEntry(iRef2);
                _allocator.getNodeStore().foreach(tree->getFrozenRoot(), func);
            } else {
                const BitVector* bv = &bve->_bv->reader();
                uint32_t         docIdLimit = bv->size();
                uint32_t         docId = bv->getFirstTrueBit(1);
                while (docId < docIdLimit) {
                    func(docId, bitVectorWeight());
                    docId = bv->getNextTrueBit(docId + 1);
                }
            }
        } else {
            const BTreeType* tree = getTreeEntry(iRef);
            _allocator.getNodeStore().foreach(tree->getFrozenRoot(), func);
        }
    } else {
        const KeyDataType* p = getKeyDataEntry(iRef, clusterSize);
        const KeyDataType* pe = p + clusterSize;
        for (; p != pe; ++p) {
            func(p->_key, p->getData());
        }
    }
}

} // namespace search::attribute
