// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/operations/operation.h>
#include <vespa/storage/distributor/persistencemessagetracker.h>

namespace storage {

namespace api {
class RemoveLocationCommand;
}

namespace distributor {

class RemoveLocationOperation  : public Operation
{
public:
    RemoveLocationOperation(DistributorComponent& manager,
                            const std::shared_ptr<api::RemoveLocationCommand> & msg,
                            PersistenceOperationMetricSet& metric);

    void onStart(DistributorMessageSender& sender);

    static int getBucketId(DistributorComponent& manager,
                           const api::RemoveLocationCommand& cmd,
                           document::BucketId& id);

    const char* getName() const { return "removelocation"; };

    std::string getStatus() const { return ""; };

    void onReceive(DistributorMessageSender& sender, const std::shared_ptr<api::StorageReply> &);

    void onClose(DistributorMessageSender& sender);

private:
    PersistenceMessageTrackerImpl _trackerInstance;
    PersistenceMessageTracker& _tracker;

    std::shared_ptr<api::RemoveLocationCommand> _msg;

    DistributorComponent& _manager;
};

}

}

