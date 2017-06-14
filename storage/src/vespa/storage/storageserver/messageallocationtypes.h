// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::MessageAllocationTypes
 *
 * \brief Memory allocation types for messages in storage.
 */
#pragma once

#include <vespa/storageframework/generic/memory/memorymanagerinterface.h>
#include <vector>

namespace storage {

class MessageAllocationTypes {
    std::vector<const framework::MemoryAllocationType*> _types;

public:
    MessageAllocationTypes(framework::MemoryManagerInterface& manager);

    const framework::MemoryAllocationType& getType(uint32_t type) const;
};

}

