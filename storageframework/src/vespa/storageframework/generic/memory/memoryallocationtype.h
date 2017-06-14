// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::MemoryAllocationType
 * \ingroup memory
 *
 * \brief Allocation types used to differ between memory manager clients.
 *
 * The different memory manager clients have different properties. It is
 * important for the memory manager to distinguish between different users in
 * order to know how to prioritize memory, and also in order to create good
 * reports on memory usage.
 *
 * An allocation type holds metadata for a memory manager client, including a
 * name for the type and various properties that may affect how much memory
 * such a client will get, whether it always gets some, etc.
 */

#pragma once

#include <string>
#include <memory>

namespace storage::framework {

struct MemoryAllocationType {
    using UP = std::unique_ptr<MemoryAllocationType>;

    enum Flags {
        NONE            = 0x00,
        FORCE_ALLOCATE  = 0x01,
        EXTERNAL_LOAD   = 0x02,
        CACHE           = 0x04
    };

    MemoryAllocationType()
        : _flags(NONE), _name("") {};

    MemoryAllocationType(const std::string& name, uint32_t flags = NONE)
        : _flags(flags), _name(name) {}

    const std::string& getName() const { return _name; }
    bool isAllocationsForced() const { return (_flags & FORCE_ALLOCATE); }
    bool isExternalLoad() const { return (_flags & EXTERNAL_LOAD); }
    bool isCache() const { return (_flags & CACHE); }

private:
    uint32_t _flags;
    std::string _name;
};

}
