// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastos/fastos.h>
#include <vector>
#include <set>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/common/rcuvector.h>
#include <vespa/searchlib/attribute/multivalue.h>
#include <vespa/searchlib/util/memoryusage.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include "address_space.h"
#include "enumstorebase.h"
#include <iostream>

namespace search {

namespace multivalue {

template <typename T,
          uint8_t NUM_OFFSET_BITS,
          uint8_t NUM_VALUE_BITS,
          uint8_t NUM_ALT_BITS>
class Index {
private:
    // unused X | values (NUM_VALUE_BITS bit) |
    // alternative (NUM_ALT_BITS bit) | offset (NUM_OFFSET_BITS bit)
    T _idx;
public:
    Index() : _idx(0) {}
    Index(uint32_t values_, uint32_t alternative_, uint32_t offset_)
        : _idx(0)
    {
        _idx += static_cast<T>(values_) << (NUM_ALT_BITS+NUM_OFFSET_BITS);
        _idx += static_cast<T>((alternative_) &
                               ((1<<NUM_ALT_BITS) - 1)) << NUM_OFFSET_BITS;
        _idx += offset_;
    }

    uint32_t
    values(void) const
    {
        return _idx >> (NUM_ALT_BITS+NUM_OFFSET_BITS);
    }

    uint32_t
    alternative(void) const
    {
        return (_idx >> NUM_OFFSET_BITS) & ((1<<NUM_ALT_BITS) - 1);
    }

    // values and alternative combined
    uint32_t
    vectorIdx(void) const
    {
        return _idx >> NUM_OFFSET_BITS;
    }

    uint32_t offset(void) const
    {
        return (_idx & ((1u << NUM_OFFSET_BITS) - 1));
    }

    T idx()                const { return _idx; }

    static uint32_t
    maxValues(void)
    {
        return (1 << NUM_VALUE_BITS) - 1;
    }

    static uint32_t
    alternativeSize(void)
    {
        return 1 << NUM_ALT_BITS;
    }

    static T
    offsetSize(void)
    {
        return 1 << (NUM_OFFSET_BITS);
    }
};

typedef Index<uint32_t, 27,4,1> Index32;
typedef Index<uint64_t, 31,10,1> Index64;

template <typename T, typename I>
struct MVMTemplateArg {
    typedef T Value;
    typedef I Index;
};

}

class MultiValueMappingVectorBaseBase
{
public:
    MultiValueMappingVectorBaseBase()
        : _used(0),
          _dead(0),
          _wantCompact(false),
          _usage()
    {
    }

    uint32_t used()                const { return _used; }
    uint32_t dead()                const { return _dead; }
    void incUsed(uint32_t inc)           { _used += inc; }
    void incDead(uint32_t inc)           { _dead += inc; }

    void
    setWantCompact(void)
    {
        _wantCompact = true;
    }

    bool
    getWantCompact(void) const
    {
        return _wantCompact;
    }

    MemoryUsage & getUsage()             { return _usage; }
    const MemoryUsage & getUsage() const { return _usage; }
protected:
    void reset() { _used = 0; _dead = 0; }
private:
    uint32_t _used;
    uint32_t _dead;
    bool _wantCompact;
    MemoryUsage _usage;
};


class MultiValueMappingBaseBase
{
public:
    class Histogram
    {
    private:
        typedef vespalib::hash_map<uint32_t, uint32_t> HistogramM;
    public:
        typedef HistogramM::const_iterator const_iterator;
        Histogram(size_t maxValues);
        uint32_t & operator [] (uint32_t i) { return _histogram[std::min(i, _maxValues)]; }
        const_iterator begin() const { return _histogram.begin(); }
        const_iterator end() const { return _histogram.end(); }
    private:
        uint32_t   _maxValues;
        HistogramM _histogram;
    };
protected:
    MultiValueMappingBaseBase(size_t maxValues, size_t maxAlternatives);
    virtual ~MultiValueMappingBaseBase();
    //-------------------------------------------------------------------------
    // private inner classes
    //-------------------------------------------------------------------------

    enum VectorStatus {
        ACTIVE, FREE, HOLD
    };

    typedef AttributeVector::generation_t generation_t;
    typedef vespalib::Array<VectorStatus> StatusVector;
    typedef vespalib::GenerationHolder GenerationHolder;

    // active -> hold
    void incValueCnt(uint32_t cnt) { _totalValueCnt += cnt; }
    void decValueCnt(uint32_t cnt) { _totalValueCnt -= cnt; }

    StatusVector _singleVectorsStatus;
    StatusVector _vectorVectorsStatus;
    GenerationHolder _genHolder;
    std::set<uint32_t> _pendingCompactSingleVector;
    bool _pendingCompactVectorVector;
    bool _pendingCompact;
    Histogram getEmptyHistogram(size_t maxValues) const;
    virtual const MemoryUsage & getSingleVectorUsage(size_t i) const = 0;
    virtual const MemoryUsage & getVectorVectorUsage(size_t i) const = 0;
    virtual size_t getSingleVectorAddressSpaceUsed(size_t i) const = 0;
    virtual size_t getVectorVectorAddressSpaceUsed(size_t i) const = 0;

private:
    size_t       _totalValueCnt;

public:
    virtual Histogram getEmptyHistogram() const = 0;
    virtual MemoryUsage getMemoryUsage() const = 0;
    Histogram getHistogram(AttributeVector::ReaderBase & reader) const;
    size_t getTotalValueCnt() const { return _totalValueCnt; }
    static void failNewSize(uint64_t minNewSize, uint64_t maxSize);

    void
    clearPendingCompact(void);

    static size_t
    computeNewSize(size_t used, size_t dead, size_t needed, size_t maxSize);

    void
    transferHoldLists(generation_t generation)
    {
        _genHolder.transferHoldLists(generation);
    }

    void
    trimHoldLists(generation_t firstUsed)
    {
        _genHolder.trimHoldLists(firstUsed);
    }
};


template <typename I>
class MultiValueMappingBase : public MultiValueMappingBaseBase
{
protected:
    typedef I Index;
    MultiValueMappingBase(uint32_t &committedDocIdLimit,
                          uint32_t numKeys = 0,
                          const GrowStrategy &gs = GrowStrategy());
    virtual ~MultiValueMappingBase();

    typedef search::attribute::RcuVectorBase<Index> IndexVector;
    IndexVector _indices;
    uint32_t &_committedDocIdLimit;

    // active -> hold
    void insertIntoHoldList(Index idx);
    void setActiveVector(Index idx);

    void reset(uint32_t numKeys=0);
private:
    virtual void clearVector(Index idx) = 0;

public:
    using IndexCopyVector = vespalib::Array<Index>;

    void
    doneHoldVector(Index idx);

    virtual Histogram getEmptyHistogram() const override {
        return MultiValueMappingBaseBase::getEmptyHistogram(Index::maxValues());
    }

    virtual MemoryUsage getMemoryUsage() const override;

    AddressSpace getAddressSpaceUsage() const;

    size_t getNumKeys(void) const
    {
        return _indices.size();
    }

    size_t getCapacityKeys(void) const
    {
        return _indices.capacity();
    }

    IndexCopyVector
    getIndicesCopy() const
    {
        uint32_t size = _committedDocIdLimit;
        assert(size <= _indices.size());
        return std::move(IndexCopyVector(&_indices[0], &_indices[0] + size));
    }

    bool
    hasKey(uint32_t key) const
    {
        return key < _indices.size();
    }

    bool
    hasReaderKey(uint32_t key) const
    {
        return key < _committedDocIdLimit && key < _indices.size();
    }

    bool
    isFull(void) const
    {
        return _indices.isFull();
    }

    static size_t
    maxValues(void)
    {
        return Index::maxValues();
    }

    void
    addKey(uint32_t & key);

    void
    shrinkKeys(uint32_t newSize);

    void
    clearDocs(uint32_t lidLow, uint32_t lidLimit, AttributeVector &v);

    void holdElem(Index idx, size_t size);

    virtual void doneHoldElem(Index idx) = 0;
};

extern template class MultiValueMappingBase<multivalue::Index32>;
extern template class MultiValueMappingBase<multivalue::Index64>;

template <typename V>
class MultiValueMappingFallbackVectorHold
    : public vespalib::GenerationHeldBase
{
    V _hold;
public:
    MultiValueMappingFallbackVectorHold(size_t size,
                                        V &rhs)
        : vespalib::GenerationHeldBase(size),
          _hold()
    {
        _hold.swap(rhs);
    }

    virtual
    ~MultiValueMappingFallbackVectorHold(void)
    {
    }
};


template <typename VT>
class MultiValueMappingVector : public vespalib::Array<VT>,
                                public MultiValueMappingVectorBaseBase
{
public:
    typedef vespalib::Array<VT> VectorBase;
    typedef MultiValueMappingFallbackVectorHold<VectorBase> FallBackHold;
    MultiValueMappingVector();
    MultiValueMappingVector(uint32_t n);
    MultiValueMappingVector(const MultiValueMappingVector & rhs);
    MultiValueMappingVector &
    operator=(const MultiValueMappingVector & rhs);

    ~MultiValueMappingVector();
    void reset(uint32_t n);
    uint32_t remaining() const { return this->size() - used(); }
    void swapVector(MultiValueMappingVector & rhs);

    vespalib::GenerationHeldBase::UP
    fallbackResize(uint64_t newSize);
};


template <typename T, typename I=multivalue::Index32>
class MultiValueMappingT : public MultiValueMappingBase<I>
{
public:
    friend class MultiValueMappingTest;
    typedef MultiValueMappingVectorBaseBase VectorBaseBase;
    typedef MultiValueMappingBaseBase::Histogram Histogram;
    typedef MultiValueMappingBaseBase::VectorStatus VectorStatus;
    typedef typename MultiValueMappingBase<I>::Index Index;

private:
    using MultiValueMappingBase<I>::_pendingCompactSingleVector;
    using MultiValueMappingBaseBase::_pendingCompactVectorVector;
    using MultiValueMappingBaseBase::_pendingCompact;
    using MultiValueMappingBaseBase::clearPendingCompact;
    using MultiValueMappingBaseBase::failNewSize;
    using MultiValueMappingBase<I>::_genHolder;
    using MultiValueMappingBase<I>::holdElem;

    typedef MultiValueMappingVector<T> SingleVector;
    typedef std::pair<SingleVector*, Index> SingleVectorPtr;
    typedef typename SingleVector::VectorBase VectorBase;
    typedef MultiValueMappingVector<VectorBase > VectorVector;
    typedef std::pair<VectorVector*, Index> VectorVectorPtr;

    //-------------------------------------------------------------------------
    // private variables
    //-------------------------------------------------------------------------
    std::vector<SingleVector> _singleVectors;
    std::vector<VectorVector> _vectorVectors;

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------
    virtual void clearVector(Index idx);
    virtual const MemoryUsage & getSingleVectorUsage(size_t i) const override;
    virtual const MemoryUsage & getVectorVectorUsage(size_t i) const override;
    virtual size_t getSingleVectorAddressSpaceUsed(size_t i) const override;
    virtual size_t getVectorVectorAddressSpaceUsed(size_t i) const override;
    void initVectors(uint32_t initSize);
    void initVectors(const Histogram & initCapacity);
    bool getValidIndex(Index & newIdx, uint32_t numValues);

    void
    compactSingleVector(SingleVectorPtr &activeVector,
                        uint32_t valueCnt,
                        uint64_t newSize,
                        uint64_t neededEntries,
                        uint64_t maxSize);

    void
    compactVectorVector(VectorVectorPtr &activeVector,
                        uint64_t newSize,
                        uint64_t neededEntries,
                        uint64_t maxSize);

    SingleVectorPtr getSingleVector(uint32_t numValues, VectorStatus status);
    VectorVectorPtr getVectorVector(VectorStatus status);
    Index getIndex(uint32_t numValues, VectorStatus status);

    void incUsed(SingleVector & vec, uint32_t numValues) {
        vec.incUsed(numValues);
        vec.getUsage().incUsedBytes(numValues * sizeof(T));
    }
    void incDead(SingleVector & vec, uint32_t numValues) {
        vec.incDead(numValues);
        vec.getUsage().incDeadBytes(numValues * sizeof(T));
    }
    void swapVector(SingleVector & vec, uint32_t initSize) {
        SingleVector(initSize).swapVector(vec);
        vec.getUsage().setAllocatedBytes(initSize * sizeof(T));
    }
    void incUsed(VectorVector & vec, uint32_t numValues) {
        vec.incUsed(1);
        vec.getUsage().incUsedBytes(numValues * sizeof(T) +
                                    sizeof(VectorBase));
        vec.getUsage().incAllocatedBytes(numValues * sizeof(T));
    }
    void incDead(VectorVector & vec) {
        vec.incDead(1);
    }
    void swapVector(VectorVector & vec, uint32_t initSize) {
        VectorVector(initSize).swapVector(vec);
        vec.getUsage().setAllocatedBytes(initSize * sizeof(VectorBase));
    }


public:
    MultiValueMappingT(uint32_t &committedDocIdLimit,
                       const GrowStrategy & gs = GrowStrategy());
    MultiValueMappingT(uint32_t &committedDocIdLimit,
                       uint32_t numKeys, uint32_t initSize = 0,
                       const GrowStrategy & gs = GrowStrategy());
    MultiValueMappingT(uint32_t &committedDocIdLimit,
                       uint32_t numKeys, const Histogram & initCapacity,
                       const GrowStrategy & gs = GrowStrategy());
    ~MultiValueMappingT();
    void reset(uint32_t numKeys, uint32_t initSize = 0);
    void reset(uint32_t numKeys, const Histogram & initCapacity);
    uint32_t get(uint32_t key, std::vector<T> & buffer) const;
    template <typename BufferType>
    uint32_t get(uint32_t key, BufferType * buffer, uint32_t sz) const;
    bool get(uint32_t key, uint32_t index, T & value) const;
    uint32_t getDataForIdx(Index idx, const T * & handle) const {
        if (__builtin_expect(idx.values() < Index::maxValues(), true)) {
            // We do not need to specialcase 0 as _singleVectors will refer to valid stuff
            // and handle SHALL not be used as the number of values returned shall be obeyed.
            const SingleVector & vec = _singleVectors[idx.vectorIdx()];
            handle = &vec[idx.offset() * idx.values()];
            __builtin_prefetch(handle, 0, 0);
            return idx.values();
        } else {
            const VectorBase & vec =
                _vectorVectors[idx.alternative()][idx.offset()];
            handle = &vec[0];
            return vec.size();
        }
    }
    uint32_t get(uint32_t key, const T * & handle) const {
        return getDataForIdx(this->_indices[key], handle);
    }
    inline uint32_t getValueCount(uint32_t key) const;
    void set(uint32_t key, const std::vector<T> & values);
    void set(uint32_t key, const T * values, uint32_t numValues);

    /* XXX: Unsafe operation, reader gets inconsistent view */
    void replace(uint32_t key, const std::vector<T> & values);

    /* XXX: Unsafe operation, reader gets inconsistent view */
    void replace(uint32_t key, const T * values, uint32_t numValues);

    Histogram getRemaining();
    bool enoughCapacity(const Histogram & capacityNeeded);
    void performCompaction(Histogram & capacityNeeded);

    template <typename V, class Saver>
    uint32_t
    fillMapped(AttributeVector::ReaderBase &attrReader,
               uint64_t numValues,
               const V *map,
               size_t mapSize,
               Saver &saver,
               uint32_t numDocs,
               bool hasWeights);

    virtual void doneHoldElem(Index idx) override;

#ifdef DEBUG_MULTIVALUE_MAPPING
    void printContent() const;
    void printVectorVectors() const;
#endif
};

//-----------------------------------------------------------------------------
// implementation of private methods
//-----------------------------------------------------------------------------
template <typename VT>
MultiValueMappingVector<VT>::MultiValueMappingVector()
    : VectorBase(),
      MultiValueMappingVectorBaseBase()
{
}

template <typename VT>
MultiValueMappingVector<VT>::~MultiValueMappingVector()
{
}

template <typename VT>
MultiValueMappingVector<VT>::MultiValueMappingVector(uint32_t n)
    : VectorBase(),
      MultiValueMappingVectorBaseBase()
{
    reset(n);
}

template <typename VT>
MultiValueMappingVector<VT>::MultiValueMappingVector(
        const MultiValueMappingVector & rhs)
    : VectorBase(rhs),
      MultiValueMappingVectorBaseBase(rhs)
{
}

template <typename VT>
MultiValueMappingVector<VT> &
MultiValueMappingVector<VT>::operator=(const MultiValueMappingVector & rhs)
{
    if (this != & rhs) {
        VectorBase::operator=(rhs);
        MultiValueMappingVectorBaseBase::operator=(rhs);
    }
    return *this;
}

template <typename VT>
void
MultiValueMappingVector<VT>::reset(uint32_t n)
{
    this->resize(n);
    MultiValueMappingVectorBaseBase::reset();
}

template <typename VT>
void
MultiValueMappingVector<VT>::swapVector(MultiValueMappingVector & rhs)
{
    MultiValueMappingVectorBaseBase tmp(rhs);
    rhs.MultiValueMappingVectorBaseBase::operator=(*this);
    MultiValueMappingVectorBaseBase::operator=(tmp);
    this->swap(rhs);
}

template <typename VT>
vespalib::GenerationHeldBase::UP
MultiValueMappingVector<VT>::fallbackResize(uint64_t newSize)
{
    VectorBase tmp(newSize);
    VectorBase &old(*this);
    size_t oldSize = old.size();
    size_t oldCapacity = old.capacity();
    for (size_t i = 0; i < oldSize; ++i) {
        tmp[i] = old[i];
    }
    std::atomic_thread_fence(std::memory_order_release);
    this->swap(tmp);
    return vespalib::GenerationHeldBase::UP(
            new MultiValueMappingFallbackVectorHold<VectorBase>
            (oldCapacity * sizeof(VT),
             tmp));
}

template <typename T, typename I>
void
MultiValueMappingT<T, I>::initVectors(uint32_t initSize)
{
    for (uint32_t i = 0; i < this->_singleVectorsStatus.size(); ++i) {
        if (i % Index::alternativeSize() == 0) {
            swapVector(_singleVectors[i], initSize);
            this->_singleVectorsStatus[i] = MultiValueMappingBaseBase::ACTIVE;
        } else {
            swapVector(_singleVectors[i], 0);
            this->_singleVectorsStatus[i] = MultiValueMappingBaseBase::FREE;
        }
    }
    for (uint32_t i = 0; i < this->_vectorVectorsStatus.size(); ++i) {
        if (i % Index::alternativeSize() == 0) {
            swapVector(_vectorVectors[i], initSize);
            this->_vectorVectorsStatus[i] = MultiValueMappingBaseBase::ACTIVE;
        } else {
            swapVector(_vectorVectors[i], 0);
            this->_vectorVectorsStatus[i] = MultiValueMappingBaseBase::FREE;
        }
    }
}

template <typename T, typename I>
void
MultiValueMappingT<T, I>::initVectors(const Histogram &initCapacity)
{
    for (typename Histogram::const_iterator it(initCapacity.begin()), mt(initCapacity.end()); it != mt; ++it) {
        uint32_t valueCnt = it->first;
        uint64_t numEntries = it->second;
        if (valueCnt != 0 && valueCnt < Index::maxValues()) {
            uint64_t maxSize = Index::offsetSize() * valueCnt;
            if (maxSize > std::numeric_limits<uint32_t>::max()) {
                maxSize = std::numeric_limits<uint32_t>::max();
                maxSize -= (maxSize % valueCnt);
            }
            if (numEntries * valueCnt > maxSize) {
                failNewSize(numEntries * valueCnt, maxSize);
            }
            swapVector(_singleVectors[valueCnt * 2], valueCnt * numEntries);
        } else if (valueCnt == Index::maxValues()) {
            uint64_t maxSize = Index::offsetSize();
            if (maxSize > std::numeric_limits<uint32_t>::max())
                maxSize = std::numeric_limits<uint32_t>::max();
            if (numEntries > maxSize) {
                failNewSize(numEntries, maxSize);
            }
            swapVector(_vectorVectors[0], numEntries);
        }
    }
}

template <typename T, typename I>
bool
MultiValueMappingT<T, I>::getValidIndex(Index &newIdx, uint32_t numValues)
{
    if (numValues == 0) {
        newIdx = Index();
    } else if (numValues < Index::maxValues()) {
        SingleVectorPtr active =
            getSingleVector(numValues, MultiValueMappingBaseBase::ACTIVE);

        if (active.first->remaining() < numValues) {
            return false;
        }

        uint32_t used = active.first->used();
        assert(used % numValues == 0);
        incUsed(*active.first, numValues);
        newIdx = Index(active.second.values(), active.second.alternative(),
                       used / numValues);
    } else {
        VectorVectorPtr active =
            getVectorVector(MultiValueMappingBaseBase::ACTIVE);

        if (active.first->remaining() == 0) {
            return false;
        }

        uint32_t used = active.first->used();
        incUsed(*active.first, numValues);
        (*active.first)[used].resize(numValues);
        newIdx = Index(active.second.values(), active.second.alternative(),
                       used);
    }
    return true;
}

template <typename T, typename I>
void
MultiValueMappingT<T, I>::
compactSingleVector(SingleVectorPtr &activeVector,
                    uint32_t valueCnt,
                    uint64_t newSize,
                    uint64_t neededEntries,
                    uint64_t maxSize)
{
    _pendingCompactSingleVector.erase(activeVector.second.values());
    clearPendingCompact();
    SingleVectorPtr freeVector =
        getSingleVector(valueCnt, MultiValueMappingBaseBase::FREE);
    if (freeVector.first == NULL) {
#ifdef LOG_MULTIVALUE_MAPPING
        LOG(warning, "did not find any free '%u-vector'", valueCnt);
#endif
        uint64_t dead = activeVector.first->dead();
        uint64_t fallbackNewSize = newSize + dead * valueCnt + 1024 * valueCnt;
        if (fallbackNewSize > maxSize)
            fallbackNewSize = maxSize;
        if (fallbackNewSize <= activeVector.first->size() ||
            fallbackNewSize < activeVector.first->used() +
            neededEntries * valueCnt) {
            fprintf(stderr, "did not find any free '%u-vector'\n", valueCnt);
            abort();
        }
        _genHolder.hold(activeVector.first->fallbackResize(fallbackNewSize));
        // When held buffer is freed then pending compact should be set
        SingleVectorPtr holdVector =
            getSingleVector(valueCnt, MultiValueMappingBaseBase::HOLD);
        assert(holdVector.first != NULL);
        holdVector.first->setWantCompact();
        return;
    }
    swapVector(*freeVector.first, newSize);
#ifdef LOG_MULTIVALUE_MAPPING
    LOG(info,
        "compacting from '%u-vector(%u)' "
        "(s = %u, u = %u, d = %u) to "
        "'%u-vector(%u)' (s = %u)",
        valueCnt, activeVector.second.alternative(),
        activeVector.first->size(),
        activeVector.first->used() , activeVector.first->dead(),
        valueCnt, freeVector.second.alternative(), newSize);
#endif
    uint32_t activeVectorIdx = activeVector.second.vectorIdx();
    for (uint32_t i = 0; i < this->_indices.size(); ++i) {
        Index & idx = this->_indices[i];
        if (activeVectorIdx == idx.vectorIdx()) {
            for (uint32_t j = idx.offset() * idx.values(),
                          k = freeVector.first->used();
                 j < (idx.offset() + 1) * idx.values() &&
                 k < freeVector.first->used() + valueCnt; ++j, ++k)
            {
                (*freeVector.first)[k] = (*activeVector.first)[j];
            }
            assert(freeVector.first->used() % valueCnt == 0);
            std::atomic_thread_fence(std::memory_order_release);
            this->_indices[i] = Index(freeVector.second.values(),
                                      freeVector.second.alternative(),
                                      freeVector.first->used() / valueCnt);
            incUsed(*freeVector.first, valueCnt);
        }
    }
    // active -> hold
    this->insertIntoHoldList(activeVector.second);
    // free -> active
    this->setActiveVector(freeVector.second);
    activeVector = freeVector;
}


template <typename T, typename I>
void
MultiValueMappingT<T, I>::
compactVectorVector(VectorVectorPtr &activeVector,
                    uint64_t newSize,
                    uint64_t neededEntries,
                    uint64_t maxSize)
{
    _pendingCompactVectorVector = false;
    clearPendingCompact();
    VectorVectorPtr freeVector =
        getVectorVector(MultiValueMappingBaseBase::FREE);
    if (freeVector.first == NULL) {
#ifdef LOG_MULTIVALUE_MAPPING
        LOG(error, "did not find any free vectorvector");
#endif
        uint64_t dead = activeVector.first->dead();
        uint64_t fallbackNewSize = newSize + dead + 1024;
        if (fallbackNewSize > maxSize)
            fallbackNewSize = maxSize;
        if (fallbackNewSize <= activeVector.first->size() ||
            fallbackNewSize < activeVector.first->used() + neededEntries) {
            fprintf(stderr, "did not find any free vectorvector\n");
            abort();
        }
        _genHolder.hold(activeVector.first->fallbackResize(fallbackNewSize));
        // When held buffer is freed then pending compact should be set
        VectorVectorPtr holdVector =
            getVectorVector(MultiValueMappingBaseBase::HOLD);
        assert(holdVector.first != NULL);
        holdVector.first->setWantCompact();
        return;
    }
    swapVector(*freeVector.first, newSize);
#ifdef LOG_MULTIVALUE_MAPPING
    LOG(info,
        "compacting from 'vectorvector(%u)' "
        "(s = %u, u = %u, d = %u) to "
        "'vectorvector(%u)' (s = %u)",
        activeVector.second.alternative(), activeVector.first->size(),
        activeVector.first->used(), activeVector.first->dead(),
        freeVector.second.alternative(), newSize);
#endif
    uint32_t activeVectorIdx = activeVector.second.vectorIdx();
    for (uint32_t i = 0; i < this->_indices.size(); ++i) {
        Index & idx = this->_indices[i];
        if (activeVectorIdx == idx.vectorIdx()) {
            uint32_t activeOffset = idx.offset();
            uint32_t vecSize = (*activeVector.first)[activeOffset].size();
            uint32_t freeOffset = freeVector.first->used();
            (*freeVector.first)[freeOffset].resize(vecSize);
            for (uint32_t j = 0; j < vecSize; ++j) {
                (*freeVector.first)[freeOffset][j] =
                    (*activeVector.first)[activeOffset][j];
            }
            std::atomic_thread_fence(std::memory_order_release);
            this->_indices[i] = Index(freeVector.second.values(),
                                      freeVector.second.alternative(),
                                      freeVector.first->used());
            incUsed(*freeVector.first, vecSize);
        }
    }
    // active -> hold
    this->insertIntoHoldList(activeVector.second);
    // free -> active
    this->setActiveVector(freeVector.second);
    activeVector = freeVector;
}

template <typename T, typename I>
typename MultiValueMappingT<T, I>::SingleVectorPtr
MultiValueMappingT<T, I>::getSingleVector(uint32_t numValues,
        VectorStatus status)
{
    for (uint32_t i = numValues * Index::alternativeSize();
         i < (numValues + 1) * Index::alternativeSize(); ++i)
    {
        if (this->_singleVectorsStatus[i] == status) {
            return SingleVectorPtr(&_singleVectors[i],
                                   Index(numValues,
                                           i % Index::alternativeSize(),
                                           0));
        }
    }
    return SingleVectorPtr(static_cast<SingleVector *>(NULL), Index());
}

template <typename T, typename I>
typename MultiValueMappingT<T, I>::VectorVectorPtr
MultiValueMappingT<T, I>::getVectorVector(VectorStatus status)
{
    for (uint32_t i = 0; i < _vectorVectors.size(); ++i) {
        if (this->_vectorVectorsStatus[i] == status) {
            return VectorVectorPtr(&_vectorVectors[i],
                                   Index(Index::maxValues(), i, 0));
        }
    }
    return VectorVectorPtr(static_cast<VectorVector *>(NULL), Index());
}

template <typename T, typename I>
typename MultiValueMappingT<T, I>::Index
MultiValueMappingT<T, I>::getIndex(uint32_t numValues, VectorStatus status)
{
    if (numValues < Index::maxValues()) {
        return getSingleVector(numValues, status).second;
    } else {
        return getVectorVector(status).second;
    }
}


//-----------------------------------------------------------------------------
// implementation of public methods
//-----------------------------------------------------------------------------

template <typename T, typename I>
MultiValueMappingT<T, I>::MultiValueMappingT(uint32_t &committedDocIdLimit,
                                             const GrowStrategy & gs)
    : MultiValueMappingBase<I>(committedDocIdLimit, 0, gs),
      _singleVectors((Index::maxValues()) * Index::alternativeSize()),
      _vectorVectors(Index::alternativeSize())
{
    initVectors(0);
}

template <typename T, typename I>
MultiValueMappingT<T, I>::MultiValueMappingT(uint32_t &committedDocIdLimit,
                                             uint32_t numKeys,
        uint32_t initSize,
        const GrowStrategy & gs)
    : MultiValueMappingBase<I>(committedDocIdLimit, numKeys, gs),
      _singleVectors((Index::maxValues()) * Index::alternativeSize()),
      _vectorVectors(Index::alternativeSize())
{
    initVectors(initSize);
}

template <typename T, typename I>
MultiValueMappingT<T, I>::MultiValueMappingT(uint32_t &committedDocIdLimit,
                                             uint32_t numKeys,
        const Histogram & initCapacity,
        const GrowStrategy & gs)
    : MultiValueMappingBase<I>(committedDocIdLimit, numKeys, gs),
      _singleVectors((Index::maxValues()) * Index::alternativeSize()),
      _vectorVectors(Index::alternativeSize())
{
    initVectors(0);
    initVectors(initCapacity);
}

template <typename T, typename I>
MultiValueMappingT<T, I>::~MultiValueMappingT()
{
    _genHolder.clearHoldLists();
}

template <typename T, typename I>
void
MultiValueMappingT<T, I>::reset(uint32_t numKeys, uint32_t initSize)
{
    MultiValueMappingBase<I>::reset(numKeys);
    initVectors(initSize);
}

template <typename T, typename I>
void
MultiValueMappingT<T, I>::reset(uint32_t numKeys,
                                const Histogram &initCapacity)
{
    MultiValueMappingBase<I>::reset(numKeys);
    initVectors(0);
    initVectors(initCapacity);
}


template <typename T, typename I>
uint32_t
MultiValueMappingT<T, I>::get(uint32_t key, std::vector<T> & buffer) const
{
    return get(key, &buffer[0], buffer.size());
}

template <typename T, typename I>
template <typename BufferType>
uint32_t
MultiValueMappingT<T, I>::get(uint32_t key,
                              BufferType * buffer,
                              uint32_t sz) const
{
    Index idx = this->_indices[key];
    if (idx.values() < Index::maxValues()) {
        uint32_t available = idx.values();
        uint32_t num2Read = std::min(available, sz);
        const SingleVector & vec = _singleVectors[idx.vectorIdx()];
        for (uint32_t i = 0, j = idx.offset() * idx.values();
             i < num2Read && j < (idx.offset() + 1) * idx.values(); ++i, ++j) {
            buffer[i] = static_cast<BufferType>(vec[j]);
        }
        return available;
    } else {
        const VectorBase & vec =
            _vectorVectors[idx.alternative()][idx.offset()];
        uint32_t available = vec.size();
        uint32_t num2Read = std::min(available, sz);
        for (uint32_t i = 0; i < num2Read; ++i) {
            buffer[i] = static_cast<BufferType>(vec[i]);
        }
        return available;
    }
}

template <typename T, typename I>
bool
MultiValueMappingT<T, I>::get(uint32_t key, uint32_t index, T & value) const
{
    if (!this->hasReaderKey(key)) {
        return false;
    }
    Index idx = this->_indices[key];
    if (idx.values() < Index::maxValues()) {
        if (index >= idx.values()) {
            return false;
        }
        uint32_t offset = idx.offset() * idx.values() + index;
        value = _singleVectors[idx.vectorIdx()][offset];
        return true;
    } else {
        if (index >= _vectorVectors[idx.alternative()][idx.offset()].size()) {
            return false;
        }
        value = _vectorVectors[idx.alternative()][idx.offset()][index];
        return true;
    }
    return false;
}

template <typename T, typename I>
inline uint32_t
MultiValueMappingT<T, I>::getValueCount(uint32_t key) const
{
    if (!this->hasReaderKey(key)) {
        return 0;
    }
    Index idx = this->_indices[key];
    if (idx.values() < Index::maxValues()) {
        return idx.values();
    } else {
        return _vectorVectors[idx.alternative()][idx.offset()].size();
    }
}

template <typename T, typename I>
void
MultiValueMappingT<T, I>::set(uint32_t key, const std::vector<T> & values)
{
    set(key, &values[0], values.size());
}

template <typename T, typename I>
void
MultiValueMappingT<T, I>::set(uint32_t key,
                              const T * values,
                              uint32_t numValues)
{
    if (!this->hasKey(key)) {
        abort();
    }

    Index oldIdx = this->_indices[key];
    Index newIdx;
    if (!getValidIndex(newIdx, numValues)) {
        abort();
    }
#ifdef LOG_MULTIVALUE_MAPPING
    LOG(info,
        "newIdx: values = %u, alternative = %u, offset = %u",
        newIdx.values(), newIdx.alternative(), newIdx.offset());
#endif

    if (newIdx.values() != 0 && newIdx.values() < Index::maxValues()) {
        SingleVector & vec = _singleVectors[newIdx.vectorIdx()];
        for (uint32_t i = newIdx.offset() * newIdx.values(), j = 0;
             i < (newIdx.offset() + 1) * newIdx.values() && j < numValues;
             ++i, ++j)
        {
            vec[i] = values[j];
        }
#ifdef LOG_MULTIVALUE_MAPPING
        LOG(info,
            "inserted in '%u-vector(%u)': "
            "key = %u, size = %u, used = %u, dead = %u, offset = %u",
            newIdx.values(), newIdx.alternative(),
            key, vec.size(),
            vec.used(), vec.dead(), newIdx.offset() * newIdx.values());
#endif
    } else if (newIdx.values() == Index::maxValues()) {
        VectorVector & vec = _vectorVectors[newIdx.alternative()];
        for (uint32_t i = 0; i < numValues; ++i) {
            vec[newIdx.offset()][i] = values[i];
        }
#ifdef LOG_MULTIVALUE_MAPPING
        LOG(info,
            "inserted %u values in 'vector-vector(%u)': "
            "key = %u, size = %u, used = %u, dead = %u, offset = %u",
            numValues, newIdx.alternative(),
            key, vec.size(), vec.used(), vec.dead(), newIdx.offset());
#endif
    }

    std::atomic_thread_fence(std::memory_order_release);
    this->_indices[key] = newIdx;
    this->incValueCnt(numValues);

    // mark space in oldIdx as dead;
    if (oldIdx.values() != 0 && oldIdx.values() < Index::maxValues()) {
        SingleVector & vec = _singleVectors[oldIdx.vectorIdx()];
        incDead(vec, oldIdx.values());
        this->decValueCnt(oldIdx.values());
#ifdef LOG_MULTIVALUE_MAPPING
        LOG(info,
            "mark space dead in '%u-vector(%u)': "
            "size = %u, used = %u, dead = %u",
            oldIdx.values(), oldIdx.alternative(),
            vec.size(), vec.used(), vec.dead());
#endif
    } else if (oldIdx.values() == Index::maxValues()) {
        VectorVector & vec = _vectorVectors[oldIdx.alternative()];
        uint32_t oldNumValues = vec[oldIdx.offset()].size();
        incDead(vec);
        this->decValueCnt(oldNumValues);
        holdElem(oldIdx, sizeof(VectorBase) + sizeof(T) * oldNumValues);
#ifdef LOG_MULTIVALUE_MAPPING
        LOG(info,
            "mark space dead in 'vector-vector(%u)': "
            "size = %u, used = %u, dead = %u",
            oldIdx.alternative(), vec.size(), vec.used(), vec.dead());
#endif
    }
}

template <typename T, typename I>
void
MultiValueMappingT<T, I>::replace(uint32_t key, const std::vector<T> & values)
{
    /* XXX: Unsafe operation, reader gets inconsistent view */
    replace(key, &values[0], values.size());
}

template <typename T, typename I>
void
MultiValueMappingT<T, I>::replace(uint32_t key,
                                  const T * values, uint32_t numValues)
{
    /* XXX: Unsafe operation, reader gets inconsistent view */
    if (!this->hasKey(key)) {
        abort();
    }

    Index currIdx = this->_indices[key];

    if (currIdx.values() != 0 && currIdx.values() < Index::maxValues()) {
        SingleVector & vec = _singleVectors[currIdx.vectorIdx()];
        for (uint32_t i = currIdx.offset() * currIdx.values(), j = 0;
             i < (currIdx.offset() + 1) * currIdx.values() && j < numValues;
             ++i, ++j)
        {
            vec[i] = values[j];
        }
    } else if (currIdx.values() == Index::maxValues()) {
        VectorBase & vec =
            _vectorVectors[currIdx.alternative()][currIdx.offset()];
        for (uint32_t i = 0; i < vec.size() && i < numValues; ++i) {
            vec[i] = values[i];
        }
    }
}


template <typename T, typename I>
void MultiValueMappingT<T, I>::clearVector(Index idx)
{
    if (idx.values() < Index::maxValues()) {
        SingleVector &vec = _singleVectors[idx.vectorIdx()];
        if (vec.getWantCompact()) {
            _pendingCompactSingleVector.insert(idx.values());
            _pendingCompact = true;
        }
        vec = SingleVector();
    } else {
        VectorVector &vec = _vectorVectors[idx.alternative()];
        if (vec.getWantCompact()) {
            _pendingCompactVectorVector = true;
            _pendingCompact = true;
        }
        vec = VectorVector();
    }
}


template <typename T, typename I>
void
MultiValueMappingT<T, I>::doneHoldElem(Index idx)
{
    assert(idx.values() == Index::maxValues());
    VectorVector &vv = _vectorVectors[idx.alternative()];
    VectorBase &v = vv[idx.offset()];
    uint32_t numValues = v.size();
    VectorBase().swap(v);
    vv.getUsage().decAllocatedBytes(numValues * sizeof(T));
    vv.getUsage().incDeadBytes(sizeof(VectorBase));
}


template <typename T, typename I>
const MemoryUsage &
MultiValueMappingT<T, I>::getSingleVectorUsage(size_t i) const
{
    return _singleVectors[i].getUsage();
}

template <typename T, typename I>
const MemoryUsage &
MultiValueMappingT<T, I>::getVectorVectorUsage(size_t i) const
{
    return _vectorVectors[i].getUsage();
}

template <typename T, typename I>
size_t
MultiValueMappingT<T, I>::getSingleVectorAddressSpaceUsed(size_t i) const
{
    if (i < Index::alternativeSize()) {
        return 0;
    }
    size_t numValues = i / Index::alternativeSize();
    size_t actualUsed = _singleVectors[i].used() - _singleVectors[i].dead();
    return (actualUsed / numValues);
}

template <typename T, typename I>
size_t
MultiValueMappingT<T, I>::getVectorVectorAddressSpaceUsed(size_t i) const
{
    return _vectorVectors[i].used() - _vectorVectors[i].dead();
}

template <typename T, typename I>
typename MultiValueMappingT<T, I>::Histogram
MultiValueMappingT<T, I>::getRemaining()
{
    Histogram result(Index::maxValues());
    result[0] = 0;
    for (uint32_t key = 1; key < Index::maxValues(); ++key) {
        SingleVectorPtr active =
            getSingleVector(key, MultiValueMappingBaseBase::ACTIVE);
        result[key] = active.first->remaining() / key;
    }
    VectorVectorPtr active =
        getVectorVector(MultiValueMappingBaseBase::ACTIVE);
    result[Index::maxValues()] = active.first->remaining();
    return result;
}

template <typename T, typename I>
bool
MultiValueMappingT<T, I>::enoughCapacity(const Histogram & capacityNeeded)
{
    if (_pendingCompact)
        return false;
    for (typename Histogram::const_iterator it(capacityNeeded.begin()), mt(capacityNeeded.end()); it != mt; ++it) {
        uint32_t valueCnt = it->first;
        uint64_t numEntries = it->second;
        if (valueCnt < Index::maxValues()) {
            SingleVectorPtr active =
                getSingleVector(valueCnt, MultiValueMappingBaseBase::ACTIVE);
            if (active.first->remaining() < numEntries * valueCnt) {
                return false;
            }
        } else if (valueCnt == Index::maxValues()) {
            VectorVectorPtr active =
                getVectorVector(MultiValueMappingBaseBase::ACTIVE);
            if (active.first->remaining() < numEntries) {
                return false;
            }
        }
    }
    return true;
}

template <typename T, typename I>
void
MultiValueMappingT<T, I>::performCompaction(Histogram & capacityNeeded)
{
#ifdef LOG_MULTIVALUE_MAPPING
    LOG(info, "performCompaction()");
#endif
    if (_pendingCompact) {
        // Further populate histogram to ensure pending compaction being done.
        for (std::set<uint32_t>::const_iterator
                 pit(_pendingCompactSingleVector.begin()),
                 pmt(_pendingCompactSingleVector.end());
             pit != pmt; ++pit) {
            (void) capacityNeeded[*pit];
        }
        if (_pendingCompactVectorVector) {
            (void) capacityNeeded[Index::maxValues()];
        }
    }
    for (typename Histogram::const_iterator it(capacityNeeded.begin()), mt(capacityNeeded.end()); it != mt; ++it) {
        uint32_t valueCnt = it->first;
        uint64_t numEntries = it->second;
        if (valueCnt != 0 && valueCnt < Index::maxValues()) {
            SingleVectorPtr active =
                getSingleVector(valueCnt, MultiValueMappingBaseBase::ACTIVE);

            if (active.first->remaining() < valueCnt * numEntries ||
                _pendingCompactSingleVector.find(valueCnt) !=
                _pendingCompactSingleVector.end()) {
                uint64_t maxSize = Index::offsetSize() * valueCnt;
                if (maxSize > std::numeric_limits<uint32_t>::max()) {
                    maxSize = std::numeric_limits<uint32_t>::max();
                    maxSize -= (maxSize % valueCnt);
                }
                uint64_t newSize = this->computeNewSize(active.first->used(),
                        active.first->dead(),
                        valueCnt * numEntries,
                        maxSize);
                compactSingleVector(active, valueCnt, newSize,
                                    numEntries, maxSize);
            }
        } else if (valueCnt == Index::maxValues()) {
            VectorVectorPtr active =
                getVectorVector(MultiValueMappingBaseBase::ACTIVE);

            if (active.first->remaining() < numEntries ||
                _pendingCompactVectorVector) {
                uint64_t maxSize = Index::offsetSize();
                if (maxSize > std::numeric_limits<uint32_t>::max())
                    maxSize = std::numeric_limits<uint32_t>::max();
                uint64_t newSize = this->computeNewSize(active.first->used(),
                        active.first->dead(),
                        numEntries,
                        maxSize);
                compactVectorVector(active, newSize,
                                    numEntries, maxSize);
            }
        }
    }
    assert(!_pendingCompact);
}

#ifdef DEBUG_MULTIVALUE_MAPPING
template <typename T, typename I>
void
MultiValueMappingT<T, I>::printContent() const
{
    for (uint32_t key = 0; key < this->_indices.size(); ++key) {
        std::vector<T> buffer(getValueCount(key));
        get(key, buffer);
        std::cout << "key = " << key << ", count = " <<
            getValueCount(key) << ": ";
        for (uint32_t i = 0; i < buffer.size(); ++i) {
            std::cout << buffer[i] << ", ";
        }
        std::cout << '\n';
    }
}

template <typename T, typename I>
void
MultiValueMappingT<T, I>::printVectorVectors() const
{
    for (uint32_t i = 0; i < _vectorVectors.size(); ++i) {
        std::cout << "Alternative " << i << '\n';
        for (uint32_t j = 0; j < _vectorVectors[i].size(); ++j) {
            std::cout << "Vector " << j << ": [";
            uint32_t size = _vectorVectors[i][j].size();
            for (uint32_t k = 0; k < size; ++k) {
                std::cout << _vectorVectors[i][j][k] << ", ";
            }
            std::cout << "]\n";
        }
    }
}
#endif

extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::Value<int8_t> >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::Value<int16_t> >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::Value<int32_t> >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::Value<int64_t> >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::Value<float> >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::Value<double> >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::Value<EnumStoreBase::Index> >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::WeightedValue<int8_t> >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::WeightedValue<int16_t> >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::WeightedValue<int32_t> >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::WeightedValue<int64_t> >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::WeightedValue<float> >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::WeightedValue<double> >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<multivalue::WeightedValue<EnumStoreBase::Index> >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::Value<int8_t> > >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::Value<int16_t> > >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::Value<int32_t> > >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::Value<int64_t> > >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::Value<float> > >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::Value<double> > >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::Value<EnumStoreBase::Index> > >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::WeightedValue<int8_t> > >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::WeightedValue<int16_t> > >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::WeightedValue<int32_t> > >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::WeightedValue<int64_t> > >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::WeightedValue<float> > >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::WeightedValue<double> > >::VectorBase >;
extern template class MultiValueMappingFallbackVectorHold<
    MultiValueMappingVector<vespalib::Array<multivalue::WeightedValue<EnumStoreBase::Index> > >::VectorBase >;

extern template class MultiValueMappingVector<
    multivalue::Value<int8_t> >;
extern template class MultiValueMappingVector<
    multivalue::Value<int16_t> >;
extern template class MultiValueMappingVector<
    multivalue::Value<int32_t> >;
extern template class MultiValueMappingVector<
    multivalue::Value<int64_t> >;
extern template class MultiValueMappingVector<
    multivalue::Value<float> >;
extern template class MultiValueMappingVector<
    multivalue::Value<double> >;
extern template class MultiValueMappingVector<
    multivalue::Value<EnumStoreBase::Index> >;
extern template class MultiValueMappingVector<
    multivalue::WeightedValue<int8_t> >;
extern template class MultiValueMappingVector<
    multivalue::WeightedValue<int16_t> >;
extern template class MultiValueMappingVector<
    multivalue::WeightedValue<int32_t> >;
extern template class MultiValueMappingVector<
    multivalue::WeightedValue<int64_t> >;
extern template class MultiValueMappingVector<
    multivalue::WeightedValue<float> >;
extern template class MultiValueMappingVector<
    multivalue::WeightedValue<double> >;
extern template class MultiValueMappingVector<
    multivalue::WeightedValue<EnumStoreBase::Index> >;
extern template class MultiValueMappingVector<
    vespalib::Array<multivalue::Value<int8_t> > >;
extern template class MultiValueMappingVector<
    vespalib::Array<multivalue::Value<int16_t> > >;
extern template class MultiValueMappingVector<
    vespalib::Array<multivalue::Value<int32_t> > >;
extern template class MultiValueMappingVector<
    vespalib::Array<multivalue::Value<int64_t> > >;
extern template class MultiValueMappingVector<
    vespalib::Array<multivalue::Value<float> > >;
extern template class MultiValueMappingVector<
    vespalib::Array<multivalue::Value<double> > >;
extern template class MultiValueMappingVector<
    vespalib::Array<multivalue::Value<EnumStoreBase::Index> > >;
extern template class MultiValueMappingVector<
    vespalib::Array<multivalue::WeightedValue<int8_t> > >;
extern template class MultiValueMappingVector<
    vespalib::Array<multivalue::WeightedValue<int16_t> > >;
extern template class MultiValueMappingVector<
    vespalib::Array<multivalue::WeightedValue<int32_t> > >;
extern template class MultiValueMappingVector<
    vespalib::Array<multivalue::WeightedValue<int64_t> > >;
extern template class MultiValueMappingVector<
    vespalib::Array<multivalue::WeightedValue<float> > >;
extern template class MultiValueMappingVector<
    vespalib::Array<multivalue::WeightedValue<double> > >;
extern template class MultiValueMappingVector<
    vespalib::Array<multivalue::WeightedValue<EnumStoreBase::Index> > >;

extern template class MultiValueMappingT<multivalue::Value<int8_t> >;
extern template class MultiValueMappingT<multivalue::Value<int16_t> >;
extern template class MultiValueMappingT<multivalue::Value<int32_t> >;
extern template class MultiValueMappingT<multivalue::Value<int64_t> >;
extern template class MultiValueMappingT<multivalue::Value<float> >;
extern template class MultiValueMappingT<multivalue::Value<double> >;
extern template class MultiValueMappingT<
    multivalue::Value<EnumStoreBase::Index> >;
extern template class MultiValueMappingT<multivalue::WeightedValue<int8_t> >;
extern template class MultiValueMappingT<multivalue::WeightedValue<int16_t> >;
extern template class MultiValueMappingT<multivalue::WeightedValue<int32_t> >;
extern template class MultiValueMappingT<multivalue::WeightedValue<int64_t> >;
extern template class MultiValueMappingT<multivalue::WeightedValue<float> >;
extern template class MultiValueMappingT<multivalue::WeightedValue<double> >;
extern template class MultiValueMappingT<
    multivalue::WeightedValue<EnumStoreBase::Index> >;
extern template class MultiValueMappingT<multivalue::Value<int8_t>,
                                         multivalue::Index64>;
extern template class MultiValueMappingT<multivalue::Value<int16_t>,
                                         multivalue::Index64>;
extern template class MultiValueMappingT<multivalue::Value<int32_t>,
                                         multivalue::Index64>;
extern template class MultiValueMappingT<multivalue::Value<int64_t>,
                                         multivalue::Index64>;
extern template class MultiValueMappingT<multivalue::Value<float>,
                                         multivalue::Index64>;
extern template class MultiValueMappingT<multivalue::Value<double>,
                                         multivalue::Index64>;
extern template class MultiValueMappingT<
    multivalue::Value<EnumStoreBase::Index>,
    multivalue::Index64>;
extern template class MultiValueMappingT<multivalue::WeightedValue<int8_t>,
                                         multivalue::Index64>;
extern template class MultiValueMappingT<multivalue::WeightedValue<int16_t>,
                                         multivalue::Index64>;
extern template class MultiValueMappingT<multivalue::WeightedValue<int32_t>,
                                         multivalue::Index64>;
extern template class MultiValueMappingT<multivalue::WeightedValue<int64_t>,
                                         multivalue::Index64>;
extern template class MultiValueMappingT<multivalue::WeightedValue<float>,
                                         multivalue::Index64>;
extern template class MultiValueMappingT<multivalue::WeightedValue<double>,
                                         multivalue::Index64>;
extern template class MultiValueMappingT<
    multivalue::WeightedValue<EnumStoreBase::Index>,
    multivalue::Index64>;

} // namespace search

