// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/operations/sequenced_operation.h>
#include <vespa/storage/distributor/persistencemessagetracker.h>

namespace storage {

namespace api { class RemoveCommand; }

namespace distributor {

class DistributorBucketSpace;

class RemoveOperation  : public SequencedOperation
{
public:
    RemoveOperation(DistributorNodeContext& node_ctx,
                    DistributorOperationContext& op_ctx,
                    DistributorBucketSpace &bucketSpace,
                    std::shared_ptr<api::RemoveCommand> msg,
                    PersistenceOperationMetricSet& metric,
                    SequencingHandle sequencingHandle = SequencingHandle());
    ~RemoveOperation() override;

    void onStart(DistributorMessageSender& sender) override;
    const char* getName() const override { return "remove"; };
    std::string getStatus() const override { return ""; };

    void onReceive(DistributorMessageSender& sender, const std::shared_ptr<api::StorageReply> &) override;
    void onClose(DistributorMessageSender& sender) override;

private:
    PersistenceMessageTrackerImpl _trackerInstance;
    PersistenceMessageTracker& _tracker;

    std::shared_ptr<api::RemoveCommand> _msg;

    DistributorNodeContext& _node_ctx;
    DistributorBucketSpace &_bucketSpace;
};

}

}
