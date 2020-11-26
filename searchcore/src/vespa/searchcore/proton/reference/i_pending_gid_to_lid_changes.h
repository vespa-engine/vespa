// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

/*
 * Interface class for a container of gid to lid changes awaiting a
 * force commit.
 */
class IPendingGidToLidChanges
{
public:
    virtual ~IPendingGidToLidChanges() = default;
    virtual void notify_done() = 0;
};

}
