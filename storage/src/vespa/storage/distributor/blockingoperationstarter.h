// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "operationstarter.h"
#include <vespa/storage/distributor/operations/operation.h>

namespace storage::distributor {

class PendingMessageTracker;

class BlockingOperationStarter : public OperationStarter
{
public:
    BlockingOperationStarter(PendingMessageTracker& messageTracker,
                             OperationStarter& starterImpl)
        : _messageTracker(messageTracker),
          _starterImpl(starterImpl)
    {}
    BlockingOperationStarter(const BlockingOperationStarter&) = delete;
    BlockingOperationStarter& operator=(const BlockingOperationStarter&) = delete;

    bool start(const std::shared_ptr<Operation>& operation, Priority priority) override;
private:
    PendingMessageTracker& _messageTracker;
    OperationStarter& _starterImpl;
};

}
