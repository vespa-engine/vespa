// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compaction_strategy.h"
#include <iostream>
namespace search {

std::ostream& operator<<(std::ostream& os, const CompactionStrategy& compaction_strategy)
{
    os << "{maxDeadBytesRatio=" << compaction_strategy.getMaxDeadBytesRatio() <<
        ", maxDeadAddressSpaceRatio=" << compaction_strategy.getMaxDeadAddressSpaceRatio() <<
        "}";
    return os;
}

}
