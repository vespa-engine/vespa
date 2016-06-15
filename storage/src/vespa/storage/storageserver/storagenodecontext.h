// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::StorageNodeContext
 * @ingroup storageserver
 *
 * @brief Data available to both provider implementations and storage server
 *
 * This utility class sets up the default component register implementation.
 * It also sets up the clock and the threadpool, such that the most basic
 * features are available to the provider, before the service layer is set up.
 *
 * The service layer still provides the memory manager functionality though,
 * so you cannot retrieve the memory manager before the service layer has
 * started up. (Before getPartitionStates() have been called on provider)
 */

#pragma once

#include <vespa/storage/frameworkimpl/component/storagecomponentregisterimpl.h>
#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/storageframework/defaultimplementation/memory/memorymanager.h>
#include <vespa/storageframework/defaultimplementation/thread/threadpoolimpl.h>

namespace storage {

struct StorageNodeContext {
    // Typedefs to simplify the remainder of the interface
    typedef StorageComponentRegisterImpl ComponentRegister;
    typedef framework::defaultimplementation::RealClock RealClock;
    typedef framework::defaultimplementation::MemoryManager MemoryManager;

    /**
     * Get the actual component register. Available as the actual type as the
     * storage server need to set implementations, and the components need the
     * actual component register interface.
     */
    ComponentRegister& getComponentRegister() { return *_componentRegister; }

    /**
     * There currently exist threads that doesn't use the component model.
     * Let the backend threadpool be accessible for now.
     */
    FastOS_ThreadPool& getThreadPool() { return _threadPool.getThreadPool(); }

    /**
     * Get the memory manager. Components that wants to print status of memory
     * manager need access to the actual implementation.
     */
    MemoryManager& getMemoryManager() { return _memoryManager; }

    void setMaximumMemoryUsage(uint64_t max);

protected:
        // Initialization has been split in two as subclass needs to initialize
        // component register before sending it on.
    StorageNodeContext(ComponentRegister::UP,
                              framework::Clock::UP);

private:
    ComponentRegister::UP _componentRegister;
    framework::Clock::UP _clock;
    framework::defaultimplementation::ThreadPoolImpl _threadPool;
    framework::defaultimplementation::AllocationLogic* _memoryLogic;
    MemoryManager _memoryManager;

};

} // storage

