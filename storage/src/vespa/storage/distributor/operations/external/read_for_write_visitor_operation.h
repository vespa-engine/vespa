// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/operations/operation.h>
#include <vespa/storage/distributor/operation_sequencer.h>
#include <memory>

namespace storage::distributor {

class PendingMessageTracker;
class VisitorOperation;
class OperationOwner;
class UuidGenerator;

/**
 * Operation starting indirection for a visitor operation that has the semantics
 * of an exclusive bucket lock. Such operations can only resolve to a single
 * super-bucket/sub-bucket pair and care should be taken to avoid starving client
 * operations through long-running locks.
 *
 * Operation starting may be deferred to the PendingMessageTracker if there are
 * pending operations to the sub-bucket when onStart is called. If so, the deferred
 * operation start takes place automatically and immediately when all pending
 * bucket operations have completed. These will be started in the context of the
 * OperationOwner provided to the operation.
 */
class ReadForWriteVisitorOperationStarter
        : public Operation,
          public std::enable_shared_from_this<ReadForWriteVisitorOperationStarter>
{
    std::shared_ptr<VisitorOperation> _visitor_op;
    OperationSequencer&               _operation_sequencer;
    OperationOwner&                   _stable_operation_owner;
    PendingMessageTracker&            _message_tracker;
    UuidGenerator&                    _uuid_generator;
public:
    ReadForWriteVisitorOperationStarter(std::shared_ptr<VisitorOperation> visitor_op,
                                        OperationSequencer& operation_sequencer,
                                        OperationOwner& stable_operation_owner,
                                        PendingMessageTracker& message_tracker,
                                        UuidGenerator& uuid_generator);
    ~ReadForWriteVisitorOperationStarter() override;

    const char* getName() const override { return "ReadForWriteVisitorOperationStarter"; }
    void onClose(DistributorMessageSender& sender) override;
    void onStart(DistributorMessageSender& sender) override;
    void onReceive(DistributorMessageSender& sender,
                   const std::shared_ptr<api::StorageReply> & msg) override;
private:
    bool bucket_has_pending_merge(const document::Bucket&, const PendingMessageTracker& tracker) const;
};

}
