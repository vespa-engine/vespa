// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multisearch.h"
#include "unpackinfo.h"

namespace search {
namespace queryeval {

/**
 * A simple implementation of the And search operation.
 **/
class AndSearch : public MultiSearch
{
public:
    // Caller takes ownership of the returned SearchIterator.
    static AndSearch *create(const Children &children, bool strict, const UnpackInfo & unpackInfo);
    static AndSearch *create(const Children &children, bool strict);

    std::unique_ptr<BitVector> get_hits(uint32_t begin_id) override;
    void or_hits_into(BitVector &result, uint32_t begin_id) override;
    void and_hits_into(BitVector &result, uint32_t begin_id) override;

    AndSearch & estimate(uint32_t est) { _estimate = est; return *this; }
    uint32_t estimate() const { return _estimate; }
protected:
    AndSearch(const Children & children);
    void doUnpack(uint32_t docid) override;
    UP andWith(UP filter, uint32_t estimate) override;
    UP offerFilterToChildren(UP filter, uint32_t estimate);
private:
    bool isAnd() const override { return true; }
    uint32_t  _estimate;
};

} // namespace queryeval
} // namespace search

