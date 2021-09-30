// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_move_operation_limiter.h"
#include <vespa/vespalib/util/idestructorcallback.h>
#include <memory>
#include <mutex>

namespace proton {

class IBlockableMaintenanceJob;

/**
 * Class used to limit the number of outstanding move operations a blockable maintenance job can have.
 *
 * When crossing the boundary of max outstanding operations the job is blocked/unblocked.
 * Create a destructor callback with beginOperation() and pass this to the component(s) responsible for handling the move operation.
 * When this object is destructed (in any thread) the limiter is signaled and the job can be unblocked (if blocked).
 */
class MoveOperationLimiter : public IMoveOperationLimiter,
                             public std::enable_shared_from_this<MoveOperationLimiter> {
private:
    using LockGuard = std::lock_guard<std::mutex>;

    struct Callback;

    mutable std::mutex        _mutex;
    IBlockableMaintenanceJob *_job;
    uint32_t                  _outstandingOps;
    const uint32_t            _maxOutstandingOps;

    bool isOnLimit(const LockGuard &guard) const;
    void endOperation();

public:
    using SP = std::shared_ptr<MoveOperationLimiter>;
    MoveOperationLimiter(IBlockableMaintenanceJob *job, uint32_t maxOutstandingOps);
    ~MoveOperationLimiter() override;
    void clearJob();
    bool isAboveLimit() const { return numPending() >= _maxOutstandingOps; }
    bool hasPending() const { return numPending() > 0;}
    std::shared_ptr<vespalib::IDestructorCallback> beginOperation() override;
    size_t numPending() const override;
};

}
