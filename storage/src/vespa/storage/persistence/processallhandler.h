// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "types.h"
#include "messages.h"
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/message/stat.h>

namespace storage {

namespace spi { struct PersistenceProvider; }
class PersistenceUtil;

class ProcessAllHandler : public Types {
public:
    ProcessAllHandler(const PersistenceUtil&, spi::PersistenceProvider&);
    MessageTrackerUP handleRemoveLocation(api::RemoveLocationCommand&, MessageTrackerUP tracker) const;
    MessageTrackerUP handleStatBucket(api::StatBucketCommand&, MessageTrackerUP tracker) const;
private:
    const PersistenceUtil    & _env;
    spi::PersistenceProvider & _spi;
};

} // storage

