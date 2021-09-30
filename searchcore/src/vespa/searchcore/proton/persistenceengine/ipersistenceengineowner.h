// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucketspace.h>

namespace storage::spi { class ClusterState; }

namespace proton {

class IPersistenceEngineOwner
{
public:
    virtual ~IPersistenceEngineOwner() = default;
    virtual void
    setClusterState(document::BucketSpace bucketSpace, const storage::spi::ClusterState &calc) = 0;
};

} // namespace proton

