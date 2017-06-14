// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::MemoryManagerInterface
 * \ingroup memory
 *
 * \brief Interface with functions clients need in order to use a memory manager
 *
 * This interface exist so clients can use a memory manager without actually
 * depending on the implementation of it.
 */

#pragma once

#include "memoryallocationtype.h"
#include "memorytoken.h"
#include "reducememoryusageinterface.h"
#include <vector>

namespace storage::framework {

struct MemoryManagerInterface
{
    typedef std::unique_ptr<MemoryManagerInterface> UP;

    virtual ~MemoryManagerInterface() {}

    virtual void setMaximumMemoryUsage(uint64_t max) = 0;

    /**
     * Registers the given allocation type by copying it, and returning
     * a reference to the copied object.
     */
    virtual const MemoryAllocationType&
    registerAllocationType(const MemoryAllocationType& type) = 0;

    /** Throws exception if failing to find type. */
    virtual const MemoryAllocationType&
    getAllocationType(const std::string& name) const = 0;

    /** Get an overview of all registration types. */
    virtual std::vector<const MemoryAllocationType*>
    getAllocationTypes() const = 0;

    /**
     * Decide how much to allocate for this request. Should be between min
     * and max, unless it's of a type that can be denied (such as external
     * requests), in which case we can also deny allocation by returning a null
     * token.
     */
    virtual MemoryToken::UP allocate(
            const MemoryAllocationType&,
            uint64_t min,
            uint64_t max,
            uint8_t priority,
            ReduceMemoryUsageInterface* = 0) = 0;

    /**
     * Utility function to see how much memory is available.
     */
    virtual uint64_t getMemorySizeFreeForPriority(uint8_t priority) const = 0;
};

}
