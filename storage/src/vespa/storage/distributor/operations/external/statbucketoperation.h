// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class StatCallback
 * @ingroup distributor
 *
 * @brief Callback class handling StatBucket messages.
 */
#pragma once

#include <vespa/storage/distributor/operations/operation.h>

namespace storage {

namespace api { class StatBucketCommand; }

namespace distributor {

class DistributorComponent;

class StatBucketOperation : public Operation
{
public:
    StatBucketOperation(DistributorComponent& manager,
                 const std::shared_ptr<api::StatBucketCommand> & cmd);
    ~StatBucketOperation();

    const char* getName() const override { return "statBucket"; }
    std::string getStatus() const override { return ""; }
    void onClose(DistributorMessageSender& sender) override;
    void onStart(DistributorMessageSender& sender) override;
    void onReceive(DistributorMessageSender& sender, const std::shared_ptr<api::StorageReply> & msg) override;
private:
    DistributorComponent& _manager;

    std::shared_ptr<api::StatBucketCommand> _command;

    std::map<uint64_t, uint16_t> _sent;
    std::map<uint16_t, std::string> _results;
};

} // distributor
} // storage

