// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <iosfwd>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/storage/distributor/maintenance/maintenancepriority.h>

namespace storage {

namespace distributor {

class PrioritizedBucket {
public:
    typedef MaintenancePriority::Priority Priority;

    static const PrioritizedBucket INVALID;

    PrioritizedBucket()
        : _bucketId(),
          _priority(MaintenancePriority::NO_MAINTENANCE_NEEDED)
    {}

    PrioritizedBucket(const document::BucketId& bid,
                      Priority pri)
        : _bucketId(bid),
          _priority(pri)
    {
    }

    const document::BucketId& getBucketId() const {
        return _bucketId;
    }

    Priority getPriority() const {
        return _priority;
    }

    bool valid() const {
        return _bucketId.getRawId() != 0;
    }

    std::string toString() const {
        return vespalib::make_string("PrioritizedBucket(%s, pri %s)",
                                     _bucketId.toString().c_str(),
                                     MaintenancePriority::toString(_priority).c_str());
    }

    bool operator==(const PrioritizedBucket& other) const {
        return _bucketId == other._bucketId && _priority == other._priority;
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
    document::BucketId _bucketId;
    Priority _priority;
};

std::ostream&
operator<<(std::ostream& os, const PrioritizedBucket& bucket);

}

}

