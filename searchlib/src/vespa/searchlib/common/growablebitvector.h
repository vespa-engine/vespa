// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/allocatedbitvector.h>

namespace search {

class GrowableBitVector : public AllocatedBitVector
{
public:
    GrowableBitVector(Index newSize,
                      Index newCapacity,
                      GenerationHolder &generationHolder);

    void reserve(Index newCapacity);
    void shrink(Index newCapacity);
    void extend(Index newCapacity);
private:
    VESPA_DLL_LOCAL void hold(GenerationHeldBase::UP v);
    GenerationHolder &_generationHolder;
};

} // namespace search

