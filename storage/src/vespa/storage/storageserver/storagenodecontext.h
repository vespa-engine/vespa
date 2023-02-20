// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::StorageNodeContext
 * @ingroup storageserver
 *
 * @brief Data available to both provider implementations and storage server
 *
 * This utility class sets up the default component register implementation.
 * It also sets up the clock and the threadpool, such that the most basic
 * features are available to the provider, before the service layer is set up.
 */

#pragma once

#include <vespa/storage/frameworkimpl/component/storagecomponentregisterimpl.h>
#include <vespa/storageframework/defaultimplementation/thread/threadpoolimpl.h>

namespace storage {

struct StorageNodeContext {
    // Typedefs to simplify the remainder of the interface
    using ComponentRegister = StorageComponentRegisterImpl;

    /**
     * Get the actual component register. Available as the actual type as the
     * storage server need to set implementations, and the components need the
     * actual component register interface.
     */
    ComponentRegister& getComponentRegister() { return *_componentRegister; }

    ~StorageNodeContext();

protected:
        // Initialization has been split in two as subclass needs to initialize
        // component register before sending it on.
    StorageNodeContext(std::unique_ptr<ComponentRegister>, std::unique_ptr<framework::Clock>);

private:
    std::unique_ptr<ComponentRegister>               _componentRegister;
    std::unique_ptr<framework::Clock>                _clock;
    framework::defaultimplementation::ThreadPoolImpl _threadPool;

};

} // storage

