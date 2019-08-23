// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enumstorebase.h"
#include "enumstore.h"
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/datastore/datastore.hpp>
#include <vespa/vespalib/datastore/unique_store_dictionary.hpp>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/bufferwriter.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/rcuvector.hpp>


#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.enumstorebase");

namespace search {

using btree::BTreeNode;

EnumStoreBase::EnumBufferType::EnumBufferType()
    : datastore::BufferType<char>(Index::align(1),
                                  Index::offsetSize() / Index::align(1),
                                  Index::offsetSize() / Index::align(1)),
      _minSizeNeeded(0),
      _deadElems(0),
      _pendingCompact(false),
      _wantCompact(false)
{
}

size_t
EnumStoreBase::EnumBufferType::calcArraysToAlloc(uint32_t bufferId, size_t sizeNeeded, bool resizing) const
{
    (void) resizing;
    size_t reservedElements = getReservedElements(bufferId);
    sizeNeeded = std::max(sizeNeeded, _minSizeNeeded);
    size_t usedElems = _activeUsedElems;
    if (_lastUsedElems != nullptr) {
        usedElems += *_lastUsedElems;
    }
    assert((usedElems % _arraySize) == 0);
    double growRatio = 1.5f;
    uint64_t maxSize = static_cast<uint64_t>(_maxArrays) * _arraySize;
    uint64_t newSize = usedElems - _deadElems + sizeNeeded;
    if (usedElems != 0) {
        newSize *= growRatio;
    }
    newSize += reservedElements;
    newSize = alignBufferSize(newSize);
    assert((newSize % _arraySize) == 0);
    if (newSize <= maxSize) {
        return newSize / _arraySize;
    }
    newSize = usedElems - _deadElems + sizeNeeded + reservedElements + 1000000;
    newSize = alignBufferSize(newSize);
    assert((newSize % _arraySize) == 0);
    if (newSize <= maxSize) {
        return _maxArrays;
    }
    failNewSize(newSize, maxSize);
    return 0;
}

EnumStoreBase::EnumStoreBase(uint64_t initBufferSize, bool hasPostings)
    : _enumDict(nullptr),
      _store(),
      _type(),
      _nextEnum(0),
      _toHoldBuffers(),
      _disabledReEnumerate(false)
{
    if (hasPostings)
        _enumDict = new EnumStoreDict<EnumPostingTree>(*this);
    else
        _enumDict = new EnumStoreDict<EnumTree>(*this);
    _store.addType(&_type);
    _type.setSizeNeededAndDead(initBufferSize, 0);
    _store.initActiveBuffers();
}

EnumStoreBase::~EnumStoreBase()
{
    _store.clearHoldLists();
    _store.dropBuffers();
    delete _enumDict;
}

void
EnumStoreBase::reset(uint64_t initBufferSize)
{
    _store.clearHoldLists();
    _store.dropBuffers();
    _type.setSizeNeededAndDead(initBufferSize, 0);
    _store.initActiveBuffers();
    _enumDict->onReset();
    _nextEnum = 0;
}

uint32_t
EnumStoreBase::getBufferIndex(datastore::BufferState::State status)
{
    for (uint32_t i = 0; i < _store.getNumBuffers(); ++i) {
        if (_store.getBufferState(i).getState() == status) {
            return i;
        }
    }
    return Index::numBuffers();
}

vespalib::MemoryUsage
EnumStoreBase::getMemoryUsage() const
{
    return _store.getMemoryUsage();
}

vespalib::AddressSpace
EnumStoreBase::getAddressSpaceUsage() const
{
    const datastore::BufferState &activeState = _store.getBufferState(_store.getActiveBufferId(TYPE_ID));
    return vespalib::AddressSpace(activeState.size(), activeState.getDeadElems(), DataStoreType::RefType::offsetSize());
}

void
EnumStoreBase::transferHoldLists(generation_t generation)
{
    _enumDict->onTransferHoldLists(generation);
    _store.transferHoldLists(generation);
}

void
EnumStoreBase::trimHoldLists(generation_t firstUsed)
{
    // remove generations in the range [0, firstUsed>
    _enumDict->onTrimHoldLists(firstUsed);
    _store.trimHoldLists(firstUsed);
}

bool
EnumStoreBase::preCompact(uint64_t bytesNeeded)
{
    if (getBufferIndex(datastore::BufferState::FREE) == Index::numBuffers()) {
        return false;
    }
    uint32_t activeBufId = _store.getActiveBufferId(TYPE_ID);
    datastore::BufferState & activeBuf = _store.getBufferState(activeBufId);
    _type.setSizeNeededAndDead(bytesNeeded, activeBuf.getDeadElems());
    _toHoldBuffers = _store.startCompact(TYPE_ID);
    return true;
}


void
EnumStoreBase::fallbackResize(uint64_t bytesNeeded)
{
    uint32_t activeBufId = _store.getActiveBufferId(TYPE_ID);
    size_t reservedElements = _type.getReservedElements(activeBufId);
    _type.setSizeNeededAndDead(bytesNeeded, reservedElements);
    _type.setWantCompact();
    _store.fallbackResize(activeBufId, bytesNeeded);
}


void
EnumStoreBase::disableReEnumerate() const
{
    assert(!_disabledReEnumerate);
    _disabledReEnumerate = true;
}


void
EnumStoreBase::enableReEnumerate() const
{
    assert(_disabledReEnumerate);
    _disabledReEnumerate = false;
}


void
EnumStoreBase::postCompact(uint32_t newEnum)
{
    _store.finishCompact(_toHoldBuffers);
    _nextEnum = newEnum;
}

void
EnumStoreBase::failNewSize(uint64_t minNewSize, uint64_t maxSize)
{
    throw vespalib::IllegalStateException(vespalib::make_string("EnumStoreBase::failNewSize: Minimum new size (%" PRIu64 ") exceeds max size (%" PRIu64 ")", minNewSize, maxSize));
}

template <class Tree>
void
EnumStoreBase::reEnumerate(const Tree &tree)
{
    typedef typename Tree::Iterator Iterator;
    Iterator it(tree.begin());
    uint32_t enumValue = 0;
    while (it.valid()) {
        EntryBase eb(getEntryBase(it.getKey()));
        eb.setEnum(enumValue);
        ++enumValue;
        ++it;
    }
    _nextEnum = enumValue;
    std::atomic_thread_fence(std::memory_order_release);
}


ssize_t
EnumStoreBase::deserialize0(const void *src,
                                size_t available,
                                IndexVector &idx)
{
    size_t left = available;
    size_t initSpace = Index::align(1);
    const char * p = static_cast<const char *>(src);
    while (left > 0) {
        ssize_t sz = deserialize(p, left, initSpace);
        if (sz < 0)
            return sz;
        p += sz;
        left -= sz;
    }
    reset(initSpace);
    left = available;
    p = static_cast<const char *>(src);
    Index idx1;
    while (left > 0) {
        ssize_t sz = deserialize(p, left, idx1);
        if (sz < 0)
            return sz;
        p += sz;
        left -= sz;
        idx.push_back(idx1);
    }
    return available - left;
}


template <typename Tree>
ssize_t
EnumStoreBase::deserialize(const void *src,
                               size_t available,
                               IndexVector &idx,
                               Tree &tree)
{
    ssize_t sz(deserialize0(src, available, idx));
    if (sz >= 0) {
        typename Tree::Builder builder(tree.getAllocator());
        typedef IndexVector::const_iterator IT;
        for (IT i(idx.begin()), ie(idx.end()); i != ie; ++i) {
            builder.insert(*i, typename Tree::DataType());
        }
        tree.assign(builder);
    }
    return sz;
}


template <typename Tree>
void
EnumStoreBase::fixupRefCounts(const EnumVector &hist, Tree &tree)
{
    if ( hist.empty() )
        return;
    typename Tree::Iterator ti(tree.begin());
    typedef EnumVector::const_iterator HistIT;

    for (HistIT hi(hist.begin()), hie(hist.end()); hi != hie; ++hi, ++ti) {
        assert(ti.valid());
        fixupRefCount(ti.getKey(), *hi);
    }
    assert(!ti.valid());
    freeUnusedEnums(false);
}


void
EnumStoreBase::writeEnumValues(BufferWriter &writer,
                               const Index *idxs, size_t count) const
{
    for (uint32_t i = 0; i < count; ++i) {
        uint32_t enumValue = getEnum(idxs[i]);
        writer.write(&enumValue, sizeof(uint32_t));
    }
}


vespalib::asciistream & operator << (vespalib::asciistream & os, const EnumStoreBase::Index & idx) {
    return os << "offset(" << idx.offset() << "), bufferId(" << idx.bufferId() << "), idx(" << idx.ref() << ")";
}


EnumStoreDictBase::EnumStoreDictBase(EnumStoreBase &enumStore)
    : _enumStore(enumStore)
{
}

EnumStoreDictBase::~EnumStoreDictBase() = default;

template <typename Dictionary>
EnumStoreDict<Dictionary>::EnumStoreDict(EnumStoreBase &enumStore)
    : EnumStoreDictBase(enumStore),
      ParentUniqueStoreDictionary()
{
}

template <typename Dictionary>
EnumStoreDict<Dictionary>::~EnumStoreDict() = default;

template <typename Dictionary>
uint32_t
EnumStoreDict<Dictionary>::getNumUniques() const
{
    return this->_dict.size();
}

template <typename Dictionary>
void
EnumStoreDict<Dictionary>::reEnumerate()
{
    _enumStore.reEnumerate(this->_dict);
}

template <typename Dictionary>
void
EnumStoreDict<Dictionary>::
writeAllValues(BufferWriter &writer,
               btree::BTreeNode::Ref rootRef) const
{
    constexpr size_t BATCHSIZE = 1000;
    std::vector<Index> idxs;
    idxs.reserve(BATCHSIZE);
    typename Dictionary::Iterator it(rootRef, this->_dict.getAllocator());
    while (it.valid()) {
        if (idxs.size() >= idxs.capacity()) {
            _enumStore.writeValues(writer, &idxs[0], idxs.size());
            idxs.clear();
        }
        idxs.push_back(it.getKey());
        ++it;
    }
    if (!idxs.empty()) {
        _enumStore.writeValues(writer, &idxs[0], idxs.size());
    }
}

template <typename Dictionary>
ssize_t
EnumStoreDict<Dictionary>::deserialize(const void *src,
                                       size_t available,
                                       IndexVector &idx)
{
    return _enumStore.deserialize(src, available, idx, this->_dict);
}

template <typename Dictionary>
void
EnumStoreDict<Dictionary>::fixupRefCounts(const EnumVector & hist)
{
    _enumStore.fixupRefCounts(hist, this->_dict);
}

template <typename Dictionary>
void
EnumStoreDict<Dictionary>::removeUnusedEnums(const IndexSet &unused,
                                             const datastore::EntryComparator &cmp,
                                             const datastore::EntryComparator *fcmp)
{
    typedef typename Dictionary::Iterator Iterator;
    if (unused.empty())
        return;
    Iterator it(BTreeNode::Ref(), this->_dict.getAllocator());
    for (const auto& idx : unused) {
        it.lower_bound(this->_dict.getRoot(), idx, cmp);
        assert(it.valid() && !cmp(idx, it.getKey()));
        if (Iterator::hasData() && fcmp != nullptr) {
            typename Dictionary::DataType pidx(it.getData());
            this->_dict.remove(it);
            if (!it.valid() || (*fcmp)(idx, it.getKey())) {
                continue;  // Next entry does not use same posting list
            }
            --it;
            if (it.valid() && !(*fcmp)(it.getKey(), idx)) {
                continue;  // Previous entry uses same posting list
            }
            if (it.valid()) {
                ++it;
            } else {
                it.begin();
            }
            this->_dict.thaw(it);
            it.writeData(pidx);
        } else {
            this->_dict.remove(it);
        }
   }
}

template <typename Dictionary>
void
EnumStoreDict<Dictionary>::freeUnusedEnums(const datastore::EntryComparator &cmp,
                                           const datastore::EntryComparator *fcmp)
{
    IndexSet unused;

    // find unused enums
    for (auto iter = this->_dict.begin(); iter.valid(); ++iter) {
        _enumStore.freeUnusedEnum(iter.getKey(), unused);
    }
    removeUnusedEnums(unused, cmp, fcmp);
}

template <typename Dictionary>
void
EnumStoreDict<Dictionary>::freeUnusedEnums(const IndexSet& toRemove,
                                           const datastore::EntryComparator& cmp,
                                           const datastore::EntryComparator* fcmp)
{
    IndexSet unused;
    for (const auto& index : toRemove) {
        _enumStore.freeUnusedEnum(index, unused);
    }
    removeUnusedEnums(unused, cmp, fcmp);
}

template <typename Dictionary>
bool
EnumStoreDict<Dictionary>::findIndex(const datastore::EntryComparator &cmp,
                                     Index &idx) const
{
    auto itr = this->_dict.find(Index(), cmp);
    if (!itr.valid()) {
        return false;
    }
    idx = itr.getKey();
    return true;
}

template <typename Dictionary>
bool
EnumStoreDict<Dictionary>::findFrozenIndex(const datastore::EntryComparator &cmp,
                                           Index &idx) const
{
    auto itr = this->_dict.getFrozenView().find(Index(), cmp);
    if (!itr.valid()) {
        return false;
    }
    idx = itr.getKey();
    return true;
}

template <typename Dictionary>
std::vector<EnumStoreBase::EnumHandle>
EnumStoreDict<Dictionary>::findMatchingEnums(const datastore::EntryComparator &cmp) const
{
    std::vector<EnumStoreBase::EnumHandle> result;
    auto itr = this->_dict.getFrozenView().find(Index(), cmp);
    while (itr.valid() && !cmp(Index(), itr.getKey())) {
        result.push_back(itr.getKey().ref());
        ++itr;
    }
    return result;
}

template <typename Dictionary>
void
EnumStoreDict<Dictionary>::onReset()
{
    this->_dict.clear();
}

template <typename Dictionary>
uint32_t
EnumStoreDict<Dictionary>::lookupFrozenTerm(BTreeNode::Ref frozenRootRef,
                                            const datastore::EntryComparator &comp) const
{
    typename Dictionary::ConstIterator itr(BTreeNode::Ref(),
                                           this->_dict.getAllocator());
    itr.lower_bound(frozenRootRef, Index(), comp);
    if (itr.valid() && !comp(Index(), itr.getKey())) {
        return 1u;
    }
    return 0u;
}

template <typename Dictionary>
uint32_t
EnumStoreDict<Dictionary>::
lookupFrozenRange(BTreeNode::Ref frozenRootRef,
                  const datastore::EntryComparator &low,
                  const datastore::EntryComparator &high) const
{
    typename Dictionary::ConstIterator lowerDictItr(BTreeNode::Ref(),
                                                    this->_dict.getAllocator());
    lowerDictItr.lower_bound(frozenRootRef, Index(), low);
    auto upperDictItr = lowerDictItr;
    if (upperDictItr.valid() && !high(Index(), upperDictItr.getKey())) {
        upperDictItr.seekPast(Index(), high);
    }
    return upperDictItr - lowerDictItr;
}


template <>
EnumPostingTree &
EnumStoreDict<EnumTree>::getPostingDictionary()
{
    LOG_ABORT("should not be reached");
}


template <>
EnumPostingTree &
EnumStoreDict<EnumPostingTree>::getPostingDictionary()
{
    return _dict;
}


template <>
const EnumPostingTree &
EnumStoreDict<EnumTree>::getPostingDictionary() const
{
    LOG_ABORT("should not be reached");
}


template <>
const EnumPostingTree &
EnumStoreDict<EnumPostingTree>::getPostingDictionary() const
{
    return _dict;
}


template <typename Dictionary>
bool
EnumStoreDict<Dictionary>::hasData() const
{
    return Dictionary::LeafNodeType::hasData();
}


template class datastore::DataStoreT<datastore::AlignedEntryRefT<31, 4> >;

template
void
EnumStoreBase::reEnumerate<EnumTree>(const EnumTree &tree);

template
void
EnumStoreBase::reEnumerate<EnumPostingTree>(const EnumPostingTree &tree);

template
ssize_t
EnumStoreBase::deserialize<EnumTree>(const void *src, size_t available, IndexVector &idx, EnumTree &tree);

template
ssize_t
EnumStoreBase::deserialize<EnumPostingTree>(const void *src, size_t available, IndexVector &idx, EnumPostingTree &tree);

template
void
EnumStoreBase::fixupRefCounts<EnumTree>(const EnumVector &hist, EnumTree &tree);

template
void
EnumStoreBase::fixupRefCounts<EnumPostingTree>(const EnumVector &hist, EnumPostingTree &tree);

template class EnumStoreDict<EnumTree>;

template class EnumStoreDict<EnumPostingTree>;

template
class btree::BTreeNodeT<EnumStoreBase::Index, EnumTreeTraits::INTERNAL_SLOTS>;

template
class btree::BTreeNodeTT<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated, EnumTreeTraits::INTERNAL_SLOTS>;

template
class btree::BTreeNodeTT<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeInternalNode<EnumStoreBase::Index, btree::NoAggregated, EnumTreeTraits::INTERNAL_SLOTS>;

template
class btree::BTreeLeafNode<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeLeafNode<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeLeafNodeTemp<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeLeafNodeTemp<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeNodeStore<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                            EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeNodeStore<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated,
                            EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;


template
class btree::BTreeRoot<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                       const datastore::EntryComparatorWrapper, EnumTreeTraits>;

template
class btree::BTreeRoot<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated,
                       const datastore::EntryComparatorWrapper, EnumTreeTraits>;

template
class btree::BTreeRootT<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                        const datastore::EntryComparatorWrapper, EnumTreeTraits>;

template
class btree::BTreeRootT<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated,
                        const datastore::EntryComparatorWrapper, EnumTreeTraits>;

template
class btree::BTreeRootBase<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                           EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeRootBase<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated,
                           EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeNodeAllocator<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                                EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeNodeAllocator<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated,
                                EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

template
class btree::BTreeIteratorBase<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                               EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS, EnumTreeTraits::PATH_SIZE>;
template
class btree::BTreeIteratorBase<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated,
                               EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS, EnumTreeTraits::PATH_SIZE>;

template class btree::BTreeConstIterator<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                                         const datastore::EntryComparatorWrapper, EnumTreeTraits>;

template class btree::BTreeConstIterator<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated,
                                         const datastore::EntryComparatorWrapper, EnumTreeTraits>;

template
class btree::BTreeIterator<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                           const datastore::EntryComparatorWrapper, EnumTreeTraits>;
template
class btree::BTreeIterator<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated,
                           const datastore::EntryComparatorWrapper, EnumTreeTraits>;

template
class btree::BTree<EnumStoreBase::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                   const datastore::EntryComparatorWrapper, EnumTreeTraits>;
template
class btree::BTree<EnumStoreBase::Index, datastore::EntryRef, btree::NoAggregated,
                   const datastore::EntryComparatorWrapper, EnumTreeTraits>;


}

namespace vespalib {
template class RcuVectorBase<search::EnumStoreIndex>;
}

VESPALIB_HASH_MAP_INSTANTIATE_H_E_M(search::EnumStoreIndex, search::EnumStoreIndex,
                                    vespalib::hash<search::EnumStoreIndex>, std::equal_to<search::EnumStoreIndex>,
                                    vespalib::hashtable_base::and_modulator);
