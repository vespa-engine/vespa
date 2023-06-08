// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "check_condition.h"
#include <vespa/storage/distributor/operations/sequenced_operation.h>
#include <vespa/storage/distributor/persistencemessagetracker.h>

namespace storage::api { class RemoveCommand; }

namespace storage::distributor {

class DistributorBucketSpace;

class RemoveOperation : public SequencedOperation
{
public:
    RemoveOperation(const DistributorNodeContext& node_ctx,
                    DistributorStripeOperationContext& op_ctx,
                    DistributorBucketSpace& bucketSpace,
                    std::shared_ptr<api::RemoveCommand> msg,
                    PersistenceOperationMetricSet& metric,
                    PersistenceOperationMetricSet& condition_probe_metrics,
                    SequencingHandle sequencingHandle = SequencingHandle());
    ~RemoveOperation() override;

    void onStart(DistributorStripeMessageSender& sender) override;
    const char* getName() const noexcept override { return "remove"; };
    std::string getStatus() const override { return ""; };

    void onReceive(DistributorStripeMessageSender& sender, const std::shared_ptr<api::StorageReply> &) override;
    void onClose(DistributorStripeMessageSender& sender) override;

private:
    PersistenceMessageTrackerImpl       _tracker_instance;
    PersistenceMessageTracker&          _tracker;
    std::shared_ptr<api::RemoveCommand> _msg;
    document::BucketId                  _doc_id_bucket_id;
    const DistributorNodeContext&       _node_ctx;
    DistributorStripeOperationContext&  _op_ctx;
    PersistenceOperationMetricSet&      _condition_probe_metrics;
    DistributorBucketSpace&             _bucket_space;
    std::shared_ptr<CheckCondition>     _check_condition;

    void start_direct_remove_dispatch(DistributorStripeMessageSender& sender);
    void start_conditional_remove(DistributorStripeMessageSender& sender);
    void on_completed_check_condition(CheckCondition::Outcome& outcome,
                                      DistributorStripeMessageSender& sender);
    [[nodiscard]] bool has_condition() const noexcept;
};

}
