// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * \class storage::PriorityMemoryLogic
 *
 * \brief Priority logic deciding who should get memory and how much.
 *
 */

#pragma once

#include <vespa/storageframework/defaultimplementation/memory/simplememorylogic.h>
#include <vespa/vespalib/util/sync.h>

namespace storage {
namespace framework {
namespace defaultimplementation {

struct PriorityMemoryLogic : public SimpleMemoryLogic
{
    PriorityMemoryLogic(Clock&, uint64_t maxMemory);

    virtual float getNonCacheThreshold(uint8_t priority) const;

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;
};

} // defaultimplementation
} // framework
} // storage

