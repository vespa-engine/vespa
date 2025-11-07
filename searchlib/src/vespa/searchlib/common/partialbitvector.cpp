// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "partialbitvector.h"
#include <cstring>

namespace search {

using vespalib::alloc::Alloc;

PartialBitVector::PartialBitVector(Index start, Index end)
    : BitVector(),
      _alloc(allocatePaddedAndAligned(start, end))
{
    init(_alloc.get(), start, end);
    clear();
}

PartialBitVector::PartialBitVector(const BitVector & org, Index start, Index end)
    : BitVector(),
      _alloc(allocatePaddedAndAligned(start, end))
{
    init(_alloc.get(), start, end);
    initialize_from(org);
    set_bit_no_range_check(size());
}

PartialBitVector::~PartialBitVector() = default;

size_t
PartialBitVector::get_allocated_bytes(bool include_self) const noexcept
{
    size_t result = _alloc.size();
    if (include_self) {
        result += sizeof(PartialBitVector);
    }
    return result;
}

} // namespace search
