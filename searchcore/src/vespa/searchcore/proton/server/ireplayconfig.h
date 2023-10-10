// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/serialnum.h>

namespace proton {

class IReplayConfig
{
public:
    virtual ~IReplayConfig();

    virtual void replayConfig(search::SerialNum serialNum) = 0;
};

} // namespace proton

