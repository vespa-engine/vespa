// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/time.h>

namespace proton {

/**
 * Class used to track when commit is needed based on wanted visibility delay.
 */
class CommitTimeTracker
{
private:
    vespalib::duration             _visibilityDelay;
    mutable vespalib::steady_time  _nextCommit;
    bool                           _replayDone;

public:
    CommitTimeTracker(vespalib::duration visibilityDelay);
    bool needCommit() const;
    void setVisibilityDelay(vespalib::duration visibilityDelay);
    bool hasVisibilityDelay() const { return _visibilityDelay != vespalib::duration::zero(); }
    void setReplayDone() { _replayDone = true; }
};

} // namespace proton
