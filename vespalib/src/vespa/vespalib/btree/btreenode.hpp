// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreenode.h"
#include <algorithm>
#include <cassert>

namespace vespalib::btree {

namespace {

class SplitInsertHelper {
private:
    uint32_t _idx;
    uint32_t _median;
    bool     _medianBumped;
public:
    SplitInsertHelper(uint32_t idx, uint32_t validSlots) noexcept :
        _idx(idx),
        _median(validSlots / 2),
        _medianBumped(false)
    {
        if (idx > _median) {
            _median++;
            _medianBumped = true;
        }
    }
    uint32_t getMedian() const noexcept { return _median; }
    bool insertInSplitNode() const noexcept {
        if (_median >= _idx && !_medianBumped) {
            return false;
        }
        return true;
    }
};


}

template <typename KeyT, uint32_t NumSlots>
template <typename CompareT>
uint32_t
BTreeNodeT<KeyT, NumSlots>::
lower_bound(uint32_t sidx, const KeyT & key, CompareT comp) const noexcept
{
    const KeyT * itr = std::lower_bound<const KeyT *, KeyT, CompareT>
        (_keys + sidx, _keys + validSlots(), key, comp);
    return itr - _keys;
}

template <typename KeyT, uint32_t NumSlots>
template <typename CompareT>
uint32_t
BTreeNodeT<KeyT, NumSlots>::lower_bound(const KeyT & key, CompareT comp) const noexcept
{
    const KeyT * itr = std::lower_bound<const KeyT *, KeyT, CompareT>
        (_keys, _keys + validSlots(), key, comp);
    return itr - _keys;
}


template <typename KeyT, uint32_t NumSlots>
template <typename CompareT>
uint32_t
BTreeNodeT<KeyT, NumSlots>::
upper_bound(uint32_t sidx, const KeyT & key, CompareT comp) const noexcept
{
    const KeyT * itr = std::upper_bound<const KeyT *, KeyT, CompareT>
        (_keys + sidx, _keys + validSlots(), key, comp);
    return itr - _keys;
}


template <typename KeyT, typename DataT, typename AggrT, uint32_t NumSlots>
void
BTreeNodeTT<KeyT, DataT, AggrT, NumSlots>::insert(uint32_t idx, const KeyT &key, const DataT &data) noexcept
{
    assert(validSlots() < NodeType::maxSlots());
    assert(!getFrozen());
    for (uint32_t i = validSlots(); i > idx; --i) {
        _keys[i] = _keys[i - 1];
        setData(i, getData(i - 1));
    }
    _keys[idx] = key;
    setData(idx, data);
    _validSlots++;
}

template <typename KeyT, typename DataT, typename AggrT, uint32_t NumSlots>
void
BTreeNodeTT<KeyT, DataT, AggrT, NumSlots>::
splitInsert(NodeType *splitNode, uint32_t idx, const KeyT &key, const DataT &data) noexcept
{
    assert(!getFrozen());
    assert(!splitNode->getFrozen());
    SplitInsertHelper sih(idx, validSlots());
    splitNode->_validSlots = validSlots() - sih.getMedian();
    for (uint32_t i = sih.getMedian(); i < validSlots(); ++i) {
        splitNode->_keys[i - sih.getMedian()] = _keys[i];
        splitNode->setData(i - sih.getMedian(), getData(i));
    }
    cleanRange(sih.getMedian(), validSlots());
    _validSlots = sih.getMedian();
    if (sih.insertInSplitNode()) {
        splitNode->insert(idx - sih.getMedian(), key, data);
    } else {
        insert(idx, key, data);
    }
}

template <typename KeyT, typename DataT, typename AggrT, uint32_t NumSlots>
void
BTreeNodeTT<KeyT, DataT, AggrT, NumSlots>::remove(uint32_t idx) noexcept
{
    assert(!getFrozen());
    for (uint32_t i = idx + 1; i < validSlots(); ++i) {
        _keys[i - 1] = _keys[i];
        setData(i - 1, getData(i));
    }
    _validSlots--;
    _keys[validSlots()] = KeyT();
    setData(validSlots(), DataT());
}

template <typename KeyT, typename DataT, typename AggrT, uint32_t NumSlots>
void
BTreeNodeTT<KeyT, DataT, AggrT, NumSlots>::
stealAllFromLeftNode(const NodeType *victim) noexcept
{
    assert(validSlots() + victim->validSlots() <= NodeType::maxSlots());
    assert(!getFrozen());
    for (int i = validSlots() - 1; i >= 0; --i) {
        _keys[i + victim->validSlots()] = _keys[i];
        setData(i + victim->validSlots(), getData(i));
    }
    for (uint32_t i = 0; i < victim->validSlots(); ++i) {
        _keys[i] = victim->_keys[i];
        setData(i, victim->getData(i));
    }
    _validSlots += victim->validSlots();
}

template <typename KeyT, typename DataT, typename AggrT, uint32_t NumSlots>
void
BTreeNodeTT<KeyT, DataT, AggrT, NumSlots>::
stealAllFromRightNode(const NodeType *victim) noexcept
{
    assert(validSlots() + victim->validSlots() <= NodeType::maxSlots());
    assert(!getFrozen());
    for (uint32_t i = 0; i < victim->validSlots(); ++i) {
        _keys[validSlots() + i] = victim->_keys[i];
        setData(validSlots() + i, victim->getData(i));
    }
    _validSlots += victim->validSlots();
}

template <typename KeyT, typename DataT, typename AggrT, uint32_t NumSlots>
void
BTreeNodeTT<KeyT, DataT, AggrT, NumSlots>::
stealSomeFromLeftNode(NodeType *victim) noexcept
{
    assert(validSlots() + victim->validSlots() >= NodeType::minSlots());
    assert(!getFrozen());
    assert(!victim->getFrozen());
    uint32_t median = (validSlots() + victim->validSlots() + 1) / 2;
    uint32_t steal = median - validSlots();
    _validSlots += steal;
    for (int32_t i = validSlots() - 1; i >= static_cast<int32_t>(steal); --i) {
        _keys[i] = _keys[i - steal];
        setData(i, getData(i - steal));
    }
    for (uint32_t i = 0; i < steal; ++i) {
        _keys[i] = victim->_keys[victim->validSlots() - steal + i];
        setData(i, victim->getData(victim->validSlots() - steal + i));
    }
    victim->cleanRange(victim->validSlots() - steal, victim->validSlots());
    victim->_validSlots -= steal;
}

template <typename KeyT, typename DataT, typename AggrT, uint32_t NumSlots>
void
BTreeNodeTT<KeyT, DataT, AggrT, NumSlots>::
stealSomeFromRightNode(NodeType *victim) noexcept
{
    assert(validSlots() + victim->validSlots() >= NodeType::minSlots());
    assert(!getFrozen());
    assert(!victim->getFrozen());
    uint32_t median = (validSlots() + victim->validSlots() + 1) / 2;
    uint32_t steal = median - validSlots();
    for (uint32_t i = 0; i < steal; ++i) {
        _keys[validSlots() + i] = victim->_keys[i];
        setData(validSlots() + i, victim->getData(i));
    }
    _validSlots += steal;
    for (uint32_t i = steal; i < victim->validSlots(); ++i) {
        victim->_keys[i - steal] = victim->_keys[i];
        victim->setData(i - steal, victim->getData(i));
    }
    victim->cleanRange(victim->validSlots() - steal, victim->validSlots());
    victim->_validSlots -= steal;
}


template <typename KeyT, typename DataT, typename AggrT, uint32_t NumSlots>
void
BTreeNodeTT<KeyT, DataT, AggrT, NumSlots>::cleanRange(uint32_t from, uint32_t to) noexcept
{
    assert(from < to);
    assert(to <= validSlots());
    assert(validSlots() <= NodeType::maxSlots());
    assert(!getFrozen());
    KeyT emptyKey = KeyT();
    for (KeyT *k = _keys + from, *ke = _keys + to; k != ke; ++k)
        *k = emptyKey;
    DataT emptyData = DataT();
    for (uint32_t i = from; i != to; ++i)
        setData(i, emptyData);
}


template <typename KeyT, typename DataT, typename AggrT, uint32_t NumSlots>
void
BTreeNodeTT<KeyT, DataT, AggrT, NumSlots>::clean() noexcept
{
    if (validSlots() == 0)
        return;
    cleanRange(0, validSlots());
    _validSlots = 0;
}


template <typename KeyT, typename DataT, typename AggrT, uint32_t NumSlots>
void
BTreeNodeTT<KeyT, DataT, AggrT, NumSlots>::cleanFrozen() noexcept
{
    assert(validSlots() <= NodeType::maxSlots());
    assert(getFrozen());
    if (validSlots() == 0)
        return;
    KeyT emptyKey = KeyT();
    for (KeyT *k = _keys, *ke = _keys + validSlots(); k != ke; ++k)
        *k = emptyKey;
    DataT emptyData = DataT();
    for (uint32_t i = 0, ie = validSlots(); i != ie; ++i)
        setData(i, emptyData);
    _validSlots = 0;
}


template <typename KeyT, typename AggrT, uint32_t NumSlots>
template <typename NodeAllocatorType>
void
BTreeInternalNode<KeyT, AggrT, NumSlots>::
splitInsert(BTreeInternalNode *splitNode, uint32_t idx, const KeyT &key,
            const BTreeNode::Ref &data, NodeAllocatorType &allocator) noexcept
{
    assert(!getFrozen());
    assert(!splitNode->getFrozen());
    SplitInsertHelper sih(idx, validSlots());
    splitNode->_validSlots = validSlots() - sih.getMedian();
    uint32_t splitLeaves = 0;
    uint32_t newLeaves = allocator.validLeaves(data);
    for (uint32_t i = sih.getMedian(); i < validSlots(); ++i) {
        splitNode->_keys[i - sih.getMedian()] = _keys[i];
        splitNode->setData(i - sih.getMedian(), getData(i));
        splitLeaves += allocator.validLeaves(getChild(i));
    }
    splitNode->_validLeaves = splitLeaves;
    this->cleanRange(sih.getMedian(), validSlots());
    _validLeaves -= splitLeaves + newLeaves;
    _validSlots = sih.getMedian();
    if (sih.insertInSplitNode()) {
        splitNode->insert(idx - sih.getMedian(), key, data);
        splitNode->_validLeaves += newLeaves;
    } else {
        this->insert(idx, key, data);
        _validLeaves += newLeaves;
    }
}


template <typename KeyT, typename AggrT, uint32_t NumSlots>
void
BTreeInternalNode<KeyT, AggrT, NumSlots>::
stealAllFromLeftNode(const BTreeInternalNode *victim) noexcept
{
    ParentType::stealAllFromLeftNode(victim);
    _validLeaves += victim->_validLeaves;
}

template <typename KeyT, typename AggrT, uint32_t NumSlots>
void
BTreeInternalNode<KeyT, AggrT, NumSlots>::
stealAllFromRightNode(const BTreeInternalNode *victim) noexcept
{
    ParentType::stealAllFromRightNode(victim);
    _validLeaves += victim->_validLeaves;
}

template <typename KeyT, typename AggrT, uint32_t NumSlots>
template <typename NodeAllocatorType>
uint32_t
BTreeInternalNode<KeyT, AggrT, NumSlots>::countValidLeaves(uint32_t start, uint32_t end, NodeAllocatorType &allocator) noexcept
{
    assert(start <= end);
    assert(end <= validSlots());
    uint32_t leaves = 0;
    for (uint32_t i = start; i < end; ++i) {
        leaves += allocator.validLeaves(getChild(i));
    }
    return leaves;
}

template <typename KeyT, typename AggrT, uint32_t NumSlots>
template <typename NodeAllocatorType>
void
BTreeInternalNode<KeyT, AggrT, NumSlots>::
stealSomeFromLeftNode(BTreeInternalNode *victim, NodeAllocatorType &allocator) noexcept
{
    uint16_t oldValidSlots = validSlots();
    ParentType::stealSomeFromLeftNode(victim);
    uint32_t stolenLeaves = countValidLeaves(0, validSlots() - oldValidSlots, allocator);
    incValidLeaves(stolenLeaves);
    victim->decValidLeaves(stolenLeaves);
}


template <typename KeyT, typename AggrT, uint32_t NumSlots>
template <typename NodeAllocatorType>
void
BTreeInternalNode<KeyT, AggrT, NumSlots>::
stealSomeFromRightNode(BTreeInternalNode *victim, NodeAllocatorType &allocator) noexcept
{
    uint16_t oldValidSlots = validSlots();
    ParentType::stealSomeFromRightNode(victim);
    uint32_t stolenLeaves = countValidLeaves(oldValidSlots, validSlots(), allocator);
    incValidLeaves(stolenLeaves);
    victim->decValidLeaves(stolenLeaves);
}


template <typename KeyT, typename AggrT, uint32_t NumSlots>
void
BTreeInternalNode<KeyT, AggrT, NumSlots>::clean() noexcept
{
    ParentType::clean();
    _validLeaves = 0;
}


template <typename KeyT, typename AggrT, uint32_t NumSlots>
void
BTreeInternalNode<KeyT, AggrT, NumSlots>::cleanFrozen() noexcept
{
    ParentType::cleanFrozen();
    _validLeaves = 0;
}

template <typename KeyT, typename AggrT, uint32_t NumSlots>
template <typename NodeStoreType, typename FunctionType>
void
BTreeInternalNode<KeyT, AggrT, NumSlots>::foreach_key(NodeStoreType &store, FunctionType func) const noexcept {
    const BTreeNode::ChildRef *it = this->_data;
    const BTreeNode::ChildRef *ite = it + _validSlots;
    if (this->getLevel() > 1u) {
        for (; it != ite; ++it) {
            store.mapInternalRef(it->load_acquire())->foreach_key(store, func);
        }
    } else {
        for (; it != ite; ++it) {
            store.mapLeafRef(it->load_acquire())->foreach_key(func);
        }
    }
}

/**
 * Call func with leaf entry key value as argument for all leaf entries in subtrees
 * for children [start_idx, end_idx).
 */
template <typename KeyT, typename AggrT, uint32_t NumSlots>
template <typename NodeStoreType, typename FunctionType>
void
BTreeInternalNode<KeyT, AggrT, NumSlots>::foreach_key_range(NodeStoreType &store, uint32_t start_idx, uint32_t end_idx, FunctionType func) const noexcept {
    const BTreeNode::ChildRef *it = this->_data;
    const BTreeNode::ChildRef *ite = it + end_idx;
    it += start_idx;
    if (this->getLevel() > 1u) {
        for (; it != ite; ++it) {
            store.mapInternalRef(it->load_acquire())->foreach_key(store, func);
        }
    } else {
        for (; it != ite; ++it) {
            store.mapLeafRef(it->load_acquire())->foreach_key(func);
        }
    }
}

template <typename KeyT, typename AggrT, uint32_t NumSlots>
template <typename NodeStoreType, typename FunctionType>
void
BTreeInternalNode<KeyT, AggrT, NumSlots>::foreach(NodeStoreType &store, FunctionType func) const noexcept {
    const BTreeNode::ChildRef *it = this->_data;
    const BTreeNode::ChildRef *ite = it + _validSlots;
    if (this->getLevel() > 1u) {
        for (; it != ite; ++it) {
            store.mapInternalRef(it->load_acquire())->foreach(store, func);
        }
    } else {
        for (; it != ite; ++it) {
            store.mapLeafRef(it->load_acquire())->foreach(func);
        }
    }
}


template <typename KeyT, typename DataT, typename AggrT, uint32_t NumSlots>
BTreeLeafNode<KeyT, DataT, AggrT, NumSlots>::
BTreeLeafNode(const KeyDataType *smallArray, uint32_t arraySize) noexcept
    : ParentType(LEAF_LEVEL)
{
    assert(arraySize <= BTreeLeafNode::maxSlots());
    _validSlots = arraySize;
    for (uint32_t idx = 0; idx < arraySize; ++idx) {
        _keys[idx] = smallArray[idx]._key;
        this->setData(idx, smallArray[idx].getData());
    }
    freeze();
}


template <typename KeyT, typename DataT, typename AggrT, uint32_t NumSlots>
template <typename FunctionType>
void
BTreeLeafNode<KeyT, DataT, AggrT, NumSlots>::foreach_key(FunctionType func) const noexcept {
    const KeyT *it = _keys;
    const KeyT *ite = it + _validSlots;
    for (; it != ite; ++it) {
        func(*it);
    }
}

/**
 * Call func with leaf entry key value as argument for leaf entries [start_idx, end_idx).
 */
template <typename KeyT, typename DataT, typename AggrT, uint32_t NumSlots>
template <typename FunctionType>
void
BTreeLeafNode<KeyT, DataT, AggrT, NumSlots>::foreach_key_range(uint32_t start_idx, uint32_t end_idx, FunctionType func) const noexcept {
    const KeyT *it = _keys;
    const KeyT *ite = it + end_idx;
    it += start_idx;
    for (; it != ite; ++it) {
        func(*it);
    }
}

template <typename KeyT, typename DataT, typename AggrT, uint32_t NumSlots>
template <typename FunctionType>
void
BTreeLeafNode<KeyT, DataT, AggrT, NumSlots>::foreach(FunctionType func) const noexcept {
    const KeyT *it = _keys;
    const KeyT *ite = it + _validSlots;
    uint32_t idx = 0;
    for (; it != ite; ++it) {
        func(*it, this->getData(idx++));
    }
}

}
