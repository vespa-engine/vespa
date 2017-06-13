// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "timestat.h"

void
FastS_TimeStatHistory::Reset()
{
    _sampleAccTime = 0.0;
    _totalAccTime = 0.0;
    _sampleIdx = 0;
    _sampleCount = 0;
    _totalCount = 0;
    for (uint32_t i = 0; i < _timestatssize; i++)
        _sampleTimes[i] = Sample();
}


double
FastS_TimeStatHistory::GetMaxTime() const
{
    double max = 0.0;
    uint32_t idx = _sampleIdx;
    for (uint32_t residue = _sampleCount;
         residue > 0; residue--)
    {
        if (idx > 0)
            idx--;
        else
            idx = _timestatssize - 1;
        if (_sampleTimes[idx]._time > max)
            max = _sampleTimes[idx]._time;
    }
    return max;
}


void
FastS_TimeStatHistory::Update(double tnow, double t, bool timedout)
{
    uint32_t timeIdx = getTimeIdx(tnow);
    if (_slotCount == 0u) {
        _timeSlots[_slotIdx].init(timeIdx);
        ++_slotCount;
    } else {
        TimeSlot &ts = _timeSlots[_slotIdx];
        if (ts._timeIdx > timeIdx)
            timeIdx = ts._timeIdx;
        if (ts._timeIdx < timeIdx) {
            if (_slotCount < NUM_TIMESLOTS)
                ++_slotCount;
            _slotIdx = nextTimeSlot(_slotIdx);
            _timeSlots[_slotIdx].init(timeIdx);
        }
    }
    _timeSlots[_slotIdx].update(t, timedout);

    _totalAccTime += t;
    ++_totalCount;
    if (timedout)
        ++_totalTimeouts;
    if (_sampleCount >= _timestatssize) {
        const Sample &s = _sampleTimes[_sampleIdx];
        _sampleAccTime -= s._time;
        if (s._timedout)
            --_sampleTimeouts;
        --_sampleCount;
    }
    _sampleTimes[_sampleIdx] = Sample(t, timedout);
    _sampleAccTime += t;
    if (timedout)
        ++_sampleTimeouts;
    _sampleIdx++;
    if (_sampleIdx >= _timestatssize)
        _sampleIdx = 0;
    ++_sampleCount;
}


void
FastS_TimeStatHistory::getRecentStats(double tsince,
                                      FastS_TimeStatTotals &totals)
{
    uint32_t timeIdx = getTimeIdx(tsince);
    uint32_t slotCount = _slotCount;
    uint32_t slotIdx = _slotIdx;
    for (; slotCount > 0u && _timeSlots[slotIdx]._timeIdx >= timeIdx;
         --slotCount, slotIdx = prevTimeSlot(slotIdx)) {
        TimeSlot &ts = _timeSlots[slotIdx];
        totals._totalCount += ts._count;
        totals._totalTimeouts += ts._timeouts;
        totals._totalAccTime += ts._accTime;
    }
}


double
FastS_TimeStatHistory::getLoadTime(double tsince, double tnow)
{
    const uint32_t holeSize = 2;	// 2 missing slots => hole
    const uint32_t minSlotLoad = 4;	// Mininum load for not being "missing"
    uint32_t sinceTimeIdx = getTimeIdx(tsince);
    uint32_t timeIdx = getTimeIdx(tnow);
    uint32_t slotCount = _slotCount;
    uint32_t slotIdx = _slotIdx;
    uint32_t doneTimeIdx = timeIdx;
    for (; slotCount > 0u; --slotCount, slotIdx = prevTimeSlot(slotIdx)) {
        TimeSlot &ts = _timeSlots[slotIdx];
        if (ts._timeIdx + holeSize < doneTimeIdx)
            break;	// Found hole, i.e. holeSize missing slots
        if (ts._timeIdx + holeSize < sinceTimeIdx)
            break;	// No point in looking further back
        if (ts._count >= minSlotLoad)
            doneTimeIdx = ts._timeIdx;
    }
    return tnow - getTimeFromTimeIdx(doneTimeIdx);
}
