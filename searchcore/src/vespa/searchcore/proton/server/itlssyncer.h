// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton
{


/*
 * Interface class for syncing transaction log server in a safe manner.
 */
class ITlsSyncer
{
public:
    virtual ~ITlsSyncer() = default;
    virtual void sync() = 0;
};

}
