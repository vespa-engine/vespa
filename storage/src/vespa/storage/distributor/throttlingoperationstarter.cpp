// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "throttlingoperationstarter.h"
#include <cassert>

namespace storage::distributor {

ThrottlingOperationStarter::ThrottlingOperation::~ThrottlingOperation()
{
    _operationStarter.signalOperationFinished(*this);
}

ThrottlingOperationStarter::~ThrottlingOperationStarter() = default;

bool
ThrottlingOperationStarter::canStart(uint32_t currentOperationCount, Priority priority) const
{
    uint32_t variablePending(_maxPending - _minPending);
    uint32_t maxPendingForPri(_minPending + variablePending*((255.0 - priority) / 255.0));

    return currentOperationCount < maxPendingForPri;
}

bool
ThrottlingOperationStarter::start(const std::shared_ptr<Operation>& operation, Priority priority)
{
    if (!may_allow_operation_with_priority(priority)) {
        operation->on_throttled();
        return false;
    }
    auto wrappedOp = std::make_shared<ThrottlingOperation>(operation, *this);
    ++_pendingCount;
    return _starterImpl.start(wrappedOp, priority);
}

bool
ThrottlingOperationStarter::may_allow_operation_with_priority(Priority priority) const noexcept
{
    return canStart(_pendingCount, priority);
}

void
ThrottlingOperationStarter::signalOperationFinished(const Operation& op)
{
    (void) op;
    assert(_pendingCount > 0);
    --_pendingCount;
}

}
