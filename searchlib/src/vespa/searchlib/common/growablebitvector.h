// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "allocatedbitvector.h"

namespace search {

class GrowableBitVector : public AllocatedBitVector
{
public:
    GrowableBitVector(Index newSize, Index newCapacity, GenerationHolder &generationHolder);

    /** Will return true if a a buffer is held */
    bool reserve(Index newCapacity);
    bool shrink(Index newCapacity);
    bool extend(Index newCapacity);
private:
    VESPA_DLL_LOCAL bool hold(GenerationHeldBase::UP v);
    GenerationHolder &_generationHolder;
};

} // namespace search

