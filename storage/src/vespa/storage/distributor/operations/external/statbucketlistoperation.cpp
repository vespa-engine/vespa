// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "statbucketlistoperation.h"
#include <vespa/storageapi/message/stat.h>
#include <vespa/storage/distributor/maintenance/maintenanceoperationgenerator.h>
#include <sstream>

namespace storage::distributor {

StatBucketListOperation::StatBucketListOperation(
        const BucketDatabase& bucketDb,
        const MaintenanceOperationGenerator& generator,
        uint16_t distributorIndex,
        const std::shared_ptr<api::GetBucketListCommand>& cmd)
    : _bucketDb(bucketDb),
      _generator(generator),
      _distributorIndex(distributorIndex),
      _command(cmd)
{
}

StatBucketListOperation::~StatBucketListOperation() = default;

void
StatBucketListOperation::getBucketStatus(const BucketDatabase::Entry& entry,
                                         std::ostream& ost) const
{
    document::Bucket bucket(_command->getBucket().getBucketSpace(), entry.getBucketId());
    std::vector<MaintenanceOperation::SP> operations(_generator.generateAll(bucket));

    for (uint32_t i = 0; i < operations.size(); ++i) {
        const MaintenanceOperation& op(*operations[i]);
        if (i > 0) {
            ost << ", ";
        }
        ost << op.getName() << ": " << op.getDetailedReason();
    }
    if (!operations.empty()) {
        ost << ' ';
    }
    ost << "[" << entry->toString() << "]";
}

void
StatBucketListOperation::onStart(DistributorStripeMessageSender& sender)
{
    auto reply = std::make_shared<api::GetBucketListReply>(*_command);

    std::vector<BucketDatabase::Entry> entries;
    _bucketDb.getAll(_command->getBucketId(), entries);

    for (const auto& entry : entries) {
        std::ostringstream ost;
        ost << "[distributor:" << _distributorIndex << "] ";

        getBucketStatus(entry, ost);

        reply->getBuckets().emplace_back(entry.getBucketId(), ost.str());
    }
    sender.sendReply(reply);
}

}
