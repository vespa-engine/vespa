// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    setClusterState(const storage::spi::ClusterState &calc) = 0;
};

} // namespace proton

