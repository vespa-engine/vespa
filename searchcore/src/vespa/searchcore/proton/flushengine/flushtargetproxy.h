// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/flush/iflushtarget.h>

namespace vespalib { class Executor; }

namespace proton {


/**
 * Implements a flush target that proxies everything to the given
 * target.
 */
class FlushTargetProxy : public searchcorespi::IFlushTarget
{
protected:
    IFlushTarget::SP    _target;

public:
    /**
     * Constructs a new instance of this class.
     *
     * @param target   The target to decorate.
     */
    FlushTargetProxy(const IFlushTarget::SP &target);

    /**
     * Constructs a new instance of this class.
     *
     * @param target   The target to decorate.
     * @param prefix   The prefix to prepend to the target
     */
    FlushTargetProxy(const IFlushTarget::SP &target, const vespalib::string & prefix);
    /**
     * Returns the decorated flush target. This should not be used for anything
     * but testing, as invoking a method on the returned target beats the
     * purpose of decorating it.
     *
     * @return The decorated flush target.
     */
    const IFlushTarget::SP & getFlushTarget() const { return _target; }
    // Implements IFlushTarget.
    MemoryGain getApproxMemoryGain() const override { return _target->getApproxMemoryGain(); }
    DiskGain getApproxDiskGain() const override { return _target->getApproxDiskGain(); }
    SerialNum getFlushedSerialNum() const override { return _target->getFlushedSerialNum(); }
    Time getLastFlushTime() const override { return _target->getLastFlushTime(); }
    bool needUrgentFlush() const override { return _target->needUrgentFlush(); }
    Priority getPriority() const override { return _target->getPriority(); }
    uint64_t getApproxBytesToWriteToDisk() const override { return _target->getApproxBytesToWriteToDisk(); }
    searchcorespi::FlushStats getLastFlushStats() const override { return _target->getLastFlushStats(); }
    double get_replay_operation_cost() const override { return _target->get_replay_operation_cost(); }
    Task::UP initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token) override;
};

} // namespace proton
