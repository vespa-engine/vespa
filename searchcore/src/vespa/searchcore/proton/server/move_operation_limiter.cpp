// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "i_blockable_maintenance_job.h"
#include "move_operation_limiter.h"
#include <cassert>

namespace proton {

using BlockedReason = IBlockableMaintenanceJob::BlockedReason;

struct MoveOperationLimiter::Callback : public vespalib::IDestructorCallback {
    MoveOperationLimiter::SP _limiter;
    Callback(MoveOperationLimiter::SP limiter) noexcept : _limiter(std::move(limiter)) {}
    ~Callback() override { _limiter->endOperation(); }
};

bool
MoveOperationLimiter::isOnLimit(const LockGuard &) const
{
    return (_outstandingOps == _maxOutstandingOps);
}

void
MoveOperationLimiter::endOperation()
{
    LockGuard guard(_mutex);
    bool considerUnblock = isOnLimit(guard);
    assert(_outstandingOps > 0);
    --_outstandingOps;
    if (_job && considerUnblock) {
        _job->unBlock(BlockedReason::OUTSTANDING_OPS);
    }
}

MoveOperationLimiter::MoveOperationLimiter(IBlockableMaintenanceJob *job,
                                           uint32_t maxOutstandingOps)
    : _mutex(),
      _job(job),
      _outstandingOps(0),
      _maxOutstandingOps(maxOutstandingOps)
{
}

MoveOperationLimiter::~MoveOperationLimiter() = default;

void
MoveOperationLimiter::clearJob()
{
    LockGuard guard(_mutex);
    _job = nullptr;
}

size_t
MoveOperationLimiter::numPending() const
{
    LockGuard guard(_mutex);
    return _outstandingOps;
}

std::shared_ptr<vespalib::IDestructorCallback>
MoveOperationLimiter::beginOperation()
{
    LockGuard guard(_mutex);
    ++_outstandingOps;
    if (_job && isOnLimit(guard)) {
        _job->setBlocked(BlockedReason::OUTSTANDING_OPS);
    }
    MoveOperationLimiter::SP thisPtr = shared_from_this();
    return std::make_shared<Callback>(std::move(thisPtr));
}

}
