// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include <vespa/searchlib/common/bitvector.h>

namespace search::queryeval {

/**
 * Search iterator that hits all documents.
 * Note that it does not search any field, and
 * does not unpack any ranking information.
 **/
class FullSearch : public SearchIterator
{
private:
    Trinary is_strict() const override { return Trinary::True; }
    void doSeek(uint32_t) override;
    void doUnpack(uint32_t) override;
    void or_hits_into(BitVector &result, uint32_t begin_id) override;
    void and_hits_into(BitVector &result, uint32_t begin_id) override;
    BitVector::UP get_hits(uint32_t begin_id) override;
    Trinary matches_any() const override { return Trinary::True; }

public:
    FullSearch();
    ~FullSearch();
};

}
