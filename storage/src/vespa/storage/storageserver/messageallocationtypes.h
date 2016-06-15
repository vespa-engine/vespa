// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::MessageAllocationTypes
 *
 * \brief Memory allocation types for messages in storage.
 */
#pragma once

#include <sstream>
#include <vespa/storageframework/generic/memory/memorymanagerinterface.h>
#include <vector>
#include <vespa/vespalib/util/exceptions.h>

namespace storage {

class MessageAllocationTypes {
    std::vector<const framework::MemoryAllocationType*> _types;

public:
    MessageAllocationTypes(framework::MemoryManagerInterface& manager);

    const framework::MemoryAllocationType& getType(uint32_t type) const {
        if (_types.size() > size_t(type) && _types[type] != 0) {
            return *_types[type];
        }
        std::ostringstream ost;
        ost << "No type registered with value " << type << ".";
        throw vespalib::IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }
};

}

