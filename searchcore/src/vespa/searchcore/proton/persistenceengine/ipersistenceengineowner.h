// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/persistence/spi/abstractpersistenceprovider.h>

namespace proton
{

class IPersistenceEngineOwner
{
public:
    virtual
    ~IPersistenceEngineOwner()
    {
    }

    virtual void
    setClusterState(document::BucketSpace bucketSpace, const storage::spi::ClusterState &calc) = 0;
};

} // namespace proton

