// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/searchlib/common/bitvector.h>

namespace search {

/**
 * search::PartialBitVector is a bitvector that is only represents 1 part
 * of the full space. All operations concerning the whole vector while only
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

    virtual ~PartialBitVector();

private:
    vespalib::alloc::Alloc  _alloc;
};

} // namespace search

