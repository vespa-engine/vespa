// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "growstrategy.h"
#include <iostream>

namespace search {

std::ostream& operator<<(std::ostream& os, const GrowStrategy& grow_strategy)
{
    os << "{docsInitialCapacity=" << grow_strategy.getDocsInitialCapacity() <<
        ", docsGrowFactor=" << grow_strategy.getDocsGrowFactor() <<
        ", docsGrowDelta=" << grow_strategy.getDocsGrowDelta() <<
        ", multiValueAllocGrowFactor=" << grow_strategy.getMultiValueAllocGrowFactor() <<
        "}";
    return os;
}

}
