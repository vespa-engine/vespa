// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bitvector.h"

namespace search {

/**
 * search::PartialBitVector is a bitvector that is only represents 1 part
 * of the full space. All operations concerning the whole vector will only
 * be conducted on this smaller area.
 */
class PartialBitVector : public BitVector
{
public:
    /**
     * Class constructor specifying startindex and endindex.
     * Allocated area is zeroed.
     *
     * @param start is the beginning.
     * @end is the end.
     *
     */
    PartialBitVector(Index start, Index end);
    PartialBitVector(const BitVector & org, Index start, Index end);

    ~PartialBitVector() override;

private:
    vespalib::alloc::Alloc  _alloc;
};

} // namespace search

