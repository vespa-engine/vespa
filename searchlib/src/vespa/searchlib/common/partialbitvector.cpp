// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#include <vespa/fastos/fastos.h>
#include "partialbitvector.h"

namespace search {

using vespalib::alloc::Alloc;

PartialBitVector::PartialBitVector(Index start, Index end) :
    BitVector(),
    _alloc(Alloc::alloc(numActiveBytes(start, end), 0x1000000, 0x1000))
{
    init(_alloc.get(), start, end);
    clear();
}

PartialBitVector::PartialBitVector(const BitVector & org, Index start, Index end) :
        BitVector(),
        _alloc(Alloc::alloc(numActiveBytes(start, end), 0x1000000, 0x1000))
{
    init(_alloc.get(), start, end);
    memcpy(_alloc.get(), org.getWordIndex(start), _alloc.size());
}

PartialBitVector::~PartialBitVector()
{
}

} // namespace search
