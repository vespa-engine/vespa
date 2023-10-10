// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "staticthrottlepolicy.h"
#include "message.h"

namespace mbus {

StaticThrottlePolicy::StaticThrottlePolicy() :
    _maxPendingCount(0),
    _maxPendingSize(0),
    _pendingSize(0)
{ }

uint32_t
StaticThrottlePolicy::getMaxPendingCount() const
{
    return _maxPendingCount;
}

StaticThrottlePolicy &
StaticThrottlePolicy::setMaxPendingCount(uint32_t maxCount)
{
    _maxPendingCount = maxCount;
    return *this;
}

uint64_t
StaticThrottlePolicy::getMaxPendingSize() const
{
    return _maxPendingSize;
}

StaticThrottlePolicy &
StaticThrottlePolicy::setMaxPendingSize(uint64_t maxSize)
{
    _maxPendingSize = maxSize;
    return *this;
}

uint64_t
StaticThrottlePolicy::getPendingSize() const
{
    return _pendingSize;
}

bool
StaticThrottlePolicy::canSend(const Message &msg, uint32_t pendingCount)
{
    if (_maxPendingCount > 0 && pendingCount >= _maxPendingCount) {
        return false;
    }
    if (_maxPendingSize > 0 && _pendingSize >= _maxPendingSize) {
        return false;
    }
    (void)msg;
    return true;
}

void
StaticThrottlePolicy::processMessage(Message &msg)
{
    uint32_t size = msg.getApproxSize();
    msg.setContext(Context((uint64_t)size));
    _pendingSize += size;
}

void
StaticThrottlePolicy::processReply(Reply &reply)
{
    uint32_t size = reply.getContext().value.UINT64;
    _pendingSize -= size;
}

} // namespace mbus
