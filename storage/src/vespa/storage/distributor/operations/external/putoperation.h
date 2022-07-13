// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storage/distributor/operations/sequenced_operation.h>
#include <vespa/storage/distributor/persistencemessagetracker.h>

namespace document {
    class Document;
}
namespace storage::lib {
    class Distribution;
}
namespace storage::api {
    class CreateBucketReply;
    class PutCommand;
}
namespace storage::distributor {

class DistributorBucketSpace;
class OperationTargetList;

class PutOperation : public SequencedOperation
{
public:
    PutOperation(const DistributorNodeContext& node_ctx,
                 DistributorStripeOperationContext& op_ctx,
                 DistributorBucketSpace &bucketSpace,
                 std::shared_ptr<api::PutCommand> msg,
                 PersistenceOperationMetricSet& metric,
                 SequencingHandle sequencingHandle = SequencingHandle());
    ~PutOperation() override;

    void onStart(DistributorStripeMessageSender& sender) override;
    const char* getName() const override { return "put"; };
    std::string getStatus() const override { return ""; };
    void onReceive(DistributorStripeMessageSender& sender, const std::shared_ptr<api::StorageReply> &) override;
    void onClose(DistributorStripeMessageSender& sender) override;

private:
    PersistenceMessageTrackerImpl _trackerInstance;
    PersistenceMessageTracker& _tracker;

    void insertDatabaseEntryAndScheduleCreateBucket(const OperationTargetList& copies, bool setOneActive,
                                                    const api::StorageCommand& originalCommand,
                                                    std::vector<MessageTracker::ToSend>& messagesToSend);

    void sendPutToBucketOnNode(document::BucketSpace bucketSpace, const document::BucketId& bucketId,
                               const uint16_t node, std::vector<PersistenceMessageTracker::ToSend>& putBatch);

    bool shouldImplicitlyActivateReplica(const OperationTargetList& targets) const;

    bool has_unavailable_targets_in_pending_state(const OperationTargetList& targets) const;

    std::shared_ptr<api::PutCommand> _msg;
    DistributorStripeOperationContext& _op_ctx;
    DistributorBucketSpace &_bucketSpace;
};

}
