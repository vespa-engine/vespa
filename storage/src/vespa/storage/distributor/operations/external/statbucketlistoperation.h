// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/hdr_abort.h>
#include <vespa/storage/distributor/operations/operation.h>
#include <vespa/storage/bucketdb/bucketdatabase.h>

namespace storage::api { class GetBucketListCommand; }

namespace storage::distributor {

class MaintenanceOperationGenerator;

class StatBucketListOperation : public Operation
{
public:
    StatBucketListOperation(
            const BucketDatabase& bucketDb,
            const MaintenanceOperationGenerator& generator,
            uint16_t distributorIndex,
            const std::shared_ptr<api::GetBucketListCommand>& cmd);
    ~StatBucketListOperation() override;

    const char* getName() const noexcept override { return "statBucketList"; }
    std::string getStatus() const override { return ""; }

    void onStart(DistributorStripeMessageSender& sender) override;
    void onReceive(DistributorStripeMessageSender&, const std::shared_ptr<api::StorageReply>&) override
    {
        // Never called.
        HDR_ABORT("should not be reached");
    }
    void onClose(DistributorStripeMessageSender&) override {}

private:
    void getBucketStatus(const BucketDatabase::Entry& entry, std::ostream& os) const;

    const BucketDatabase& _bucketDb;
    const MaintenanceOperationGenerator& _generator;
    uint16_t _distributorIndex;
    std::shared_ptr<api::GetBucketListCommand> _command;
};

} // storage::distributor
