// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cacheevictionguard.h"
#include <vespa/memfilepersistence/memfile/memfile.h>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.memfile.cacheevictionguard");

namespace storage {
namespace memfile {

MemFileCacheEvictionGuard::~MemFileCacheEvictionGuard()
{
    if (!_ok) {
        LOG(debug,
            "Clearing %s from cache to force reload "
            "of file on next access.",
            _ptr->getFile().getBucketId().toString().c_str());
        // Throw away all non-persisted changes to file and clear it from the
        // cache to force a full reload on next access. This is the safest
        // option, as all operations that are not yet persisted should fail
        // back to the client automatically.
        _ptr->clearFlag(Types::SLOTS_ALTERED);
        _ptr.eraseFromCache(); // nothrow
    }
}

}
}
