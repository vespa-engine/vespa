// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/operationstarter.h>
#include <vespa/storage/distributor/operations/operation.h>

namespace storage {
namespace distributor {

class PendingMessageTracker;

class BlockingOperationStarter : public OperationStarter
{
public:
    BlockingOperationStarter(PendingMessageTracker& messageTracker,
                             OperationStarter& starterImpl)
        : _messageTracker(messageTracker),
          _starterImpl(starterImpl)
    {}

    virtual bool start(const std::shared_ptr<Operation>& operation,
                       Priority priority);

private:
    BlockingOperationStarter(const BlockingOperationStarter&);
    BlockingOperationStarter& operator=(const BlockingOperationStarter&);

    PendingMessageTracker& _messageTracker;
    OperationStarter& _starterImpl;
};

}
}

