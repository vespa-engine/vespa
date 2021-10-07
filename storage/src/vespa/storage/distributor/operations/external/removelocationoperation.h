// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/operations/operation.h>
#include <vespa/storage/distributor/persistencemessagetracker.h>

namespace storage {

namespace api { class RemoveLocationCommand; }

namespace distributor {

class DistributorBucketSpace;

class RemoveLocationOperation  : public Operation
{
public:
    RemoveLocationOperation(const DistributorNodeContext& node_ctx,
                            DistributorStripeOperationContext& op_ctx,
                            const DocumentSelectionParser& parser,
                            DistributorBucketSpace &bucketSpace,
                            std::shared_ptr<api::RemoveLocationCommand> msg,
                            PersistenceOperationMetricSet& metric);
    ~RemoveLocationOperation() override;


    static int getBucketId(const DistributorNodeContext& node_ctx,
                           const DocumentSelectionParser& parser,
                           const api::RemoveLocationCommand& cmd,
                           document::BucketId& id);
    void onStart(DistributorStripeMessageSender& sender) override;
    const char* getName() const override { return "removelocation"; };
    std::string getStatus() const override { return ""; };
    void onReceive(DistributorStripeMessageSender& sender, const std::shared_ptr<api::StorageReply> &) override;
    void onClose(DistributorStripeMessageSender& sender) override;
private:
    PersistenceMessageTrackerImpl _trackerInstance;
    PersistenceMessageTracker& _tracker;

    std::shared_ptr<api::RemoveLocationCommand> _msg;

    const DistributorNodeContext& _node_ctx;
    const DocumentSelectionParser& _parser;
    DistributorBucketSpace &_bucketSpace;
};

}

}
