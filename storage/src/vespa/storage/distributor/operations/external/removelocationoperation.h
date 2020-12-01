// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    RemoveLocationOperation(DistributorNodeContext& node_ctx,
                            DistributorOperationContext& op_ctx,
                            DocumentSelectionParser& parser,
                            DistributorBucketSpace &bucketSpace,
                            std::shared_ptr<api::RemoveLocationCommand> msg,
                            PersistenceOperationMetricSet& metric);
    ~RemoveLocationOperation() override;


    static int getBucketId(DistributorNodeContext& node_ctx,
                           DocumentSelectionParser& parser,
                           const api::RemoveLocationCommand& cmd,
                           document::BucketId& id);
    void onStart(DistributorMessageSender& sender) override;
    const char* getName() const override { return "removelocation"; };
    std::string getStatus() const override { return ""; };
    void onReceive(DistributorMessageSender& sender, const std::shared_ptr<api::StorageReply> &) override;
    void onClose(DistributorMessageSender& sender) override;
private:
    PersistenceMessageTrackerImpl _trackerInstance;
    PersistenceMessageTracker& _tracker;

    std::shared_ptr<api::RemoveLocationCommand> _msg;

    DistributorNodeContext& _node_ctx;
    DocumentSelectionParser& _parser;
    DistributorBucketSpace &_bucketSpace;
};

}

}
