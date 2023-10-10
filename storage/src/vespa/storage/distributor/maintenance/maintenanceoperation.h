// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/operations/operation.h>

namespace storage::distributor {

class MaintenanceOperation : public Operation
{
public:
    enum Type {
        DELETE_BUCKET,
        MERGE_BUCKET,
        SPLIT_BUCKET,
        JOIN_BUCKET,
        SET_BUCKET_STATE,
        GARBAGE_COLLECTION,
        OPERATION_COUNT
    };

    using SP = std::shared_ptr<MaintenanceOperation>;

    virtual const std::string& getDetailedReason() const = 0;
};

} // storage::distributor
