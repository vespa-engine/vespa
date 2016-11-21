// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/generationholder.h>
#include <vespa/searchlib/util/memoryusage.h>
#include <vespa/searchcommon/common/growstrategy.h>
#include <vespa/vespalib/util/array.h>

namespace search {
namespace attribute {

template <typename T>
class RcuVectorHeld : public vespalib::GenerationHeldBase
{
    std::unique_ptr<T> _data;

public:
    RcuVectorHeld(size_t size, std::unique_ptr<T> data)
        : vespalib::GenerationHeldBase(size),
          _data(std::move(data))
    {
    }

    virtual
    ~RcuVectorHeld(void)
    {
    }
};


/**
 * Vector class for elements of type T using the read-copy-update
 * mechanism to ensure that reader threads will have a consistent view
 * of the vector while the update thread is inserting new elements.
 * The update thread is also responsible for updating the current
 * generation of the vector, and initiating removing of old underlying
 * data vectors.
 **/
template <typename T>
class RcuVectorBase
{
    static_assert(std::is_trivially_destructible<T>::value,
                  "Value type must be trivially destructible");

protected:
    typedef vespalib::Array<T> Array;
    typedef vespalib::GenerationHandler::generation_t generation_t;
    typedef vespalib::GenerationHolder GenerationHolder;
    Array              _data;
    size_t             _growPercent;
    size_t             _growDelta;
    GenerationHolder   &_genHolder;

    size_t
    calcSize(size_t baseSize) const
    {
        size_t delta = (baseSize * _growPercent / 100) + _growDelta;
        return baseSize + std::max(delta, static_cast<size_t>(1));
    }
    size_t
    calcSize() const
    {
        return calcSize(_data.capacity());
    }
    void expand(size_t newCapacity);
    void expandAndInsert(const T & v);

public:
    using ValueType = T;
    RcuVectorBase(GenerationHolder &genHolder);

    /**
     * Construct a new vector with the given initial capacity and grow
     * parameters.
     *
     * New capacity is calculated based on old capacity and grow parameters:
     * nc = oc + (oc * growPercent / 100) + growDelta.
     **/
    RcuVectorBase(size_t initialCapacity, size_t growPercent, size_t growDelta,
                  GenerationHolder &genHolder);

    RcuVectorBase(GrowStrategy growStrategy, GenerationHolder &genHolder)
            : RcuVectorBase(growStrategy.getDocsInitialCapacity(),
                            growStrategy.getDocsGrowPercent(),
                            growStrategy.getDocsGrowDelta(),
                            genHolder) {}

    /**
     * Return whether all capacity has been used.  If true the next
     * call to push_back() will cause an expand of the underlying
     * data.
     **/
    bool isFull() const { return _data.size() == _data.capacity(); }

    /**
     * Return the combined memory usage for this instance.
     **/
    MemoryUsage getMemoryUsage() const;

    // vector interface
    // no swap method, use reset() to forget old capacity and holds
    // NOTE: Unsafe resize/reserve may invalidate data references held by readers!
    void unsafe_resize(size_t n) { _data.resize(n); }
    void unsafe_reserve(size_t n) { _data.reserve(n); }
    void ensure_size(size_t n, T fill = T()) {
        if (n > capacity()) {
            expand(calcSize(n));
        }
        while (size() < n) {
            _data.push_back(fill);
        }
    }
    void push_back(const T & v) {
        if (_data.size() < _data.capacity()) {
            _data.push_back(v);
        } else {
            expandAndInsert(v);
        }
    }

    bool
    empty(void) const
    {
        return _data.empty();
    }

    size_t size() const { return _data.size(); }
    size_t capacity() const { return _data.capacity(); }
    void clear() { _data.clear(); }
    T & operator[](size_t i) { return _data[i]; }
    const T & operator[](size_t i) const { return _data[i]; }

    void
    reset(void)
    {
        // Assumes no readers at this moment
        Array().swap(_data);
        _data.reserve(16);
    }

    void
    shrink(size_t newSize) __attribute__((noinline));
};

template <typename T>
void
RcuVectorBase<T>::expand(size_t newCapacity) {
    std::unique_ptr<Array> tmpData(new Array());
    tmpData->reserve(newCapacity);
    tmpData->resize(_data.size());
    memcpy(tmpData->begin(), _data.begin(), _data.size() * sizeof(T));
    tmpData->swap(_data); // atomic switch of underlying data
    size_t holdSize = tmpData->size() * sizeof(T);
    vespalib::GenerationHeldBase::UP hold(new RcuVectorHeld<Array>(holdSize, std::move(tmpData)));
    _genHolder.hold(std::move(hold));
}

template <typename T>
void
RcuVectorBase<T>::expandAndInsert(const T & v)
{
    expand(calcSize());
    assert(_data.size() < _data.capacity());
    _data.push_back(v);
}


template <typename T>
void
RcuVectorBase<T>::shrink(size_t newSize)
{
    // TODO: Extend Array class to support more optimial shrink when
    // backing store is memory mapped.
    assert(newSize <= _data.size());
    std::unique_ptr<Array> tmpData(new Array());
    tmpData->reserve(newSize);
    tmpData->resize(newSize);
    for (uint32_t i = 0; i < newSize; ++i) {
        (*tmpData)[i] = _data[i];
    }
    // Users of RCU vector must ensure that no readers use old size
    // after swap.  Attribute vectors uses _committedDocIdLimit for this.
    tmpData->swap(_data); // atomic switch of underlying data
    // Use capacity() instead of size() ?
    size_t holdSize = tmpData->size() * sizeof(T);
    vespalib::GenerationHeldBase::UP hold(new RcuVectorHeld<Array>(holdSize, std::move(tmpData)));
    _genHolder.hold(std::move(hold));
}


template <typename T>
RcuVectorBase<T>::RcuVectorBase(GenerationHolder &genHolder)
    : _data(),
      _growPercent(100),
      _growDelta(0),
      _genHolder(genHolder)
{
    _data.reserve(16);
}

template <typename T>
RcuVectorBase<T>::RcuVectorBase(size_t initialCapacity,
                                size_t growPercent,
                                size_t growDelta,
                                GenerationHolder &genHolder)
    : _data(),
      _growPercent(growPercent),
      _growDelta(growDelta),
      _genHolder(genHolder)
{
    _data.reserve(initialCapacity);
}

template <typename T>
MemoryUsage
RcuVectorBase<T>::getMemoryUsage() const
{
    MemoryUsage retval;
    retval.incAllocatedBytes(_data.capacity() * sizeof(T));
    retval.incUsedBytes(_data.size() * sizeof(T));
    return retval;
}


template <typename T>
class RcuVector : public RcuVectorBase<T>
{
private:
    typedef typename RcuVectorBase<T>::generation_t generation_t;
    typedef typename RcuVectorBase<T>::GenerationHolder GenerationHolder;
    using RcuVectorBase<T>::_data;
    generation_t       _generation;
    GenerationHolder   _genHolderStore;

    void
    expandAndInsert(const T & v)
    {
        RcuVectorBase<T>::expandAndInsert(v);
        _genHolderStore.transferHoldLists(_generation);
    }

public:
    RcuVector()
        : RcuVectorBase<T>(_genHolderStore),
          _generation(0),
          _genHolderStore()
    {
    }

    /**
     * Construct a new vector with the given initial capacity and grow
     * parameters.
     *
     * New capacity is calculated based on old capacity and grow parameters:
     * nc = oc + (oc * growPercent / 100) + growDelta.
     **/
    RcuVector(size_t initialCapacity, size_t growPercent, size_t growDelta)
        : RcuVectorBase<T>(initialCapacity, growPercent, growDelta,
                           _genHolderStore),
          _generation(0),
          _genHolderStore()
    {
    }

    RcuVector(GrowStrategy growStrategy)
            : RcuVectorBase<T>(growStrategy, _genHolderStore), _generation(0), _genHolderStore()
    {
    }

    ~RcuVector()
    {
        _genHolderStore.clearHoldLists();
    }

    generation_t
    getGeneration() const
    {
        return _generation;
    }

    void
    setGeneration(generation_t generation)
    {
        _generation = generation;
    }

    /**
     * Remove all old data vectors where generation < firstUsed.
     **/
    void
    removeOldGenerations(generation_t firstUsed)
    {
        _genHolderStore.trimHoldLists(firstUsed);
    }

    void
    push_back(const T & v)
    {
        if (_data.size() < _data.capacity()) {
            _data.push_back(v);
        } else {
            expandAndInsert(v);
        }
    }

    MemoryUsage
    getMemoryUsage() const
    {
        MemoryUsage retval(RcuVectorBase<T>::getMemoryUsage());
        retval.mergeGenerationHeldBytes(_genHolderStore.getHeldBytes());
        return retval;
    }
};


}
}

