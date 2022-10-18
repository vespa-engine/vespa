// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include <vespa/searchlib/common/bitvector.h>

namespace search::queryeval {

/** Search iterator that never yields any hits. */
class EmptySearch : public SearchIterator
{
protected:
    void doSeek(uint32_t) override;
    void doUnpack(uint32_t) override;
    void or_hits_into(BitVector &result, uint32_t begin_id) override;
    void and_hits_into(BitVector &result, uint32_t begin_id) override;
    BitVector::UP get_hits(uint32_t begin_id) override;
    void initRange(uint32_t begin, uint32_t end) override {
        SearchIterator::initRange(begin, end);
        setAtEnd();
    }
    virtual Trinary is_strict() const override;
    Trinary matches_any() const override { return Trinary::False; }

public:
    EmptySearch();
    ~EmptySearch();
};

}
