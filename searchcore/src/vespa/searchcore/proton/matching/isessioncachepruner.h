// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastos/timestamp.h>

namespace proton::matching {

struct ISessionCachePruner {
    virtual ~ISessionCachePruner() {}

    virtual void pruneTimedOutSessions(fastos::SteadyTimeStamp currentTime) = 0;
};

}
