// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fastos/timestamp.h>

namespace proton {

/**
 * Class used to track when commit is needed based on wanted visibility delay.
 */
class CommitTimeTracker
{
private:
    fastos::TimeStamp                _visibilityDelay;
    mutable fastos::SteadyTimeStamp  _nextCommit;
    bool                             _replayDone;

public:
    CommitTimeTracker(fastos::TimeStamp visibilityDelay);

    bool needCommit() const;

    void setVisibilityDelay(fastos::TimeStamp visibilityDelay);

    bool hasVisibilityDelay() const { return _visibilityDelay != 0; }

    void setReplayDone() { _replayDone = true; }
};

} // namespace proton
