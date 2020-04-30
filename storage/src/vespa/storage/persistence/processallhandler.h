// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/persistence/messages.h>
#include <vespa/storage/persistence/persistenceutil.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/persistence/spi/persistenceprovider.h>

namespace document::select { class Node; }

namespace storage {

class ProcessAllHandler : public Types {

public:
    ProcessAllHandler(PersistenceUtil&, spi::PersistenceProvider&);
    MessageTracker::UP handleRemoveLocation(api::RemoveLocationCommand&, MessageTracker::UP tracker);
    MessageTracker::UP handleStatBucket(api::StatBucketCommand&, MessageTracker::UP tracker);

protected:
    PersistenceUtil& _env;
    spi::PersistenceProvider& _spi;
};

} // storage

