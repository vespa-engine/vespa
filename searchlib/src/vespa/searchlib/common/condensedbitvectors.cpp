// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "condensedbitvectors.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/rcuvector.h>
#include <cassert>

using vespalib::IllegalArgumentException;
using vespalib::make_string;
using vespalib::GenerationHolder;

namespace search {

namespace {

template <typename T>
class CondensedBitVectorT : public CondensedBitVector
{
public:
    CondensedBitVectorT(size_t sz, GenerationHolder &genHolder) :
        _v(vespalib::GrowStrategy(sz, 30, 1000, 0), genHolder)
    {
        for (size_t i = 0; i < sz; ++i) {
            _v.push_back(0);
        }
    }
private:
    static uint8_t countBits(T v) {
        return ((sizeof(T)) <= 4)
                   ? __builtin_popcount(v)
                   : __builtin_popcountl(v);
    }
    T computeMask(const KeySet & keys) const __attribute__ ((noinline)) {
        T mask(0);
        for (size_t i : keys) {
            assert(i < getKeyCapacity());
            mask |= (B << i);
        }
        return mask;
    }
    static const uint64_t B = 1ul;
    void initializeCountVector(const KeySet & keys, CountVector & cv) const override {
       struct S {
           void operator () (uint8_t & cv, uint8_t v) { cv = v; }
       };
       computeCountVector(computeMask(keys), cv, S());
    }
    void addCountVector(const KeySet & keys, CountVector & cv) const override {
       struct S {
           void operator () (uint8_t & cv, uint8_t v) { cv += v; }
       };
       computeCountVector(computeMask(keys), cv, S());
    }

    void clearIndex(uint32_t index) override {
        _v[index] = 0;
    }

    template <typename F>
    void computeCountVector(T mask, CountVector & cv, F func) const __attribute__((noinline));

    template <typename F>
    void computeTail(T mask, CountVector & cv, F func, size_t i) const __attribute__((noinline));

    void set(Key key, uint32_t index, bool v) override {
        assert(key < getKeyCapacity());
        if (v) {
            _v[index] |= B << key;
        } else {
            _v[index] &= ~(B << key);
        }
    }
    bool get(Key key, uint32_t index) const override {
        assert(key < getKeyCapacity());
        return (_v.acquire_elem_ref(index) & (B << key)) != 0;
    }

    size_t getKeyCapacity() const override { return sizeof(T)*8; }
    /*
     * getCapacity() should be called from writer only.
     * Const type qualifier removed to prevent call from readers.
     */
    size_t getCapacity() override { return _v.capacity(); }
    /*
     * getSize() should be called from writer only.
     * Const type qualifier removed to prevent call from readers.
     */
    size_t getSize() override { return _v.size(); }
    void adjustDocIdLimit(uint32_t docId) override;
    vespalib::RcuVectorBase<T> _v;
};

template <typename T>
template <typename F>
void
CondensedBitVectorT<T>::computeCountVector(T mask, CountVector & cv, F func) const
{
    size_t i(0);
    const size_t UNROLL = 2;
    uint8_t *d = &cv[0];
    const T *v = &_v.acquire_elem_ref(0);
    for (const size_t m(cv.size() - (UNROLL - 1)); i < m; i+=UNROLL) {
        for (size_t j(0); j < UNROLL; j++) {
            func(d[i+j], countBits(v[i+j] & mask));
        }
    }
    computeTail(mask, cv, func, i);
}

template <typename T>
template <typename F>
void
CondensedBitVectorT<T>::computeTail(T mask, CountVector & cv, F func, size_t i) const
{
    auto* v = &_v.acquire_elem_ref(0);
    for (; i < cv.size(); i++) {
        func(cv[i], countBits(v[i] & mask));
    }
}


template <typename T>
void
CondensedBitVectorT<T>::adjustDocIdLimit(uint32_t docId)
{
    _v.reserve(docId+1);
    while (_v.size() <= docId) {
        _v.push_back(0);
    }
}


void throwIllegalKey(size_t numKeys, size_t key) __attribute__((noinline));

void throwIllegalKey(size_t numKeys, size_t key)
{
    throw IllegalArgumentException(make_string("All %ld possible keys are used. Key %ld is not added", numKeys, key), VESPA_STRLOC);
}

}

CondensedBitVector::~CondensedBitVector() = default;

void
CondensedBitVector::addKey(Key key) const
{
    if ( ! hasKey(key)) {
        throwIllegalKey(getKeyCapacity(), key);
    }
}

CondensedBitVector::UP
CondensedBitVector::create(size_t size, GenerationHolder &genHolder)
{
    return std::make_unique<CondensedBitVectorT<uint32_t>>(size, genHolder);
}

}
