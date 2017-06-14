// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threadpool.h"

namespace storage {
namespace framework {

ThreadProperties::ThreadProperties(uint64_t waitTimeMs,
                                   uint64_t maxProcessTimeMs,
                                   int ticksBeforeWait)
{
    _waitTimeMs.store(waitTimeMs);
    _maxProcessTimeMs.store(maxProcessTimeMs);
    _ticksBeforeWait.store(ticksBeforeWait);
}

uint64_t ThreadProperties::getMaxProcessTime() const {
    return _maxProcessTimeMs.load(std::memory_order_relaxed);
}

uint64_t ThreadProperties::getWaitTime() const {
    return _waitTimeMs.load(std::memory_order_relaxed);
}

int ThreadProperties::getTicksBeforeWait() const {
    return _ticksBeforeWait.load(std::memory_order_relaxed);
}

void ThreadProperties::setMaxProcessTime(uint64_t maxProcessingTimeMs) {
    _maxProcessTimeMs.store(maxProcessingTimeMs);
}

void ThreadProperties::setWaitTime(uint64_t waitTimeMs) {
    _waitTimeMs.store(waitTimeMs);
}

void ThreadProperties::setTicksBeforeWait(int ticksBeforeWait) {
    _ticksBeforeWait.store(ticksBeforeWait);
}

} // framework
} // storage
