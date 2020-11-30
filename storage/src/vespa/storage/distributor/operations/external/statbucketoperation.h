// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class StatCallback
 * @ingroup distributor
 *
 * @brief Callback class handling StatBucket messages.
 */
#pragma once

#include <vespa/storage/distributor/operations/operation.h>
#include <map>

namespace storage::api { class StatBucketCommand; }

namespace storage::distributor {

class DistributorBucketSpace;

class StatBucketOperation : public Operation
{
public:
    StatBucketOperation(DistributorBucketSpace &bucketSpace,
                        const std::shared_ptr<api::StatBucketCommand> & cmd);
    ~StatBucketOperation();

    const char* getName() const override { return "statBucket"; }
    std::string getStatus() const override { return ""; }
    void onClose(DistributorMessageSender& sender) override;
    void onStart(DistributorMessageSender& sender) override;
    void onReceive(DistributorMessageSender& sender, const std::shared_ptr<api::StorageReply> & msg) override;
private:
    DistributorBucketSpace &_bucketSpace;

    std::shared_ptr<api::StatBucketCommand> _command;

    std::map<uint64_t, uint16_t>    _sent;
    std::map<uint16_t, std::string> _results;
};

}
