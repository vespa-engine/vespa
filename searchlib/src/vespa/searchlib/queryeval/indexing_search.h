// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include <vespa/searchlib/common/bitvector.h>

namespace search::queryeval {

/**
 * Search iterator that hits all documents.
 * get_element_ids() and and_element_ids_into() use the supplied element_id
 * Note that it does not search any field, and
 * does not unpack any ranking information.
 * TODO Match only attribute vectors that actually contain an element at element_id?
 **/
class IndexingSearch : public SearchIterator
{
private:
    uint32_t _element_id;

    Trinary is_strict() const override { return Trinary::True; }
    void doSeek(uint32_t) override;
    void doUnpack(uint32_t) override;
    void or_hits_into(BitVector &result, uint32_t begin_id) override;
    void and_hits_into(BitVector &result, uint32_t begin_id) override;
    BitVector::UP get_hits(uint32_t begin_id) override;
    Trinary matches_any() const override { return Trinary::True; }

public:
    IndexingSearch(uint32_t element_id);
    ~IndexingSearch() override;
    void get_element_ids(uint32_t docid, std::vector<uint32_t>& element_ids) override;
    void and_element_ids_into(uint32_t docid, std::vector<uint32_t>& element_ids) override;
};

}
