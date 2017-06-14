// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <cstdint>

class FastS_TimeStatTotals
{
public:
    int _totalCount;
    uint32_t _totalTimeouts;
    double _totalAccTime;

    FastS_TimeStatTotals()
        : _totalCount(0),
          _totalTimeouts(0u),
          _totalAccTime(0)
    {
    }

    FastS_TimeStatTotals &
    operator+=(const FastS_TimeStatTotals &rhs)
    {
        _totalCount += rhs._totalCount;
        _totalTimeouts += rhs._totalTimeouts;
        _totalAccTime += rhs._totalAccTime;
        return *this;
    }

    FastS_TimeStatTotals &
    operator-=(const FastS_TimeStatTotals &rhs)
    {
        _totalCount -= rhs._totalCount;
        _totalTimeouts -= rhs._totalTimeouts;
        _totalAccTime -= rhs._totalAccTime;
        return *this;
    }

    double
    getAvgTime() const
    {
        if (_totalCount == 0)
            return 0.0;
        return (_totalAccTime / _totalCount);
    }

    double
    getTimeoutRate() const
    {
        if (_totalCount == 0)
            return 0.0;
        return (static_cast<double>(_totalTimeouts) / _totalCount);
    }
};


class FastS_TimeStatHistory
{
    enum {
        _timestatssize = 100
    };
    enum {
        SLOT_SIZE = 5,
        NUM_TIMESLOTS = 128
    };
    double   _sampleAccTime;
    double   _totalAccTime;
    uint32_t _sampleIdx;
    uint32_t _sampleCount;
    uint32_t _sampleTimeouts;
    uint32_t _totalCount;
    uint32_t _totalTimeouts;
    struct Sample {
        double  _time;
        bool _timedout;

        Sample()
            : _time(0.0),
              _timedout(false)
        {
        }

        Sample(double time, bool timedout)
            : _time(time),
              _timedout(timedout)
        {
        }
    };
    Sample _sampleTimes[_timestatssize];

    struct TimeSlot
    {
        double _accTime;
        uint32_t _count;
        uint32_t _timeouts;
        uint32_t _timeIdx;

        TimeSlot()
            : _accTime(0.0),
              _count(0u),
              _timeouts(0u),
              _timeIdx(0u)
        {
        }

        void
        init(uint32_t timeIdx)
        {
            _accTime = 0.0;
            _count = 0u;
            _timeouts = 0u;
            _timeIdx = timeIdx;
        }

        void
        update(double t, bool timedout)
        {
            _accTime += t;
            ++_count;
            if (timedout)
                ++_timeouts;
        }
    };

    static uint32_t
    nextTimeSlot(uint32_t timeSlot)
    {
        return (timeSlot >= (NUM_TIMESLOTS - 1)) ? 0u : timeSlot + 1;
    }

    static uint32_t
    prevTimeSlot(uint32_t timeSlot)
    {
        return (timeSlot == 0u) ? (NUM_TIMESLOTS - 1) : timeSlot - 1;
    }


    TimeSlot _timeSlots[NUM_TIMESLOTS];
    uint32_t _slotCount;
    uint32_t _slotIdx;

    static uint32_t
    getTimeIdx(double t)
    {
        // Each SLOT_SIZE second period has it's own slot of statistics
        return static_cast<uint32_t>(t / SLOT_SIZE);
    }

    static double
    getTimeFromTimeIdx(uint32_t timeIdx)
    {
        return static_cast<double>(timeIdx) * SLOT_SIZE;
    }
public:
    FastS_TimeStatHistory()
        : _sampleAccTime(0.0),
          _totalAccTime(0.0),
          _sampleIdx(0),
          _sampleCount(0),
          _sampleTimeouts(0),
          _totalCount(0),
          _totalTimeouts(0u),
          _sampleTimes(),
          _timeSlots(),
          _slotCount(0u),
          _slotIdx(0u)
    {
        Reset();
    }

    void Reset();

    double  GetSampleAccTime() const { return _sampleAccTime; }
    uint32_t GetSampleCount() const { return _sampleCount; }

    uint32_t
    getSampleTimeouts() const
    {
        return _sampleTimeouts;
    }

    double GetAvgTime() const
    {
        if (_sampleCount == 0)
            return 0.0;
        return (_sampleAccTime / _sampleCount);
    }
    double GetMaxTime() const;

    void Update(double tnow, double t, bool timedout);

    uint32_t GetTotalCount() const { return _totalCount; }
    double  GetTotalAccTime() const { return _totalAccTime; }

    uint32_t
    getTotalTimeouts() const
    {
        return _totalTimeouts;
    }

    void AddTotal(FastS_TimeStatTotals *totals) {
        totals->_totalCount += GetTotalCount();
        totals->_totalTimeouts += getTotalTimeouts();
        totals->_totalAccTime += GetTotalAccTime();
    }

    void
    getRecentStats(double tsince, FastS_TimeStatTotals &totals);

    double
    getLoadTime(double tsince, double tnow);
};


