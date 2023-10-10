// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "operationstarter.h"
#include <vespa/storage/distributor/operations/operation.h>

namespace storage::distributor {

class DistributorStripeOperationContext;
class OperationSequencer;

class BlockingOperationStarter : public OperationStarter
{
public:
    BlockingOperationStarter(DistributorStripeOperationContext& ctx,
                             OperationSequencer& operation_sequencer,
                             OperationStarter& starterImpl)
        : _operation_context(ctx),
          _operation_sequencer(operation_sequencer),
          _starterImpl(starterImpl)
    {}
    BlockingOperationStarter(const BlockingOperationStarter&) = delete;
    BlockingOperationStarter& operator=(const BlockingOperationStarter&) = delete;

    bool start(const std::shared_ptr<Operation>& operation, Priority priority) override;
private:
    DistributorStripeOperationContext& _operation_context;
    OperationSequencer&                _operation_sequencer;
    OperationStarter&                  _starterImpl;
};

}
