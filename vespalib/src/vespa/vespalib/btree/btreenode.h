// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "noaggregated.h"
#include "minmaxaggregated.h"
#include "btree_key_data.h"
#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <vespa/vespalib/datastore/handle.h>
#include <cassert>
#include <type_traits>
#include <utility>
#include <cstddef>

namespace vespalib::datastore {

template <typename, typename> class Allocator;
template <typename, typename> class BufferType;

namespace allocator {
template <typename, typename ...> struct Assigner;
}

}

namespace vespalib::btree {

template <typename, typename, typename, size_t, size_t> class BTreeNodeAllocator;
template <typename, typename, typename, size_t, size_t> class BTreeNodeStore;

class NoAggregated;

class BTreeNode {
private:
    uint8_t _level;
    bool _isFrozen;
public:
    static constexpr uint8_t EMPTY_LEVEL = 255;
    static constexpr uint8_t LEAF_LEVEL = 0;
protected:
    uint16_t _validSlots;
    BTreeNode(uint8_t level) noexcept
        : _level(level),
          _isFrozen(false),
          _validSlots(0)
    {}

    BTreeNode(const BTreeNode &rhs) noexcept
        : _level(rhs._level),
          _isFrozen(rhs._isFrozen),
          _validSlots(rhs._validSlots)
    {}

    BTreeNode &
    operator=(const BTreeNode &rhs) noexcept
    {
        assert(!_isFrozen);
        _level = rhs._level;
        _isFrozen = rhs._isFrozen;
        _validSlots = rhs._validSlots;
        return *this;
    }

    ~BTreeNode() { assert(_isFrozen); }

public:
    using Ref = datastore::EntryRef;
    using ChildRef = datastore::AtomicEntryRef;

    bool isLeaf() const { return _level == 0u; }
    bool getFrozen() const { return _isFrozen; }
    void freeze() { _isFrozen = true; }
    void unFreeze() { _isFrozen = false; }
    void setLevel(uint8_t level) { _level = level; }
    uint32_t getLevel() const { return _level; }
    uint32_t validSlots() const { return _validSlots; }
    void setValidSlots(uint16_t validSlots_) { _validSlots = validSlots_; }
};


/**
 * Use of BTreeNoLeafData class triggers the below partial
 * specialization of BTreeNodeDataWrap to prevent unneeded storage
 * overhead.
 */
template <class DataT, uint32_t NumSlots>
class BTreeNodeDataWrap
{
public:
    DataT _data[NumSlots];

    BTreeNodeDataWrap() noexcept : _data() {}
    ~BTreeNodeDataWrap() = default;

    void copyData(const BTreeNodeDataWrap &rhs, uint32_t validSlots) {
        const DataT *rdata = rhs._data;
        DataT *ldata = _data;
        DataT *ldatae = _data + validSlots;
        for (; ldata != ldatae; ++ldata, ++rdata)
            *ldata = *rdata;
    }

    const DataT &getData(uint32_t idx) const noexcept { return _data[idx]; }
    // Only use during compaction when changing reference to moved value
    DataT &getWData(uint32_t idx) noexcept { return _data[idx]; }
    void setData(uint32_t idx, const DataT &data) noexcept { _data[idx] = data; }
    static bool hasData() noexcept { return true; }
};


template <uint32_t NumSlots>
class BTreeNodeDataWrap<BTreeNoLeafData, NumSlots>
{
public:
    BTreeNodeDataWrap() noexcept {}

    void copyData(const BTreeNodeDataWrap &rhs, uint32_t validSlots) {
        (void) rhs;
        (void) validSlots;
    }

    const BTreeNoLeafData &getData(uint32_t idx) const {
        (void) idx;
        return BTreeNoLeafData::_instance;
    }

    // Only use during compaction when changing reference to moved value
    BTreeNoLeafData &getWData(uint32_t) const { return BTreeNoLeafData::_instance; }

    void setData(uint32_t idx, const BTreeNoLeafData &data) {
        (void) idx;
        (void) data;
    }

    static bool hasData() { return false; }
};


template <typename AggrT>
class BTreeNodeAggregatedWrap
{
    using AggregatedType = AggrT;

    AggrT _aggr;
    static AggrT _instance;

public:
    BTreeNodeAggregatedWrap() noexcept
        : _aggr()
    {}
    AggrT &getAggregated() { return _aggr; }
    const AggrT &getAggregated() const { return _aggr; }
    static const AggrT &getEmptyAggregated() { return _instance; }
};


template <>
class BTreeNodeAggregatedWrap<NoAggregated>
{
    using AggregatedType = NoAggregated;

    static NoAggregated _instance;
public:
    BTreeNodeAggregatedWrap() noexcept {}

    NoAggregated &getAggregated() { return _instance; }
    const NoAggregated &getAggregated() const { return _instance; }
    static const NoAggregated &getEmptyAggregated() { return _instance; }
};

template <> MinMaxAggregated BTreeNodeAggregatedWrap<MinMaxAggregated>::_instance;

template <typename KeyT, uint32_t NumSlots>
class BTreeNodeT : public BTreeNode {
protected:
    KeyT _keys[NumSlots];
    BTreeNodeT(uint8_t level) noexcept
        : BTreeNode(level),
          _keys()
    {}

    ~BTreeNodeT() = default;

    BTreeNodeT(const BTreeNodeT &rhs) noexcept
        : BTreeNode(rhs)
    {
        const KeyT *rkeys = rhs._keys;
        KeyT *lkeys = _keys;
        KeyT *lkeyse = _keys + _validSlots;
        for (; lkeys != lkeyse; ++lkeys, ++rkeys)
            *lkeys = *rkeys;
    }

    BTreeNodeT &
    operator=(const BTreeNodeT &rhs) noexcept
    {
        BTreeNode::operator=(rhs);
        const KeyT *rkeys = rhs._keys;
        KeyT *lkeys = _keys;
        KeyT *lkeyse = _keys + _validSlots;
        for (; lkeys != lkeyse; ++lkeys, ++rkeys)
            *lkeys = *rkeys;
        return *this;
    }

public:
    const KeyT & getKey(uint32_t idx) const noexcept { return _keys[idx]; }
    const KeyT & getLastKey() const noexcept { return _keys[validSlots() - 1]; }
    void writeKey(uint32_t idx, const KeyT & key) noexcept {
        if constexpr (std::is_same_v<KeyT, vespalib::datastore::AtomicEntryRef>) {
            _keys[idx].store_release(key.load_relaxed());
        } else {
            _keys[idx] = key;
        }
    }
    void write_key_relaxed(uint32_t idx, const KeyT & key) noexcept { _keys[idx] = key; }

    template <typename CompareT>
    uint32_t lower_bound(uint32_t sidx, const KeyT & key, CompareT comp) const;

    template <typename CompareT>
    uint32_t lower_bound(const KeyT & key, CompareT comp) const;

    template <typename CompareT>
    uint32_t upper_bound(uint32_t sidx, const KeyT & key, CompareT comp) const;

    bool isFull() const noexcept { return validSlots() == NumSlots; }
    bool isAtLeastHalfFull() const noexcept { return validSlots() >= minSlots(); }
    static constexpr uint32_t maxSlots() noexcept { return NumSlots; }
    static constexpr uint32_t minSlots() noexcept { return NumSlots / 2; }
};

template <typename KeyT, typename DataT, typename AggrT, uint32_t NumSlots>
class BTreeNodeTT : public BTreeNodeT<KeyT, NumSlots>,
                    public BTreeNodeDataWrap<DataT, NumSlots>,
                    public BTreeNodeAggregatedWrap<AggrT>
{
public:
    using ParentType = BTreeNodeT<KeyT, NumSlots>;
    using DataWrapType = BTreeNodeDataWrap<DataT, NumSlots>;
    using AggrWrapType = BTreeNodeAggregatedWrap<AggrT>;
    using ParentType::_validSlots;
    using ParentType::validSlots;
    using ParentType::getFrozen;
    using ParentType::_keys;
    using DataWrapType::getData;
    using DataWrapType::setData;
    using DataWrapType::copyData;
protected:
    BTreeNodeTT(uint8_t level) noexcept
        : ParentType(level),
          DataWrapType()
    {}

    ~BTreeNodeTT() = default;

    BTreeNodeTT(const BTreeNodeTT &rhs) noexcept
        : ParentType(rhs),
          DataWrapType(rhs),
          AggrWrapType(rhs)
    {
        copyData(rhs, _validSlots);
    }

    BTreeNodeTT &operator=(const BTreeNodeTT &rhs) noexcept {
        ParentType::operator=(rhs);
        AggrWrapType::operator=(rhs);
        copyData(rhs, _validSlots);
        return *this;
    }

public:
    using NodeType = BTreeNodeTT<KeyT, DataT, AggrT, NumSlots>;
    void insert(uint32_t idx, const KeyT & key, const DataT & data);
    void update(uint32_t idx, const KeyT & key, const DataT & data) {
        // assert(idx < NodeType::maxSlots());
        // assert(!getFrozen());
        _keys[idx] = key;
        setData(idx, data);
    }
    void splitInsert(NodeType * splitNode, uint32_t idx, const KeyT & key, const DataT & data);
    void remove(uint32_t idx);
    void stealAllFromLeftNode(const NodeType * victim);
    void stealAllFromRightNode(const NodeType * victim);
    void stealSomeFromLeftNode(NodeType * victim);
    void stealSomeFromRightNode(NodeType * victim);
    void cleanRange(uint32_t from, uint32_t to);
    void clean();
    void cleanFrozen();
};

template <typename KeyT, typename AggrT, uint32_t NumSlots = 16>
class BTreeInternalNode : public BTreeNodeTT<KeyT, BTreeNode::ChildRef, AggrT, NumSlots>
{
public:
    using ParentType = BTreeNodeTT<KeyT, BTreeNode::ChildRef, AggrT, NumSlots>;
    using InternalNodeType = BTreeInternalNode<KeyT, AggrT, NumSlots>;
    template <typename, typename, typename, size_t, size_t>
    friend class BTreeNodeAllocator;
    template <typename, typename, typename, size_t, size_t>
    friend class BTreeNodeStore;
    template <typename, uint32_t>
    friend class BTreeNodeDataWrap;
    template <typename, typename>
    friend class datastore::BufferType;
    template <typename, typename>
    friend class datastore::Allocator;
    template <typename, typename...>
    friend struct datastore::allocator::Assigner;
    using Ref = BTreeNode::Ref;
    using RefPair = datastore::Handle<InternalNodeType>;
    using ParentType::_keys;
    using ParentType::_data;
    using ParentType::validSlots;
    using ParentType::_validSlots;
    using ParentType::getFrozen;
    using ParentType::getData;
    using ParentType::insert;
    using ParentType::setData;
    using ParentType::setLevel;
    using ParentType::update;
    using ParentType::EMPTY_LEVEL;
    using KeyType = KeyT;
    using DataType = Ref;
private:
    uint32_t _validLeaves;
protected:
    BTreeInternalNode() noexcept
        : ParentType(EMPTY_LEVEL),
          _validLeaves(0u)
    {}

    BTreeInternalNode(const BTreeInternalNode &rhs) noexcept
        : ParentType(rhs),
          _validLeaves(rhs._validLeaves)
    {}

    ~BTreeInternalNode() = default;

    BTreeInternalNode &operator=(const BTreeInternalNode &rhs) noexcept {
        ParentType::operator=(rhs);
        _validLeaves = rhs._validLeaves;
        return *this;
    }
private:
    template <typename NodeAllocatorType>
    uint32_t countValidLeaves(uint32_t start, uint32_t end, NodeAllocatorType &allocator);

public:
    BTreeNode::Ref getChild(uint32_t idx) const { return _data[idx].load_acquire(); }
    BTreeNode::Ref get_child_relaxed(uint32_t idx) const { return _data[idx].load_relaxed(); }
    void setChild(uint32_t idx, BTreeNode::Ref child) { _data[idx].store_release(child); }
    void set_child_relaxed(uint32_t idx, BTreeNode::Ref child) { _data[idx].store_relaxed(child); }
    BTreeNode::Ref get_last_child_relaxed() const { return get_child_relaxed(validSlots() - 1); }
    void update(uint32_t idx, const KeyT & key, BTreeNode::Ref child) {
        update(idx, key, BTreeNode::ChildRef(child));
    }
    void insert(uint32_t idx, const KeyT & key, BTreeNode::Ref child) {
        insert(idx, key, BTreeNode::ChildRef(child));
    }
    uint32_t validLeaves() const { return _validLeaves; }
    void setValidLeaves(uint32_t newValidLeaves) { _validLeaves = newValidLeaves; }
    void incValidLeaves(uint32_t delta) { _validLeaves += delta; }
    void decValidLeaves(uint32_t delta) { _validLeaves -= delta; }

    template <typename NodeAllocatorType>
    void splitInsert(BTreeInternalNode *splitNode, uint32_t idx, const KeyT &key,
                     const BTreeNode::Ref &data, NodeAllocatorType &allocator);

    void stealAllFromLeftNode(const BTreeInternalNode *victim);
    void stealAllFromRightNode(const BTreeInternalNode *victim);

    template <typename NodeAllocatorType>
    void stealSomeFromLeftNode(BTreeInternalNode *victim, NodeAllocatorType &allocator);

    template <typename NodeAllocatorType>
    void stealSomeFromRightNode(BTreeInternalNode *victim, NodeAllocatorType &allocator);

    void clean();
    void cleanFrozen();

    template <typename NodeStoreType, typename FunctionType>
    void foreach_key(NodeStoreType &store, FunctionType func) const {
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
    template <typename NodeStoreType, typename FunctionType>
    void foreach_key_range(NodeStoreType &store, uint32_t start_idx, uint32_t end_idx, FunctionType func) const {
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

    template <typename NodeStoreType, typename FunctionType>
    void foreach(NodeStoreType &store, FunctionType func) const {
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
};

template <typename KeyT, typename DataT, typename AggrT, uint32_t NumSlots = 16>
class BTreeLeafNode : public BTreeNodeTT<KeyT, DataT, AggrT, NumSlots>
{
public:
    using ParentType = BTreeNodeTT<KeyT, DataT, AggrT, NumSlots>;
    using LeafNodeType = BTreeLeafNode<KeyT, DataT, AggrT, NumSlots>;
    template <typename, typename, typename, size_t, size_t>
    friend class BTreeNodeAllocator;
    template <typename, typename, typename, size_t, size_t>
    friend class BTreeNodeStore;
    template <typename, typename>
    friend class datastore::BufferType;
    template <typename, typename>
    friend class datastore::Allocator;
    template <typename, typename...>
    friend struct datastore::allocator::Assigner;
    using Ref = BTreeNode::Ref;
    using RefPair = datastore::Handle<LeafNodeType>;
    using ParentType::validSlots;
    using ParentType::_validSlots;
    using ParentType::_keys;
    using ParentType::freeze;
    using ParentType::stealSomeFromLeftNode;
    using ParentType::stealSomeFromRightNode;
    using ParentType::LEAF_LEVEL;
    using KeyDataType = BTreeKeyData<KeyT, DataT>;
    using KeyType = KeyT;
    using DataType = DataT;
protected:
    BTreeLeafNode() noexcept : ParentType(LEAF_LEVEL) {}

    BTreeLeafNode(const BTreeLeafNode &rhs) noexcept
        : ParentType(rhs)
    {}

    BTreeLeafNode(const KeyDataType *smallArray, uint32_t arraySize) noexcept;

    ~BTreeLeafNode() = default;

    BTreeLeafNode &operator=(const BTreeLeafNode &rhs) noexcept {
        ParentType::operator=(rhs);
        return *this;
    }

public:
    template <typename NodeAllocatorType>
    void stealSomeFromLeftNode(BTreeLeafNode *victim, NodeAllocatorType &allocator)
    {
        (void) allocator;
        stealSomeFromLeftNode(victim);
    }

    template <typename NodeAllocatorType>
    void stealSomeFromRightNode(BTreeLeafNode *victim, NodeAllocatorType &allocator) {
        (void) allocator;
        stealSomeFromRightNode(victim);
    }

    const DataT &getLastData() const { return this->getData(validSlots() - 1); }
    void writeData(uint32_t idx, const DataT &data) { this->setData(idx, data); }
    uint32_t validLeaves() const { return validSlots(); }

    template <typename FunctionType>
    void foreach_key(FunctionType func) const {
        const KeyT *it = _keys;
        const KeyT *ite = it + _validSlots;
        for (; it != ite; ++it) {
            func(*it);
        }
    }

    /**
     * Call func with leaf entry key value as argument for leaf entries [start_idx, end_idx).
     */
    template <typename FunctionType>
    void foreach_key_range(uint32_t start_idx, uint32_t end_idx, FunctionType func) const {
        const KeyT *it = _keys;
        const KeyT *ite = it + end_idx;
        it += start_idx;
        for (; it != ite; ++it) {
            func(*it);
        }
    }

    template <typename FunctionType>
    void foreach(FunctionType func) const {
        const KeyT *it = _keys;
        const KeyT *ite = it + _validSlots;
        uint32_t idx = 0;
        for (; it != ite; ++it) {
            func(*it, this->getData(idx++));
        }
    }
};


template <typename KeyT, typename DataT, typename AggrT,
          uint32_t NumSlots = 16>
class BTreeLeafNodeTemp : public BTreeLeafNode<KeyT, DataT, AggrT, NumSlots>
{
public:
    using ParentType = BTreeLeafNode<KeyT, DataT, AggrT, NumSlots>;
    using KeyDataType = typename ParentType::KeyDataType;

    BTreeLeafNodeTemp(const KeyDataType *smallArray, uint32_t arraySize) noexcept
        : ParentType(smallArray, arraySize)
    {}

    ~BTreeLeafNodeTemp() {}
};

extern template class BTreeNodeDataWrap<uint32_t, 16>;
extern template class BTreeNodeDataWrap<BTreeNoLeafData, 16>;
extern template class BTreeNodeT<uint32_t, 16>;
extern template class BTreeNodeTT<uint32_t, uint32_t, NoAggregated, 16>;
extern template class BTreeNodeTT<uint32_t, BTreeNoLeafData, NoAggregated, 16>;
extern template class BTreeNodeTT<uint32_t, datastore::EntryRef, NoAggregated, 16>;
extern template class BTreeNodeTT<uint32_t, int32_t, MinMaxAggregated, 16>;
extern template class BTreeInternalNode<uint32_t, NoAggregated, 16>;
extern template class BTreeInternalNode<uint32_t, MinMaxAggregated, 16>;
extern template class BTreeLeafNode<uint32_t, uint32_t, NoAggregated, 16>;
extern template class BTreeLeafNode<uint32_t, BTreeNoLeafData, NoAggregated, 16>;
extern template class BTreeLeafNode<uint32_t, int32_t, MinMaxAggregated, 16>;
extern template class BTreeLeafNodeTemp<uint32_t, uint32_t, NoAggregated, 16>;
extern template class BTreeLeafNodeTemp<uint32_t, int32_t, MinMaxAggregated, 16>;
extern template class BTreeLeafNodeTemp<uint32_t, BTreeNoLeafData, NoAggregated, 16>;

}
