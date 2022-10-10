// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generationholder.h"
#include "generation_hold_list.hpp"

namespace vespalib {

GenerationHeldBase::~GenerationHeldBase() = default;

GenerationHolder::GenerationHolder()
    : GenerationHoldList<GenerationHeldBase::UP, true, false>()
{
}

}
