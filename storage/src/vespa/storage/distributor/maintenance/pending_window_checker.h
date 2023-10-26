// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/operationstarter.h>

namespace storage::distributor {

/**
 * Allows for easily and cheaply checking if an operation with a given internal maintenance
 * priority could possibly be started "downstream" due to there being available capacity
 * in the maintenance pending window.
 */
class PendingWindowChecker {
public:
    virtual ~PendingWindowChecker() = default;
    [[nodiscard]] virtual bool may_allow_operation_with_priority(OperationStarter::Priority) const noexcept = 0;
};

}
