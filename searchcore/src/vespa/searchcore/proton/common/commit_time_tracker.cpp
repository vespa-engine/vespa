// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "commit_time_tracker.h"

namespace proton {

CommitTimeTracker::CommitTimeTracker(vespalib::duration visibilityDelay)
    : _visibilityDelay(visibilityDelay),
      _nextCommit(vespalib::steady_clock::now()),
      _replayDone(false)
{
    _nextCommit = _nextCommit + visibilityDelay;
}

bool
CommitTimeTracker::needCommit() const
{
    if (hasVisibilityDelay()) {
        if (_replayDone) {
            return false; // maintenance job will do forced commits now
        }
        vespalib::steady_time now(vespalib::steady_clock::now());
        if (now > _nextCommit) {
            _nextCommit = now + _visibilityDelay;
            return true;
        }
        return false;
    }
    return true;
}

void
CommitTimeTracker::setVisibilityDelay(vespalib::duration visibilityDelay)
{
    vespalib::steady_time nextCommit = vespalib::steady_clock::now() + visibilityDelay;
    if (nextCommit < _nextCommit) {
        _nextCommit = nextCommit;
    }
    _visibilityDelay = visibilityDelay;
}

}
