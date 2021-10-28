// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once
#include <vespa/vespalib/util/arrayref.h>
#include "doc_vector_access.h"
#include "nns-l2.h"
#include <memory>

struct SqDist {
    double distance;
    explicit SqDist(double d) noexcept : distance(d) {}
};

struct NnsHit {
    uint32_t docid;
    SqDist sq;
    NnsHit(uint32_t di, SqDist sqD) noexcept
        : docid(di), sq(sqD) {}
};
struct NnsHitComparatorLessDistance {
    bool operator() (const NnsHit &lhs, const NnsHit& rhs) const {
        if (lhs.sq.distance > rhs.sq.distance) return false;
        if (lhs.sq.distance < rhs.sq.distance) return true;
        return (lhs.docid > rhs.docid);
    }
};
struct NnsHitComparatorGreaterDistance {
    bool operator() (const NnsHit &lhs, const NnsHit& rhs) const {
        if (lhs.sq.distance < rhs.sq.distance) return false;
        if (lhs.sq.distance > rhs.sq.distance) return true;
        return (lhs.docid > rhs.docid);
    }
};
struct NnsHitComparatorLessDocid {
    bool operator() (const NnsHit &lhs, const NnsHit& rhs) const {
        return (lhs.docid < rhs.docid);
    }
};

class BitVector {
private:
    std::vector<uint64_t> _bits;
public:
    BitVector(size_t sz) : _bits((sz+63)/64) {}
    BitVector& setBit(size_t idx) {
        uint64_t mask = 1;
        mask <<= (idx%64);
        _bits[idx/64] |= mask;
        return *this;
    }
    bool isSet(size_t idx) const {
        uint64_t mask = 1;
        mask <<= (idx%64);
        uint64_t word = _bits[idx/64];
        return (word & mask) != 0;
    }
    BitVector& clearBit(size_t idx) {
        uint64_t mask = 1;
        mask <<= (idx%64);
        _bits[idx/64] &= ~mask;
        return *this;
    }
};

template <typename FltType = float>
class NNS
{
public:
    NNS(uint32_t numDims, const DocVectorAccess<FltType> &dva)
      : _numDims(numDims), _dva(dva)
    {}

    virtual void addDoc(uint32_t docid) = 0;
    virtual void removeDoc(uint32_t docid) = 0;

    using Vector = vespalib::ConstArrayRef<FltType>;
    virtual std::vector<NnsHit> topK(uint32_t k, Vector vector, uint32_t search_k) = 0;
    virtual std::vector<NnsHit> topKfilter(uint32_t k, Vector vector, uint32_t search_k, const BitVector &skipDocIds) = 0;
    virtual ~NNS() {}
protected:
    uint32_t _numDims;
    const DocVectorAccess<FltType> &_dva;
};

extern
std::unique_ptr<NNS<float>>
make_annoy_nns(uint32_t numDims, const DocVectorAccess<float> &dva);

extern
std::unique_ptr<NNS<float>>
make_rplsh_nns(uint32_t numDims, const DocVectorAccess<float> &dva);

extern
std::unique_ptr<NNS<float>>
make_hnsw_nns(uint32_t numDims, const DocVectorAccess<float> &dva);

extern
std::unique_ptr<NNS<float>>
make_hnsw_wrap(uint32_t numDims, const DocVectorAccess<float> &dva);
