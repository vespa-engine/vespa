// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ipendinglidtracker.h"
#include <vespa/vespalib/stllike/hash_map.h>
#include <mutex>
#include <condition_variable>

namespace proton {

/**
 * Base class for doing 2 phased lid tracking. The first phase is from when the feed operation
 * is in progress and lasts until the OperationDoneContext goes out of scope. This might include commit
 * when visibility-delay is zero.
 * When a commit is started a snapshot containing all lids in state NEED_COMMIT are taken,
 * while also moving the lids to WAITING. Once the snapshot goes out of scope when the commit is complete,
 * it will cleanup and move all lids from WAITING to COMPLETE.
 */
class PendingLidTrackerBase : public IPendingLidTracker,
                              public ILidCommitState
{
public:
    ~PendingLidTrackerBase();
    struct Payload {
        virtual ~Payload() = default;
    };
    using Snapshot = std::unique_ptr<Payload>;
    virtual Snapshot produceSnapshot() = 0;

    State waitState(State state, uint32_t lid) const override;
    State waitState(State state, const LidList & lids) const override;
protected:
    using MonitorGuard = std::unique_lock<std::mutex>;
    PendingLidTrackerBase();
    virtual State waitFor(MonitorGuard & guard, State state, uint32_t lid) const = 0;
    MonitorGuard getGuard() { return MonitorGuard(_mutex); }
    mutable std::mutex                     _mutex;
    mutable std::condition_variable        _cond;
};

/**
 * Use for tracking lids when visibility-delay is zero and commit is implicit.
 * In this case lids go directly to WAITING and the second phase is a noop.
 */
class PendingLidTracker : public PendingLidTrackerBase
{
public:
    PendingLidTracker();
    ~PendingLidTracker() override;
    Token produce(uint32_t lid) override;
    Snapshot produceSnapshot() override;
private:
    void consume(uint32_t lid) override;
    State waitFor(MonitorGuard & guard, State state, uint32_t lid) const override;

    vespalib::hash_map<uint32_t, uint32_t> _pending;
};

}
