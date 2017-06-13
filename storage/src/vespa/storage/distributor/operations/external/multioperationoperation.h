// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/persistencemessagetracker.h>
#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/storageapi/messageapi/returncode.h>
#include <vespa/vdslib/container/writabledocumentlist.h>

namespace document { class Document; }

namespace storage {

namespace api {
    class CreateBucketReply;
    class MultiOperationCommand;
}

namespace distributor {

class MultiOperationOperation  : public Operation
{
public:
    MultiOperationOperation(DistributorComponent& manager,
                            const std::shared_ptr<api::MultiOperationCommand> & msg,
                            PersistenceOperationMetricSet& metric);
    ~MultiOperationOperation();

    void onStart(DistributorMessageSender& sender) override;
    const char* getName() const override { return "multioperation"; };
    std::string getStatus() const override { return ""; };
    void onReceive(DistributorMessageSender& sender, const std::shared_ptr<api::StorageReply> &) override;
    void onClose(DistributorMessageSender& sender) override;
private:
    std::shared_ptr<api::MultiOperationReply> _reply;
    PersistenceMessageTrackerImpl _trackerInstance;
    PersistenceMessageTracker& _tracker;
    std::shared_ptr<api::MultiOperationCommand> _msg;
    DistributorComponent& _manager;
    uint32_t _minUseBits;

    uint32_t getMinimumUsedBits(const vdslib::DocumentList& opList) const;

    bool sendToBucket(BucketDatabase::Entry& e,
                      std::shared_ptr<api::MultiOperationCommand> moCommand);
};

}

}
