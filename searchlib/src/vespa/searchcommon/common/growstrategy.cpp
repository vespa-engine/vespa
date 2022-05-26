// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "growstrategy.h"
#include <iostream>

namespace search {

std::ostream&
operator<<(std::ostream& os, const GrowStrategy& grow_strategy)
{
    os << "{docsInitialCapacity=" << grow_strategy.getInitialCapacity() <<
        ", docsMinimumCapacity=" << grow_strategy.getMinimumCapacity() <<
        ", docsGrowFactor=" << grow_strategy.getGrowFactor() <<
        ", docsGrowDelta=" << grow_strategy.getGrowDelta() <<
        ", multiValueAllocGrowFactor=" << grow_strategy.getMultiValueAllocGrowFactor() <<
        "}";
    return os;
}

}
