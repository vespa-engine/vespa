// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    FlushTargetProxy(const IFlushTarget::SP &target,
                     const vespalib::string & prefix);
    /**
     * Returns the decorated flush target. This should not be used for anything
     * but testing, as invoking a method on the returned target beats the
     * purpose of decorating it.
     *
     * @return The decorated flush target.
     */
    const IFlushTarget::SP &
    getFlushTarget() const
    {
        return _target;
    }
    // Implements IFlushTarget.
    virtual MemoryGain
    getApproxMemoryGain() const override;

    virtual DiskGain
    getApproxDiskGain() const override;

    virtual SerialNum
    getFlushedSerialNum() const override;

    virtual Time
    getLastFlushTime() const override;

    virtual bool
    needUrgentFlush() const override;

    virtual Task::UP
    initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token) override;

    virtual searchcorespi::FlushStats
    getLastFlushStats() const override;

    virtual uint64_t getApproxBytesToWriteToDisk() const override;
};

} // namespace proton
