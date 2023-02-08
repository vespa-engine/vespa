// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storagenodecontext.h"
#include <vespa/storageframework/generic/clock/clock.h>

namespace storage {

StorageNodeContext::StorageNodeContext(std::unique_ptr<ComponentRegister> compReg, std::unique_ptr<framework::Clock> clock)
    : _componentRegister(std::move(compReg)),
      _clock(std::move(clock)),
      _threadPool(*_clock)
{
    _componentRegister->setClock(*_clock);
    _componentRegister->setThreadPool(_threadPool);
}

StorageNodeContext::~StorageNodeContext() = default;

} // storage
