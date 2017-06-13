// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::ReduceMemoryUsageInterface
 * \ingroup memory
 *
 * \brief The manager can take memory back when needed using this interface.
 *
 * Some memory users, typically caches, wants to use all available memory. But
 * to let them use all available memory, it must also be easy to take memory
 * back when needed for something else. An implementation of this interface can
 * be given on memory allocations to give the memory manager the ability to take
 * memory back when needed.
 */

#pragma once

namespace storage::framework {

struct ReduceMemoryUsageInterface
{
    virtual ~ReduceMemoryUsageInterface() {}

    /**
     * This callback is called when the memory manager want to reduce the usage
     * of the given memory token. Actual memory to be released should be
     * released in this function. The token itself will be adjusted by the
     * memory manager though. The memory manager may keep a lock through this
     * call, so no memory manager calls should be made inside this callback.
     *
     * It is recommended that you actually release at least as many bytes as
     * requested. Though currently it is allowed to reduce less or refuse, but
     * this might mean that some higher priority task does not get the memory it
     * needs.
     *
     * @param reduceBy Always in the range 0 < reduceBy <= token.size()
     * @return The amount of memory no longer used.
     */
    virtual uint64_t reduceMemoryConsumption(const MemoryToken&, uint64_t reduceBy) = 0;
};

}
