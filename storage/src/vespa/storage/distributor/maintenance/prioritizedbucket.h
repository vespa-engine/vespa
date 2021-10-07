// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <iosfwd>
#include <vespa/document/bucket/bucket.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/storage/distributor/maintenance/maintenancepriority.h>

namespace storage::distributor {

class PrioritizedBucket {
public:
    using Priority = MaintenancePriority::Priority;

    static const PrioritizedBucket INVALID;

    PrioritizedBucket()
        : _bucket(),
          _priority(MaintenancePriority::NO_MAINTENANCE_NEEDED)
    {}

    PrioritizedBucket(const document::Bucket &bucket, Priority pri)
        : _bucket(bucket),
          _priority(pri)
    {
    }

    document::Bucket getBucket() const { return _bucket; }

    Priority getPriority() const {
        return _priority;
    }

    bool valid() const {
        return _bucket.getBucketId().getRawId() != 0;
    }

    std::string toString() const {
        return vespalib::make_string("PrioritizedBucket(%s, pri %s)",
                                     _bucket.toString().c_str(),
                                     MaintenancePriority::toString(_priority));
    }

    bool operator==(const PrioritizedBucket& other) const {
        return _bucket == other._bucket && _priority == other._priority;
    }

    bool requiresMaintenance() const {
        return _priority != MaintenancePriority::NO_MAINTENANCE_NEEDED;
    }

    bool moreImportantThan(const PrioritizedBucket& other) const {
        return _priority > other._priority;
    }

    bool moreImportantThan(Priority otherPri) const {
        return _priority > otherPri;
    }

private:
    document::Bucket _bucket;
    Priority _priority;
};

std::ostream&
operator<<(std::ostream& os, const PrioritizedBucket& bucket);

}
