// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threadpool.h"

namespace storage::framework {

ThreadProperties::ThreadProperties(vespalib::duration waitTimeMs,
                                   vespalib::duration maxProcessTimeMs,
                                   int ticksBeforeWait)
{
    setWaitTime(waitTimeMs);
    setMaxProcessTime(maxProcessTimeMs);
    setTicksBeforeWait(ticksBeforeWait);
}

    vespalib::duration ThreadProperties::getMaxProcessTime() const {
    return _maxProcessTimeMs.load(std::memory_order_relaxed);
}

vespalib::duration ThreadProperties::getWaitTime() const {
    return _waitTimeMs.load(std::memory_order_relaxed);
}

int ThreadProperties::getTicksBeforeWait() const {
    return _ticksBeforeWait.load(std::memory_order_relaxed);
}

void ThreadProperties::setMaxProcessTime(vespalib::duration maxProcessingTimeMs) {
    _maxProcessTimeMs.store(maxProcessingTimeMs);
}

void ThreadProperties::setWaitTime(vespalib::duration waitTimeMs) {
    _waitTimeMs.store(waitTimeMs);
}

void ThreadProperties::setTicksBeforeWait(int ticksBeforeWait) {
    _ticksBeforeWait.store(ticksBeforeWait);
}

}
