// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "emptysearch.h"

namespace search::queryeval {

void
EmptySearch::doSeek(uint32_t)
{
}

void
EmptySearch::doUnpack(uint32_t)
{
}

void
EmptySearch::or_hits_into(BitVector &, uint32_t)
{
    // nop
}

void
EmptySearch::and_hits_into(BitVector &result, uint32_t begin_id)
{
    result.clearInterval(begin_id, getEndId());
}

BitVector::UP
EmptySearch::get_hits(uint32_t begin_id)
{
    auto result = BitVector::create(begin_id, getEndId());
    return result;
}

EmptySearch::Trinary
EmptySearch::is_strict() const
{
    return Trinary::True;
}

EmptySearch::EmptySearch()
    : SearchIterator()
{
}

EmptySearch::~EmptySearch()
{
}

} // namespace
