// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "prioritymemorylogic.h"

#include <vespa/log/log.h>
LOG_SETUP(".memory.logic.priority");

namespace storage {
namespace framework {
namespace defaultimplementation {

PriorityMemoryLogic::PriorityMemoryLogic(Clock& c, uint64_t maxMem)
    : SimpleMemoryLogic(c, maxMem)
{
    LOG(debug, "Setup priority memory logic with max memory of %" PRIu64 " bytes", maxMem);
}

float
PriorityMemoryLogic::getNonCacheThreshold(uint8_t priority) const
{
    return 0.6 + ((255 - priority) / 255.0) * 0.4;
}

void
PriorityMemoryLogic::print(std::ostream& out, bool verbose,
                         const std::string& indent) const
{
    out << "PriorityMemoryLogic() : ";
    SimpleMemoryLogic::print(out, verbose, indent);
}

} // defaultimplementation
} // framework
} // storage
