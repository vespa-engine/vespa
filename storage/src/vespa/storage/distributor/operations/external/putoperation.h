// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "check_condition.h"
#include <vespa/storage/distributor/operations/sequenced_operation.h>
#include <vespa/storage/distributor/persistencemessagetracker.h>

namespace document { class Document; }

namespace storage::lib { class Distribution; }

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
                 DistributorBucketSpace& bucketSpace,
                 std::shared_ptr<api::PutCommand> msg,
                 PersistenceOperationMetricSet& metric,
                 PersistenceOperationMetricSet& condition_probe_metrics,
                 SequencingHandle sequencingHandle = SequencingHandle());
    ~PutOperation() override;

    void onStart(DistributorStripeMessageSender& sender) override;
    const char* getName() const noexcept override { return "put"; };
    std::string getStatus() const override { return ""; };
    void onReceive(DistributorStripeMessageSender& sender, const std::shared_ptr<api::StorageReply>&) override;
    void onClose(DistributorStripeMessageSender& sender) override;

private:
    PersistenceMessageTrackerImpl      _tracker_instance;
    PersistenceMessageTracker&         _tracker;
    std::shared_ptr<api::PutCommand>   _msg;
    document::BucketId                 _doc_id_bucket_id;
    const DistributorNodeContext&      _node_ctx;
    DistributorStripeOperationContext& _op_ctx;
    PersistenceOperationMetricSet&     _condition_probe_metrics;
    DistributorBucketSpace&            _bucket_space;
    std::shared_ptr<CheckCondition>    _check_condition;

    void start_direct_put_dispatch(DistributorStripeMessageSender& sender);
    void start_conditional_put(DistributorStripeMessageSender& sender);
    void on_completed_check_condition(CheckCondition::Outcome& outcome,
                                      DistributorStripeMessageSender& sender);
    void insertDatabaseEntryAndScheduleCreateBucket(const OperationTargetList& copies, bool setOneActive,
                                                    const api::StorageCommand& originalCommand,
                                                    std::vector<MessageTracker::ToSend>& messagesToSend);

    void sendPutToBucketOnNode(document::BucketSpace bucketSpace, const document::BucketId& bucketId,
                               uint16_t node, std::vector<PersistenceMessageTracker::ToSend>& putBatch);

    [[nodiscard]] bool shouldImplicitlyActivateReplica(const OperationTargetList& targets) const;

    [[nodiscard]] bool has_unavailable_targets_in_pending_state(const OperationTargetList& targets) const;
    [[nodiscard]] bool at_least_one_storage_node_is_available() const;
    [[nodiscard]] bool has_condition() const noexcept;
};

}
