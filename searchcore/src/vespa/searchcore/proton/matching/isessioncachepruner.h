// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastos/timestamp.h>

namespace proton {
namespace matching {

struct ISessionCachePruner {
    virtual ~ISessionCachePruner() {}

    virtual void pruneTimedOutSessions(fastos::TimeStamp currentTime) = 0;
};

}  // namespace proton::matching
}  // namespace proton

