// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "operationstarter.h"
#include <vespa/storage/distributor/operations/operation.h>

namespace storage::distributor {

class PendingMessageTracker;
class OperationSequencer;

class BlockingOperationStarter : public OperationStarter
{
public:
    BlockingOperationStarter(PendingMessageTracker& messageTracker,
                             OperationSequencer& operation_sequencer,
                             OperationStarter& starterImpl)
        : _messageTracker(messageTracker),
          _operation_sequencer(operation_sequencer),
          _starterImpl(starterImpl)
    {}
    BlockingOperationStarter(const BlockingOperationStarter&) = delete;
    BlockingOperationStarter& operator=(const BlockingOperationStarter&) = delete;

    bool start(const std::shared_ptr<Operation>& operation, Priority priority) override;
private:
    PendingMessageTracker& _messageTracker;
    OperationSequencer&    _operation_sequencer;
    OperationStarter&      _starterImpl;
};

}
