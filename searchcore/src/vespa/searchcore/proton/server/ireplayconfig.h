// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/transactionlog/translogclient.h>

namespace proton
{

class IReplayConfig
{
public:
    virtual
    ~IReplayConfig(void);

    virtual void
    replayConfig(search::SerialNum serialNum) = 0;
};

} // namespace proton

