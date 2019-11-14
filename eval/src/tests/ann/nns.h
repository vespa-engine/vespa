// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once
#include <vespa/vespalib/util/arrayref.h>
#include "doc_vector_access.h"
#include "nns-l2.h"
#include <memory>

struct NnsHit {
    uint32_t docid;
    double sqDistance;
    NnsHit(uint32_t di, double sqD)
        : docid(di), sqDistance(sqD) {}
};
struct NnsHitComparatorLessDistance {
    bool operator() (const NnsHit &lhs, const NnsHit& rhs) const {
        if (lhs.sqDistance > rhs.sqDistance) return false;
        if (lhs.sqDistance < rhs.sqDistance) return true;
        return (lhs.docid > rhs.docid);
    }
};
struct NnsHitComparatorGreaterDistance {
    bool operator() (const NnsHit &lhs, const NnsHit& rhs) const {
        if (lhs.sqDistance < rhs.sqDistance) return false;
        if (lhs.sqDistance > rhs.sqDistance) return true;
        return (lhs.docid > rhs.docid);
    }
};
struct NnsHitComparatorLessDocid {
    bool operator() (const NnsHit &lhs, const NnsHit& rhs) const {
        return (lhs.docid < rhs.docid);
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
