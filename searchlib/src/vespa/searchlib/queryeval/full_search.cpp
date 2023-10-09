// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "full_search.h"

namespace search::queryeval {

void
FullSearch::doSeek(uint32_t docid)
{
    setDocId(docid);
}

void
FullSearch::doUnpack(uint32_t)
{
}

void
FullSearch::or_hits_into(BitVector &result, uint32_t begin_id)
{
    result.setInterval(begin_id, getEndId());
}

void
FullSearch::and_hits_into(BitVector &, uint32_t)
{
    // nop
}

BitVector::UP
FullSearch::get_hits(uint32_t begin_id)
{
    auto result = BitVector::create(begin_id, getEndId());
    result->setInterval(begin_id, getEndId());
    return result;
}

FullSearch::FullSearch() : SearchIterator()
{
}

FullSearch::~FullSearch() = default;

} // namespace
