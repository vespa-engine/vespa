// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "postingstore.h"
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/common/growablebitvector.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/attribute/status.h>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreerootbase.cpp>
#include <vespa/vespalib/datastore/datastore.hpp>
#include <vespa/vespalib/datastore/compacting_buffers.h>
#include <vespa/vespalib/datastore/compaction_spec.h>
#include <vespa/vespalib/datastore/entry_ref_filter.h>
#include <vespa/vespalib/datastore/buffer_type.hpp>

namespace search::attribute {

using vespalib::btree::BTreeNoLeafData;
using vespalib::datastore::EntryRefFilter;

PostingStoreBase2::PostingStoreBase2(IEnumStoreDictionary& dictionary, Status &status, const Config &config)
    : _enableOnlyBitVector(config.getEnableOnlyBitVector()),
      _isFilter(config.getIsFilter()),
      _bvSize(64u),
      _bvCapacity(128u),
      _minBvDocFreq(64),
      _maxBvDocFreq(std::numeric_limits<uint32_t>::max()),
      _bvs(),
      _dictionary(dictionary),
      _status(status),
      _bvExtraBytes(0),
      _compaction_spec()
{
}


PostingStoreBase2::~PostingStoreBase2() = default;


bool
PostingStoreBase2::resizeBitVectors(uint32_t newSize, uint32_t newCapacity)
{
    assert(newCapacity >= newSize);
    newSize = (newSize + 63) & ~63;
    if (newSize >= newCapacity)
        newSize = newCapacity;
    if (newSize == _bvSize && newCapacity == _bvCapacity)
        return false;
    _minBvDocFreq = std::max(newSize >> 6, 64u);
    _maxBvDocFreq = std::max(newSize >> 5, 128u);
    if (_bvs.empty()) {
        _bvSize = newSize;
        _bvCapacity = newCapacity;
        return false;
    }
    _bvSize = newSize;
    _bvCapacity = newCapacity;
    return removeSparseBitVectors();
}


template <typename DataT>
PostingStore<DataT>::PostingStore(IEnumStoreDictionary& dictionary, Status &status,
                                  const Config &config)
    : Parent(false),
      PostingStoreBase2(dictionary, status, config),
      _bvType(1, 1024u, RefType::offsetSize())
{
    // TODO: Add type for bitvector
    _store.addType(&_bvType);
    _store.init_primary_buffers();
    _store.enableFreeLists();
}


template <typename DataT>
PostingStore<DataT>::~PostingStore()
{
    _builder.clear();
    _store.dropBuffers();   // Drop buffers before type handlers are dropped
}


template <typename DataT>
bool
PostingStore<DataT>::removeSparseBitVectors()
{
    bool res = false;
    bool needscan = false;
    for (auto &i : _bvs) {
        RefType iRef = EntryRef(i);
        uint32_t typeId = getTypeId(iRef);
        (void) typeId;
        assert(isBitVector(typeId));
        BitVectorEntry *bve = getWBitVectorEntry(iRef);
        GrowableBitVector &bv = *bve->_bv;
        uint32_t docFreq = bv.writer().countTrueBits();
        if (bve->_tree.valid()) {
            RefType iRef2(bve->_tree);
            assert(isBTree(iRef2));
            const BTreeType *tree = getTreeEntry(iRef2);
            assert(tree->size(_allocator) == docFreq);
            (void) tree;
        }
        if (docFreq < _minBvDocFreq)
            needscan = true;
        unsigned int oldExtraSize = bv.writer().extraByteSize();
        if (bv.writer().size() > _bvSize) {
            bv.shrink(_bvSize);
            res = true;
        }
        if (bv.writer().capacity() < _bvCapacity) {
            bv.reserve(_bvCapacity);
            res = true;
        }
        if (bv.writer().size() < _bvSize) {
            bv.extend(_bvSize);
        }
        unsigned int newExtraSize = bv.writer().extraByteSize();
        if (oldExtraSize != newExtraSize) {
            _bvExtraBytes = _bvExtraBytes + newExtraSize - oldExtraSize;
        }
    }
    if (needscan) {
        EntryRefFilter filter(RefType::numBuffers(), RefType::offset_bits);
        filter.add_buffers(_bvType.get_active_buffers());
        res = _dictionary.normalize_posting_lists([this](std::vector<EntryRef>& refs)
                                                  { consider_remove_sparse_bitvector(refs); },
                                                  filter);
    }
    return res;
}

template <typename DataT>
void
PostingStore<DataT>::consider_remove_sparse_bitvector(std::vector<EntryRef>& refs)
{
    for (auto& ref : refs) {
        RefType iRef(ref);
        assert(iRef.valid());
        uint32_t typeId = getTypeId(iRef);
        assert(isBitVector(typeId));
        assert(_bvs.find(iRef.ref()) != _bvs.end());
        BitVectorEntry *bve = getWBitVectorEntry(iRef);
        BitVector &bv = bve->_bv->writer();
        uint32_t docFreq = bv.countTrueBits();
        if (bve->_tree.valid()) {
            RefType iRef2(bve->_tree);
            assert(isBTree(iRef2));
            const BTreeType *tree = getTreeEntry(iRef2);
            assert(tree->size(_allocator) == docFreq);
            (void) tree;
        }
        if (docFreq < _minBvDocFreq) {
            dropBitVector(ref);
            iRef = ref;
            if (iRef.valid()) {
                typeId = getTypeId(iRef);
                if (isBTree(typeId)) {
                    BTreeType *tree = getWTreeEntry(iRef);
                    normalizeTree(ref, tree, false);
                }
            }
        }
    }
}

template <typename DataT>
void
PostingStore<DataT>::applyNew(EntryRef &ref, AddIter a, AddIter ae)
{
    // No old data
    assert(!ref.valid());
    size_t additionSize(ae - a);
    uint32_t clusterSize = additionSize;
    if (clusterSize <= clusterLimit) {
        applyNewArray(ref, a, ae);
    } else if (clusterSize >= _maxBvDocFreq) {
        applyNewBitVector(ref, a, ae);
    } else {
        applyNewTree(ref, a, ae, CompareT());
    }
}


template <typename DataT>
void
PostingStore<DataT>::makeDegradedTree(EntryRef &ref,
                                      const BitVector &bv)
{
    assert(!ref.valid());
    BTreeTypeRefPair tPair(allocBTree());
    BTreeType *tree = tPair.data;
    Builder &builder = _builder;
    builder.reuse();
    uint32_t docIdLimit = _bvSize;
    assert(_bvSize == bv.size());
    uint32_t docId = bv.getFirstTrueBit();
    while (docId < docIdLimit) {
        builder.insert(docId, bitVectorWeight());
        docId = bv.getNextTrueBit(docId + 1);
    }
    tree->assign(builder, _allocator);
    assert(tree->size(_allocator) == bv.countTrueBits());
    // barrier ?
    ref = tPair.ref;
}


template <typename DataT>
void
PostingStore<DataT>::dropBitVector(EntryRef &ref)
{
    assert(ref.valid());
    RefType iRef(ref);
    uint32_t typeId = getTypeId(iRef);
    assert(isBitVector(typeId));
    (void) typeId;
    BitVectorEntry *bve = getWBitVectorEntry(iRef);
    GrowableBitVector *bv = bve->_bv.get();
    assert(bv);
    uint32_t docFreq = bv->writer().countTrueBits();
    EntryRef ref2(bve->_tree);
    if (!ref2.valid()) {
        makeDegradedTree(ref2, bv->writer());
    }
    assert(ref2.valid());
    assert(isBTree(ref2));
    const BTreeType *tree = getTreeEntry(ref2);
    assert(tree->size(_allocator) == docFreq);
    (void) tree;
    (void) docFreq;
    _bvs.erase(ref.ref());
    _store.holdElem(iRef, 1);
    _status.decBitVectors();
    _bvExtraBytes -= bv->writer().extraByteSize();
    ref = ref2;
}


template <typename DataT>
void
PostingStore<DataT>::makeBitVector(EntryRef &ref)
{
    assert(ref.valid());
    RefType iRef(ref);
    uint32_t typeId = getTypeId(iRef);
    assert(isBTree(typeId));
    (void) typeId;
    vespalib::GenerationHolder &genHolder = _store.getGenerationHolder();
    auto bvsp = std::make_shared<GrowableBitVector>(_bvSize, _bvCapacity, genHolder);
    BitVector &bv = bvsp->writer();
    uint32_t docIdLimit = _bvSize;
    (void) docIdLimit;
    Iterator it = begin(ref);
    uint32_t expDocFreq = it.size();
    (void) expDocFreq;
    for (; it.valid(); ++it) {
        uint32_t docId = it.getKey();
        assert(docId < docIdLimit);
        bv.setBit(docId);
    }
    bv.invalidateCachedCount();
    assert(bv.countTrueBits() == expDocFreq);
    BitVectorRefPair bPair(allocBitVector());
    BitVectorEntry *bve = bPair.data;
    if (_enableOnlyBitVector) {
        BTreeType *tree = getWTreeEntry(iRef);
        tree->clear(_allocator);
        _store.holdElem(ref, 1);
    } else {
        bve->_tree = ref;
    }
    bve->_bv = bvsp;
    _bvs.insert(bPair.ref.ref());
    _status.incBitVectors();
    _bvExtraBytes += bvsp->writer().extraByteSize();
    // barrier ?
    ref = bPair.ref;
}


template <typename DataT>
void
PostingStore<DataT>::applyNewBitVector(EntryRef &ref, AddIter aOrg, AddIter ae)
{
    assert(!ref.valid());
    vespalib::GenerationHolder &genHolder = _store.getGenerationHolder();
    auto bvsp = std::make_shared<GrowableBitVector>(_bvSize, _bvCapacity, genHolder);
    BitVector &bv = bvsp->writer();
    uint32_t docIdLimit = _bvSize;
    (void) docIdLimit;
    uint32_t expDocFreq = ae - aOrg;
    (void) expDocFreq;
    for (AddIter a = aOrg; a != ae; ++a) {
        uint32_t docId = a->_key;
        assert(docId < docIdLimit);
        bv.setBit(docId);
    }
    bv.invalidateCachedCount();
    assert(bv.countTrueBits() == expDocFreq);
    BitVectorRefPair bPair(allocBitVector());
    BitVectorEntry *bve = bPair.data;
    if (!_enableOnlyBitVector) {
        applyNewTree(bve->_tree, aOrg, ae, CompareT());
    }
    bve->_bv = bvsp;
    _bvs.insert(bPair.ref.ref());
    _status.incBitVectors();
    _bvExtraBytes += bvsp->writer().extraByteSize();
    // barrier ?
    ref = bPair.ref;
}


template <typename DataT>
void
PostingStore<DataT>::apply(BitVector &bv,
                           AddIter a,
                           AddIter ae,
                           RemoveIter r,
                           RemoveIter re)
{
    while (a != ae || r != re) {
        if (r != re && (a == ae || *r < a->_key)) {
            // remove
            assert(*r < bv.size());
            bv.clearBitAndMaintainCount(*r);
            ++r;
        } else {
            if (r != re && !(a->_key < *r)) {
                // update or add
                assert(a->_key < bv.size());
                bv.setBitAndMaintainCount(a->_key);
                ++r;
            } else {
                assert(a->_key < bv.size());
                bv.setBitAndMaintainCount(a->_key);
            }
            ++a;
        }
    }
}


template <typename DataT>
void
PostingStore<DataT>::apply(EntryRef &ref,
                           AddIter a,
                           AddIter ae,
                           RemoveIter r,
                           RemoveIter re)
{
    if (!ref.valid()) {
        // No old data
        applyNew(ref, a, ae);
        return;
    }
    RefType iRef(ref);
    bool wasArray = false;
    uint32_t typeId = getTypeId(iRef);
    uint32_t clusterSize = getClusterSize(typeId);
    if (clusterSize != 0) {
        wasArray = true;
        if (applyCluster(ref, clusterSize, a, ae, r, re, CompareT()))
            return;
        iRef = ref;
        typeId = getTypeId(iRef);
    }
    // Old data was tree or has been converted to a tree
    // ... or old data was bitvector
    if (isBitVector(typeId)) {
        BitVectorEntry *bve = getWBitVectorEntry(iRef);
        EntryRef ref2(bve->_tree);
        RefType iRef2(ref2);
        if (iRef2.valid()) {
            assert(isBTree(iRef2));
            BTreeType *tree = getWTreeEntry(iRef2);
            applyTree(tree, a, ae, r, re, CompareT());
        }
        BitVector *bv = &bve->_bv->writer();
        assert(bv);
        apply(*bv, a, ae, r, re);
        uint32_t docFreq = bv->countTrueBits();
        if (docFreq < _minBvDocFreq) {
            dropBitVector(ref);
            if (ref.valid()) {
                iRef = ref;
                typeId = getTypeId(iRef);
                if (isBTree(typeId)) {
                    BTreeType *tree = getWTreeEntry(iRef);
                    assert(tree->size(_allocator) == docFreq);
                    normalizeTree(ref, tree, wasArray);
                }
            }
        }
    } else {
        BTreeType *tree = getWTreeEntry(iRef);
        applyTree(tree, a, ae, r, re, CompareT());
        uint32_t docFreq = tree->size(_allocator);
        if (docFreq >= _maxBvDocFreq) {
            makeBitVector(ref);
            return;
        }
        normalizeTree(ref, tree, wasArray);
    }
}


template <typename DataT>
size_t
PostingStore<DataT>::internalSize(uint32_t typeId, const RefType & iRef) const
{
    if (isBitVector(typeId)) {
        const BitVectorEntry *bve = getBitVectorEntry(iRef);
        RefType iRef2(bve->_tree);
        if (iRef2.valid()) {
            assert(isBTree(iRef2));
            const BTreeType *tree = getTreeEntry(iRef2);
            return tree->size(_allocator);
        } else {
            const BitVector *bv = &bve->_bv->writer();
            return bv->countTrueBits();
        }
    } else {
        const BTreeType *tree = getTreeEntry(iRef);
        return tree->size(_allocator);
    }
}


template <typename DataT>
size_t
PostingStore<DataT>::internalFrozenSize(uint32_t typeId, const RefType & iRef) const
{
    if (isBitVector(typeId)) {
        const BitVectorEntry *bve = getBitVectorEntry(iRef);
        RefType iRef2(bve->_tree);
        if (iRef2.valid()) {
            assert(isBTree(iRef2));
            const BTreeType *tree = getTreeEntry(iRef2);
            return tree->frozenSize(_allocator);
        } else {
            // Some inaccuracy is expected, data changes underfeet
            return bve->_bv->reader().countTrueBits();
        }
    } else {
        const BTreeType *tree = getTreeEntry(iRef);
        return tree->frozenSize(_allocator);
    }
}


template <typename DataT>
typename PostingStore<DataT>::Iterator
PostingStore<DataT>::begin(const EntryRef ref) const
{
    if (!ref.valid())
        return Iterator();
    RefType iRef(ref);
    uint32_t typeId = getTypeId(iRef);
    uint32_t clusterSize = getClusterSize(typeId);
    if (clusterSize == 0) {
        if (isBitVector(typeId)) {
            const BitVectorEntry *bve = getBitVectorEntry(iRef);
            RefType iRef2(bve->_tree);
            if (iRef2.valid()) {
                assert(isBTree(iRef2));
                const BTreeType *tree = getTreeEntry(iRef2);
                return tree->begin(_allocator);
            }
            return Iterator();
        }
        const BTreeType *tree = getTreeEntry(iRef);
        return tree->begin(_allocator);
    }
    const KeyDataType *shortArray = getKeyDataEntry(iRef, clusterSize);
    return Iterator(shortArray, clusterSize, _allocator, _aggrCalc);
}


template <typename DataT>
typename PostingStore<DataT>::ConstIterator
PostingStore<DataT>::beginFrozen(const EntryRef ref) const
{
    if (!ref.valid())
        return ConstIterator();
    RefType iRef(ref);
    uint32_t typeId = getTypeId(iRef);
    uint32_t clusterSize = getClusterSize(typeId);
    if (clusterSize == 0) {
        if (isBitVector(typeId)) {
            const BitVectorEntry *bve = getBitVectorEntry(iRef);
            RefType iRef2(bve->_tree);
            if (iRef2.valid()) {
                assert(isBTree(iRef2));
                const BTreeType *tree = getTreeEntry(iRef2);
                return tree->getFrozenView(_allocator).begin();
            }
            return ConstIterator();
        }
        const BTreeType *tree = getTreeEntry(iRef);
        return tree->getFrozenView(_allocator).begin();
    }
    const KeyDataType *shortArray = getKeyDataEntry(iRef, clusterSize);
    return ConstIterator(shortArray, clusterSize, _allocator, _aggrCalc);
}


template <typename DataT>
void
PostingStore<DataT>::beginFrozen(const EntryRef ref,
                                 std::vector<ConstIterator> &where) const
{
    if (!ref.valid()) {
        where.emplace_back();
        return;
    }
    RefType iRef(ref);
    uint32_t typeId = getTypeId(iRef);
    uint32_t clusterSize = getClusterSize(typeId);
    if (clusterSize == 0) {
        if (isBitVector(typeId)) {
            const BitVectorEntry *bve = getBitVectorEntry(iRef);
            RefType iRef2(bve->_tree);
            if (iRef2.valid()) {
                assert(isBTree(iRef2));
                const BTreeType *tree = getTreeEntry(iRef2);
                tree->getFrozenView(_allocator).begin(where);
                return;
            }
            where.emplace_back();
            return;
        }
        const BTreeType *tree = getTreeEntry(iRef);
        tree->getFrozenView(_allocator).begin(where);
        return;
    }
    const KeyDataType *shortArray = getKeyDataEntry(iRef, clusterSize);
    where.emplace_back(shortArray, clusterSize, _allocator, _aggrCalc);
}


template <typename DataT>
typename PostingStore<DataT>::AggregatedType
PostingStore<DataT>::getAggregated(const EntryRef ref) const
{
    if (!ref.valid())
        return AggregatedType();
    RefType iRef(ref);
    uint32_t typeId = getTypeId(iRef);
    uint32_t clusterSize = getClusterSize(typeId);
    if (clusterSize == 0) {
        if (isBitVector(typeId)) {
            const BitVectorEntry *bve = getBitVectorEntry(iRef);
            RefType iRef2(bve->_tree);
            if (iRef2.valid()) {
                assert(isBTree(iRef2));
                const BTreeType *tree = getTreeEntry(iRef2);
                return tree->getAggregated(_allocator);
            }
            return AggregatedType();
        }
        const BTreeType *tree = getTreeEntry(iRef);
        return tree->getAggregated(_allocator);
    }
    const KeyDataType *shortArray = getKeyDataEntry(iRef, clusterSize);
    AggregatedType a;
    for (uint32_t i = 0; i < clusterSize; ++i) {
        _aggrCalc.add(a, _aggrCalc.getVal(shortArray[i].getData()));
    }
    return a;
}


template <typename DataT>
void
PostingStore<DataT>::clear(const EntryRef ref)
{
    if (!ref.valid())
        return;
    RefType iRef(ref);
    uint32_t typeId = getTypeId(iRef);
    uint32_t clusterSize = getClusterSize(typeId);
    if (clusterSize == 0) {
        if (isBitVector(typeId)) {
            const BitVectorEntry *bve = getBitVectorEntry(iRef);
            RefType iRef2(bve->_tree);
            if (iRef2.valid()) {
                assert(isBTree(iRef2));
                BTreeType *tree = getWTreeEntry(iRef2);
                tree->clear(_allocator);
                _store.holdElem(iRef2, 1);
            }
            _bvs.erase(ref.ref());
            _status.decBitVectors();
            _bvExtraBytes -= bve->_bv->writer().extraByteSize();
            _store.holdElem(ref, 1);
        } else {
            BTreeType *tree = getWTreeEntry(iRef);
            tree->clear(_allocator);
            _store.holdElem(ref, 1);
        }
    } else {
        _store.holdElem(ref, clusterSize);
    }
}


template <typename DataT>
vespalib::MemoryUsage
PostingStore<DataT>::getMemoryUsage() const
{
    vespalib::MemoryUsage usage;
    usage.merge(_allocator.getMemoryUsage());
    usage.merge(_store.getMemoryUsage());
    uint64_t bvExtraBytes = _bvExtraBytes;
    usage.incUsedBytes(bvExtraBytes);
    usage.incAllocatedBytes(bvExtraBytes);
    return usage;
}

template <typename DataT>
vespalib::MemoryUsage
PostingStore<DataT>::update_stat(const CompactionStrategy& compaction_strategy)
{
    vespalib::MemoryUsage usage;
    auto btree_nodes_memory_usage = _allocator.getMemoryUsage();
    auto store_memory_usage = _store.getMemoryUsage();
    _compaction_spec = PostingStoreCompactionSpec(compaction_strategy.should_compact_memory(btree_nodes_memory_usage), compaction_strategy.should_compact_memory(store_memory_usage));
    usage.merge(btree_nodes_memory_usage);
    usage.merge(store_memory_usage);
    uint64_t bvExtraBytes = _bvExtraBytes;
    usage.incUsedBytes(bvExtraBytes);
    usage.incAllocatedBytes(bvExtraBytes);
    return usage;
}

template <typename DataT>
void
PostingStore<DataT>::move_btree_nodes(const std::vector<EntryRef>& refs)
{
    for (auto ref : refs) {
        RefType iRef(ref);
        assert(iRef.valid());
        uint32_t typeId = getTypeId(iRef);
        uint32_t clusterSize = getClusterSize(typeId);
        assert(clusterSize == 0);
        if (isBitVector(typeId)) {
            BitVectorEntry *bve = getWBitVectorEntry(iRef);
            RefType iRef2(bve->_tree);
            if (iRef2.valid()) {
                assert(isBTree(iRef2));
                BTreeType *tree = getWTreeEntry(iRef2);
                tree->move_nodes(_allocator);
            }
        } else {
            assert(isBTree(typeId));
            BTreeType *tree = getWTreeEntry(iRef);
            tree->move_nodes(_allocator);
        }
    }
}

template <typename DataT>
void
PostingStore<DataT>::move(std::vector<EntryRef>& refs)
{
    for (auto& ref : refs) {
        RefType iRef(ref);
        assert(iRef.valid());
        uint32_t typeId = getTypeId(iRef);
        uint32_t clusterSize = getClusterSize(typeId);
        if (clusterSize == 0) {
            if (isBitVector(typeId)) {
                BitVectorEntry *bve = getWBitVectorEntry(iRef);
                RefType iRef2(bve->_tree);
                if (iRef2.valid()) {
                    assert(isBTree(iRef2));
                    if (_store.getCompacting(iRef2)) {
                        BTreeType *tree = getWTreeEntry(iRef2);
                        auto ref_and_ptr = allocBTreeCopy(*tree);
                        tree->prepare_hold();
                        // Note: Needs review when porting to other platforms
                        // Assumes that other CPUs observes stores from this CPU in order
                        std::atomic_thread_fence(std::memory_order_release);
                        bve->_tree = ref_and_ptr.ref;
                    }
                }
                if (_store.getCompacting(iRef)) {
                    auto new_ref = allocBitVectorCopy(*bve).ref;
                    _bvs.erase(iRef.ref());
                    _bvs.insert(new_ref.ref());
                    ref = new_ref;
                }
            } else {
                assert(isBTree(typeId));
                assert(_store.getCompacting(iRef));
                BTreeType *tree = getWTreeEntry(iRef);
                auto ref_and_ptr = allocBTreeCopy(*tree);
                tree->prepare_hold();
                ref = ref_and_ptr.ref;
            }
        } else {
            assert(_store.getCompacting(iRef));
            const KeyDataType *shortArray = getKeyDataEntry(iRef, clusterSize);
            ref = allocKeyDataCopy(shortArray, clusterSize).ref;
        }
    }
}

template <typename DataT>
void
PostingStore<DataT>::compact_worst_btree_nodes(const CompactionStrategy& compaction_strategy)
{
    auto compacting_buffers = this->start_compact_worst_btree_nodes(compaction_strategy);
    EntryRefFilter filter(RefType::numBuffers(), RefType::offset_bits);
    // Only look at buffers containing bitvectors and btree roots
    filter.add_buffers(this->_treeType.get_active_buffers());
    filter.add_buffers(_bvType.get_active_buffers());
    _dictionary.foreach_posting_list([this](const std::vector<EntryRef>& refs)
                                     { move_btree_nodes(refs); }, filter);
    compacting_buffers->finish();
}

template <typename DataT>
void
PostingStore<DataT>::compact_worst_buffers(CompactionSpec compaction_spec, const CompactionStrategy& compaction_strategy)
{

    auto compacting_buffers = this->start_compact_worst_buffers(compaction_spec, compaction_strategy);
    bool compact_btree_roots = false;
    auto filter = compacting_buffers->make_entry_ref_filter();
    // Start with looking at buffers being compacted
    for (uint32_t buffer_id : compacting_buffers->get_buffer_ids()) {
        if (isBTree(_store.getBufferState(buffer_id).getTypeId())) {
            compact_btree_roots = true;
        }
    }
    if (compact_btree_roots) {
        // If we are compacting btree roots then we also have to look at bitvector
        // buffers
        filter.add_buffers(_bvType.get_active_buffers());
    }
    _dictionary.normalize_posting_lists([this](std::vector<EntryRef>& refs)
                                        { return move(refs); },
                                        filter);
    compacting_buffers->finish();
}

template <typename DataT>
bool
PostingStore<DataT>::consider_compact_worst_btree_nodes(const CompactionStrategy& compaction_strategy)
{
    if (_allocator.getNodeStore().has_held_buffers()) {
        return false;
    }
    if (_compaction_spec.btree_nodes()) {
        compact_worst_btree_nodes(compaction_strategy);
        return true;
    }
    return false;
}

template <typename DataT>
bool
PostingStore<DataT>::consider_compact_worst_buffers(const CompactionStrategy& compaction_strategy)
{
    if (_store.has_held_buffers()) {
        return false;
    }
    if (_compaction_spec.store()) {
        CompactionSpec compaction_spec(true, false);
        compact_worst_buffers(compaction_spec, compaction_strategy);
        return true;
    }
    return false;
}

template <typename DataT>
std::unique_ptr<queryeval::SearchIterator>
PostingStore<DataT>::make_bitvector_iterator(RefType ref, uint32_t doc_id_limit, fef::TermFieldMatchData &match_data, bool strict) const
{
    if (!ref.valid()) {
        return {};
    }
    auto type_id = getTypeId(ref);
    if (!isBitVector(type_id)) {
        return {};
    }
    const auto& bv = getBitVectorEntry(ref)->_bv->reader();
    return BitVectorIterator::create(&bv, std::min(bv.size(), doc_id_limit), match_data, strict, false);
}

template class PostingStore<BTreeNoLeafData>;
template class PostingStore<int32_t>;

}
