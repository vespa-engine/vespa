// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multisearch.h"
#include "unpackinfo.h"

namespace search::queryeval {

/**
 * A simple implementation of the Or search operation.
 **/
class OrSearch : public MultiSearch
{
public:
    using Children = MultiSearch::Children;

    static SearchIterator::UP create(ChildrenIterators children, bool strict);
    static SearchIterator::UP create(ChildrenIterators children, bool strict, const UnpackInfo & unpackInfo);

    std::unique_ptr<BitVector> get_hits(uint32_t begin_id) override;
    void or_hits_into(BitVector &result, uint32_t begin_id) override;
    void and_hits_into(BitVector &result, uint32_t begin_id) override;

protected:
    OrSearch(Children children) : MultiSearch(std::move(children)) { }
private:

    bool isOr() const override { return true; }
};

}
