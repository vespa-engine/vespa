// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generationholder.h"
#include "generation_hold_list.hpp"

namespace vespalib {

template class GenerationHoldList<GenerationHeldBase::UP, true, false>;

template void GenerationHolderParent::reclaim_internal
        <GenerationHolderParent::NoopFunc>(generation_t oldest_used_gen, NoopFunc func);

GenerationHeldBase::~GenerationHeldBase() = default;

GenerationHolder::GenerationHolder()
    : GenerationHolderParent()
{
}

}
