// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/operations/sequenced_operation.h>
#include <vespa/storage/distributor/persistencemessagetracker.h>

namespace storage {

namespace api {
class RemoveCommand;
}

namespace distributor {

class RemoveOperation  : public SequencedOperation
{
public:
    RemoveOperation(DistributorComponent& manager,
                    const std::shared_ptr<api::RemoveCommand> & msg,
                    PersistenceOperationMetricSet& metric,
                    SequencingHandle sequencingHandle = SequencingHandle());

    void onStart(DistributorMessageSender& sender);

    const char* getName() const { return "remove"; };

    std::string getStatus() const { return ""; };

    void onReceive(DistributorMessageSender& sender, const std::shared_ptr<api::StorageReply> &);

    void onClose(DistributorMessageSender& sender);

private:
    PersistenceMessageTrackerImpl _trackerInstance;
    PersistenceMessageTracker& _tracker;

    std::shared_ptr<api::RemoveCommand> _msg;

    DistributorComponent& _manager;
};

}

}

