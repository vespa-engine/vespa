// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * \class storage::PriorityMemoryLogic
 *
 * \brief Priority logic deciding who should get memory and how much.
 *
 */

#pragma once

#include "simplememorylogic.h"
#include <vespa/vespalib/util/sync.h>

namespace storage {
namespace framework {
namespace defaultimplementation {

struct PriorityMemoryLogic : public SimpleMemoryLogic
{
    PriorityMemoryLogic(Clock&, uint64_t maxMemory);
    float getNonCacheThreshold(uint8_t priority) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

} // defaultimplementation
} // framework
} // storage

