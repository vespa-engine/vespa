// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "alloc.h"
#include "array.h"
#include "arrayref.h"
#include "generationholder.h"
#include "growstrategy.h"
#include "memoryusage.h"

namespace vespalib {

template <typename T>
class RcuVectorHeld : public GenerationHeldBase
{
    T _data;

public:
    RcuVectorHeld(size_t size, T&& data);

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
    std::atomic<const T*> _vector_start;
    GrowStrategy          _growStrategy;
    GenerationHolderType &_genHolder;

    size_t calcNewSize(size_t baseSize) const;
    size_t calcNewSize() const;
    void expand(size_t newCapacity);
    void expandAndInsert(const T & v);
    void update_vector_start();
protected:
    virtual void onReallocation();

public:
    using ValueType = T;

    /**
     * Construct a new vector with the given initial capacity and grow
     * parameters.
     *
     * New capacity is calculated based on old capacity and grow parameters:
     * nc = oc + (oc * growPercent / 100) + growDelta.
     **/
    RcuVectorBase(GrowStrategy growStrategy,
                  GenerationHolderType &genHolder,
                  const Alloc &initialAlloc = Alloc::alloc());

    virtual ~RcuVectorBase();

    /**
     * Return whether all capacity has been used.  If true the next
     * call to push_back() will cause an expand of the underlying
     * data.
     * isFull() should be called from writer only.
     * Const type qualifier removed to prevent call from readers.
     **/
    bool isFull() { return _data.size() == _data.capacity(); }

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
    /*
     * size() should be called from writer only.
     * Const type qualifier removed to prevent call from readers.
     */
    size_t size()  { return _data.size(); }
    /*
     * get_size() should be called from writer only or with proper lock held.
     * Used in predicate attribute by reader (causing data race).
     */
    size_t get_size() const { return _data.size(); }
    /*
     * capacity() should be called from writer only.
     * Const type qualifier removed to prevent call from readers.
     */
    size_t capacity() { return _data.capacity(); }
    void clear() { _data.clear(); }
    /*
     * operator[]() should be called from writer only.
     * Overload with const type qualifier removed to prevent call from readers.
     */
    T & operator[](size_t i) { return _data[i]; }
    /*
     * Readers holding a generation guard can call acquire_elem_ref(i)
     * to get a const reference to element i. Array bound must be handled
     * by reader, cf. committed docid limit in attribute vectors.
     */
    const T& acquire_elem_ref(size_t i) const noexcept { return *(_vector_start.load(std::memory_order_acquire) + i); }

    const T& get_elem_ref(size_t i) const noexcept { return _data[i]; } // Called from writer only

    /*
     * Readers holding a generation guard can call make_read_view() to
     * get a read view to the rcu vector. Array bound (read_size) must
     * be specified by reader, cf. committed docid limit in attribute vectors.
     */
    ConstArrayRef<T> make_read_view(size_t read_size) const noexcept {
        return ConstArrayRef<T>(&acquire_elem_ref(0), read_size);
    }

    void reset();
    void shrink(size_t newSize) __attribute__((noinline));
    void replaceVector(ArrayType replacement);
    ArrayType create_replacement_vector() const { return _data.create(); }
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
    RcuVector(GrowStrategy growStrategy);
    ~RcuVector();

    generation_t getGeneration() const { return _generation; }
    void setGeneration(generation_t generation) { _generation = generation; }

    /**
     * Remove all old data vectors where generation < firstUsed.
     **/
    void reclaim_memory(generation_t oldest_used_gen);

    MemoryUsage getMemoryUsage() const override;
};

}
