// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threadpool.h"

namespace storage::framework {

ThreadProperties::ThreadProperties(vespalib::duration waitTime,
                                   vespalib::duration maxProcessTime,
                                   int ticksBeforeWait)
{
    setWaitTime(waitTime);
    setMaxProcessTime(maxProcessTime);
    setTicksBeforeWait(ticksBeforeWait);
}

    vespalib::duration ThreadProperties::getMaxProcessTime() const {
    return _maxProcessTime.load(std::memory_order_relaxed);
}

vespalib::duration ThreadProperties::getWaitTime() const {
    return _waitTime.load(std::memory_order_relaxed);
}

int ThreadProperties::getTicksBeforeWait() const {
    return _ticksBeforeWait.load(std::memory_order_relaxed);
}

void ThreadProperties::setMaxProcessTime(vespalib::duration maxProcessingTime) {
    _maxProcessTime.store(maxProcessingTime);
}

void ThreadProperties::setWaitTime(vespalib::duration waitTime) {
    _waitTime.store(waitTime);
}

void ThreadProperties::setTicksBeforeWait(int ticksBeforeWait) {
    _ticksBeforeWait.store(ticksBeforeWait);
}

}
