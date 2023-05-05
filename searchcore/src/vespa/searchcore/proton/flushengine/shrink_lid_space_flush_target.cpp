// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "shrink_lid_space_flush_target.h"
#include <vespa/searchlib/common/i_compactable_lid_space.h>

namespace proton {

using searchcorespi::IFlushTarget;
using searchcorespi::LeafFlushTarget;
using searchcorespi::FlushStats;
using searchcorespi::FlushTask;

class ShrinkLidSpaceFlushTarget::Flusher : public FlushTask
{
    ShrinkLidSpaceFlushTarget &_target;
    SerialNum _flushSerialNum;
public:
    Flusher(ShrinkLidSpaceFlushTarget &target, SerialNum flushSerialNum);
    void run() override;
    search::SerialNum getFlushSerial() const override;
};

ShrinkLidSpaceFlushTarget::Flusher::Flusher(ShrinkLidSpaceFlushTarget &target, SerialNum flushSerialNum)
    : FlushTask(),
      _target(target),
      _flushSerialNum(flushSerialNum)
{
    _target._target->shrinkLidSpace();
}

void
ShrinkLidSpaceFlushTarget::Flusher::run()
{
    _target._flushedSerialNum = _flushSerialNum;
    _target._lastFlushTime = vespalib::system_clock::now();
}

search::SerialNum
ShrinkLidSpaceFlushTarget::Flusher::getFlushSerial() const
{
    return _flushSerialNum;
}

ShrinkLidSpaceFlushTarget::ShrinkLidSpaceFlushTarget(const vespalib::string &name,
                                                     Type type,
                                                     Component component,
                                                     SerialNum flushedSerialNum,
                                                     Time lastFlushTime,
                                                     std::shared_ptr<ICompactableLidSpace> target)
    : LeafFlushTarget(name, type, component),

      _target(std::move(target)),
      _flushedSerialNum(flushedSerialNum),
      _lastFlushTime(lastFlushTime),
      _lastStats()
{
}

IFlushTarget::MemoryGain
ShrinkLidSpaceFlushTarget::getApproxMemoryGain() const
{
    int64_t canFree = _target->getEstimatedShrinkLidSpaceGain();
    return MemoryGain(canFree, 0);
}

IFlushTarget::DiskGain
ShrinkLidSpaceFlushTarget::getApproxDiskGain() const
{
    return DiskGain(0, 0);
}

IFlushTarget::SerialNum
ShrinkLidSpaceFlushTarget::getFlushedSerialNum() const
{
    return _flushedSerialNum;
}

IFlushTarget::Time
ShrinkLidSpaceFlushTarget::getLastFlushTime() const
{
    return _lastFlushTime;
}

IFlushTarget::Task::UP
ShrinkLidSpaceFlushTarget::initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken>)
{
    if (currentSerial < _flushedSerialNum) {
        _lastFlushTime = vespalib::system_clock::now();
        return IFlushTarget::Task::UP();
    } else if (!_target->canShrinkLidSpace()) {
        _flushedSerialNum = currentSerial;
        _lastFlushTime = vespalib::system_clock::now();
        return IFlushTarget::Task::UP();
    } else {
        return std::make_unique<Flusher>(*this, currentSerial);
    }
}

FlushStats
ShrinkLidSpaceFlushTarget::getLastFlushStats() const
{
    return _lastStats;
}

uint64_t
ShrinkLidSpaceFlushTarget::getApproxBytesToWriteToDisk() const
{
    return 0;
}

} // namespace proton
