// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#include <vespa/fastos/fastos.h>
#include "partialbitvector.h"

/////////////////////////////////
namespace search
{

PartialBitVector::PartialBitVector(Index start, Index end) :
    BitVector(),
    _alloc(numActiveBytes(start, end))
{
    init(_alloc.get(), start, end);
    clear();
}

PartialBitVector::~PartialBitVector()
{
}

} // namespace search
