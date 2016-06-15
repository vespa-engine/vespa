// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <sstream>
#include <vespa/storage/distributor/maintenance/simplemaintenancescanner.h>

namespace storage {
namespace distributor {

MaintenanceScanner::ScanResult
SimpleMaintenanceScanner::scanNext()
{
    BucketDatabase::Entry entry(_bucketDb.getNext(_bucketCursor));
    if (!entry.valid()) {
        return ScanResult::createDone();
    }
    prioritizeBucket(entry.getBucketId());
    _bucketCursor = entry.getBucketId();
    return ScanResult::createNotDone(entry);
}

void
SimpleMaintenanceScanner::reset()
{
    _bucketCursor = document::BucketId();
    _pendingMaintenance = PendingMaintenanceStats();
}

void
SimpleMaintenanceScanner::prioritizeBucket(const document::BucketId& id)
{
    MaintenancePriorityAndType pri(
            _priorityGenerator.prioritize(
                    id, _pendingMaintenance.perNodeStats));
    if (pri.requiresMaintenance()) {
        _bucketPriorityDb.setPriority(PrioritizedBucket(id, pri.getPriority().getPriority()));
        assert(pri.getType() != MaintenanceOperation::OPERATION_COUNT);
        ++_pendingMaintenance.global.pending[pri.getType()];
    }
}

std::ostream&
operator<<(std::ostream& os,
           const SimpleMaintenanceScanner::GlobalMaintenanceStats& stats)
{
    using MO = MaintenanceOperation;
    os << "delete bucket: "        << stats.pending[MO::DELETE_BUCKET]
       << ", merge bucket: "       << stats.pending[MO::MERGE_BUCKET]
       << ", split bucket: "       << stats.pending[MO::SPLIT_BUCKET]
       << ", join bucket: "        << stats.pending[MO::JOIN_BUCKET]
       << ", set bucket state: "   << stats.pending[MO::SET_BUCKET_STATE]
       << ", garbage collection: " << stats.pending[MO::GARBAGE_COLLECTION];
    return os;
}

}
}
