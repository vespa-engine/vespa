// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/operations/operation.h>

namespace storage {
namespace distributor {

class MaintenanceOperation : public Operation
{
public:
    typedef enum {
        DELETE_BUCKET,
        MERGE_BUCKET,
        SPLIT_BUCKET,
        JOIN_BUCKET,
        SET_BUCKET_STATE,
        GARBAGE_COLLECTION,
        OPERATION_COUNT
    } Type;

    typedef std::shared_ptr<MaintenanceOperation> SP;

    virtual const std::string& getDetailedReason() const = 0;
};

} // distributor
} // storage
