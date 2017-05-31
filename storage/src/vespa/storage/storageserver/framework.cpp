// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "framework.h"

#include <vespa/storageframework/defaultimplementation/memory/prioritymemorylogic.h>

using storage::framework::defaultimplementation::AllocationLogic;

namespace storage {

Framework::Framework(framework::Clock::UP clock)
    : _componentRegister(),
      _clock(clock),
      _threadPool(*_clock),
      _memoryLogic(new framework::defaultimplementation::PriorityMemoryLogic(
                *_clock, 1024 * 1024 * 1024)),
      _memoryManager(AllocationLogic::UP(_memoryLogic))
{
    framework::defaultimplementation::ComponentRegisterImpl& cri(
            _componentRegister.getComponentRegisterImpl());
    cri.setClock(*_clock);
    cri.setThreadPool(_threadPool);
    cri.setMemoryManager(_memoryManager);
}

void
Framework::setMaximumMemoryUsage(uint64_t max)
{
    using storage::framework::defaultimplementation::PriorityMemoryLogic;
    static_cast<PriorityMemoryLogic*>(_memoryLogic)->setMaximumMemoryUsage(max);
}

} // storage
