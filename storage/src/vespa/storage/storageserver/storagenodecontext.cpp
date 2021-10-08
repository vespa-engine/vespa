// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storagenodecontext.h"

namespace storage {

StorageNodeContext::StorageNodeContext(ComponentRegister::UP compReg, framework::Clock::UP clock)
    : _componentRegister(std::move(compReg)),
      _clock(std::move(clock)),
      _threadPool(*_clock)
{
    _componentRegister->setClock(*_clock);
    _componentRegister->setThreadPool(_threadPool);
}

StorageNodeContext::~StorageNodeContext() = default;

} // storage
