// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "commit_time_tracker.h"

namespace proton {

CommitTimeTracker::CommitTimeTracker(vespalib::duration visibilityDelay)
    : _visibilityDelay(visibilityDelay),
      _nextCommit(vespalib::steady_clock::now())
{
    _nextCommit = _nextCommit + visibilityDelay;
}

bool
CommitTimeTracker::needCommit() const
{
    if (hasVisibilityDelay()) {
        vespalib::steady_time now(vespalib::steady_clock::now());
        if (now > _nextCommit) {
            _nextCommit = now + _visibilityDelay;
            return true;
        }
        return false;
    }
    return false;
}

}
