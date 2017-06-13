// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/storage/distributor/maintenance/maintenancepriority.h>

namespace storage {

namespace distributor {

/**
 * Simple container to communicate a bucket that needs to be
 * checked for maintenanceoperations.
 */
class MaintenanceBucket {
public:
    typedef MaintenancePriority::Priority Priority;

    MaintenanceBucket()
        : node(0),
          pri(MaintenancePriority::NO_MAINTENANCE_NEEDED)
    {}

    MaintenanceBucket(const document::BucketId& bid_,
                      uint16_t node_,
                      Priority pri_)
        : bid(bid_),
          node(node_),
          pri(pri_)
    {

    }

    // The bucket to be checked.
    document::BucketId bid;

    // The primary node of the bucket.
    uint16_t node;

    // The priority to check the bucket.
    Priority pri;

    bool requiresMaintenance() const {
        return pri != MaintenancePriority::NO_MAINTENANCE_NEEDED;
    }

    std::string toString() const {
        return vespalib::make_string("MaintenanceBucket(%s: Node %d, Pri %s)",
                                     bid.toString().c_str(),
                                     (int)node,
                                     MaintenancePriority::toString(pri).c_str());
    }
};

}

}

