// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/time.h>

namespace proton::matching {

struct ISessionCachePruner {
    virtual ~ISessionCachePruner() {}

    virtual void pruneTimedOutSessions(vespalib::steady_time currentTime) = 0;
};

}
