// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "alloc.h"
#include "array.h"
#include "generationholder.h"
#include "growstrategy.h"
#include "memoryusage.h"

namespace vespalib {

template <typename T>
class RcuVectorHeld : public GenerationHeldBase
{
    std::unique_ptr<T> _data;

public:
    RcuVectorHeld(size_t size, std::unique_ptr<T> data);

    ~RcuVectorHeld();
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
private:
    static_assert(std::is_trivially_destructible<T>::value,
                  "Value type must be trivially destructible");

    using ArrayType = Array<T>;
    using Alloc = alloc::Alloc;
protected:
    using generation_t = GenerationHandler::generation_t;
    using GenerationHolderType = GenerationHolder;
private:
    ArrayType             _data;
    size_t                _growPercent;
    size_t                _growDelta;
    GenerationHolderType &_genHolder;

    size_t calcNewSize(size_t baseSize) const {
        size_t delta = (baseSize * _growPercent / 100) + _growDelta;
        return baseSize + std::max(delta, static_cast<size_t>(1));
    }
    size_t calcNewSize() const {
        return calcNewSize(_data.capacity());
    }
    void expand(size_t newCapacity);
    void expandAndInsert(const T & v);
    virtual void onReallocation();

public:
    using ValueType = T;
    RcuVectorBase(GenerationHolderType &genHolder,
                  const Alloc &initialAlloc = Alloc::alloc());

    /**
     * Construct a new vector with the given initial capacity and grow
     * parameters.
     *
     * New capacity is calculated based on old capacity and grow parameters:
     * nc = oc + (oc * growPercent / 100) + growDelta.
     **/
    RcuVectorBase(size_t initialCapacity, size_t growPercent, size_t growDelta,
                  GenerationHolderType &genHolder,
                  const Alloc &initialAlloc = Alloc::alloc());

    RcuVectorBase(GrowStrategy growStrategy,
                  GenerationHolderType &genHolder,
                  const Alloc &initialAlloc = Alloc::alloc());

    virtual ~RcuVectorBase();

    /**
     * Return whether all capacity has been used.  If true the next
     * call to push_back() will cause an expand of the underlying
     * data.
     **/
    bool isFull() const { return _data.size() == _data.capacity(); }

    /**
     * Return the combined memory usage for this instance.
     **/
    virtual MemoryUsage getMemoryUsage() const;

    // vector interface
    // no swap method, use reset() to forget old capacity and holds
    // NOTE: Unsafe resize/reserve may invalidate data references held by readers!
    void unsafe_resize(size_t n);
    void unsafe_reserve(size_t n);
    void ensure_size(size_t n, T fill = T());
    void reserve(size_t n) {
        if (n > capacity()) {
            expand(calcNewSize(n));
        }
    }
    void push_back(const T & v) {
        if (_data.size() < _data.capacity()) {
            _data.push_back(v);
        } else {
            expandAndInsert(v);
        }
    }

    bool empty() const { return _data.empty(); }
    size_t size() const { return _data.size(); }
    size_t capacity() const { return _data.capacity(); }
    void clear() { _data.clear(); }
    T & operator[](size_t i) { return _data[i]; }
    const T & operator[](size_t i) const { return _data[i]; }

    void reset();
    void shrink(size_t newSize) __attribute__((noinline));
    void replaceVector(std::unique_ptr<ArrayType> replacement);
};

template <typename T>
class RcuVector : public RcuVectorBase<T>
{
private:
    using generation_t         = typename RcuVectorBase<T>::generation_t;
    using GenerationHolderType = typename RcuVectorBase<T>::GenerationHolderType;
    generation_t         _generation;
    GenerationHolderType _genHolderStore;

    void onReallocation() override;

public:
    RcuVector();

    /**
     * Construct a new vector with the given initial capacity and grow
     * parameters.
     *
     * New capacity is calculated based on old capacity and grow parameters:
     * nc = oc + (oc * growPercent / 100) + growDelta.
     **/
    RcuVector(size_t initialCapacity, size_t growPercent, size_t growDelta);
    RcuVector(GrowStrategy growStrategy);
    ~RcuVector();

    generation_t getGeneration() const { return _generation; }
    void setGeneration(generation_t generation) { _generation = generation; }

    /**
     * Remove all old data vectors where generation < firstUsed.
     **/
    void removeOldGenerations(generation_t firstUsed);

    MemoryUsage getMemoryUsage() const override;
};

}
