// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

class IHeartBeatHandler
{
public:
    virtual void heartBeat() = 0;

    virtual ~IHeartBeatHandler() = default;
};

} // namespace proton
