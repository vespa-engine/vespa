// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/common/bucketmessages.h>
#include <vespa/storage/persistence/persistenceutil.h>

namespace storage {

class DiskMoveOperationHandler : public Types {

public:
    DiskMoveOperationHandler(PersistenceUtil&,
                             spi::PersistenceProvider& provider);

    MessageTracker::UP handleBucketDiskMove(BucketDiskMoveCommand&,
                                            spi::Context&);

private:
    PersistenceUtil& _env;
    spi::PersistenceProvider& _provider;
};

} // storage

