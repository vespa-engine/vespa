// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "commit_time_tracker.h"

namespace proton {

CommitTimeTracker::CommitTimeTracker(fastos::TimeStamp visibilityDelay)
    : _visibilityDelay(visibilityDelay),
      _nextCommit(fastos::ClockSteady::now()),
      _replayDone(false)
{
    _nextCommit = _nextCommit + visibilityDelay;
}

bool
CommitTimeTracker::needCommit() const
{
    if (_visibilityDelay > 0) {
        if (_replayDone) {
            return false; // maintenance job will do forced commits now
        }
        fastos::SteadyTimeStamp now(fastos::ClockSteady::now());
        if (now > _nextCommit) {
            _nextCommit = now + _visibilityDelay;
            return true;
        }
        return false;
    }
    return true;
}

void
CommitTimeTracker::setVisibilityDelay(fastos::TimeStamp visibilityDelay)
{
    fastos::SteadyTimeStamp nextCommit = fastos::ClockSteady::now() + visibilityDelay;
    if (nextCommit < _nextCommit) {
        _nextCommit = nextCommit;
    }
    _visibilityDelay = visibilityDelay;
}

} // namespace proton
