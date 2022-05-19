// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::FakeClock
 * \ingroup test
 *
 * \brief Implements a fake clock to use for testing.
 */
#pragma once

#include <vespa/storageframework/generic/clock/clock.h>
#include <mutex>

namespace storage::framework::defaultimplementation {

struct FakeClock : public framework::Clock {
    enum Mode {
        FAKE_ABSOLUTE, // Time is always equal to supplied absolute time
        FAKE_ABSOLUTE_CYCLE // Time is equal to absolute time + counter that
                            // increases for each request so you never get same
                            // timestamp twice.
    };

private:
    Mode _mode;
    framework::MicroSecTime _absoluteTime;
    mutable time_t          _cycleCount;
    mutable std::mutex      _lock;

public:
    FakeClock(Mode m = FAKE_ABSOLUTE,
              framework::MicroSecTime startTime = framework::MicroSecTime(1));

    void setMode(Mode m) {
        std::lock_guard guard(_lock);
        _mode = m;
    }
    virtual void setFakeCycleMode() { setMode(FAKE_ABSOLUTE_CYCLE); }

    virtual void setAbsoluteTimeInSeconds(uint32_t seconds) {
        std::lock_guard guard(_lock);
        _absoluteTime = framework::MicroSecTime(seconds * uint64_t(1000000));
        _cycleCount = 0;
        _mode = FAKE_ABSOLUTE;
    }

    virtual void setAbsoluteTimeInMicroSeconds(uint64_t usecs) {
        std::lock_guard guard(_lock);
        _absoluteTime = framework::MicroSecTime(usecs);
        _cycleCount = 0;
        _mode = FAKE_ABSOLUTE;
    }

    virtual void addMilliSecondsToTime(uint64_t ms) {
        std::lock_guard guard(_lock);
        _absoluteTime += framework::MicroSecTime(ms * 1000);
    }

    virtual void addSecondsToTime(uint32_t nr) {
        std::lock_guard guard(_lock);
        _absoluteTime += framework::MicroSecTime(nr * uint64_t(1000000));
    }

    framework::MicroSecTime getTimeInMicros() const override {
        std::lock_guard guard(_lock);
        if (_mode == FAKE_ABSOLUTE) return _absoluteTime;
        return _absoluteTime + framework::MicroSecTime(1000000 * _cycleCount++);
    }
    framework::MilliSecTime getTimeInMillis() const override {
        return getTimeInMicros().getMillis();
    }
    framework::SecondTime getTimeInSeconds() const override {
        return getTimeInMicros().getSeconds();
    }
    framework::MonotonicTimePoint getMonotonicTime() const override {
        // For simplicity, assume fake monotonic time follows fake wall clock.
        return MonotonicTimePoint(std::chrono::microseconds(
                getTimeInMicros().getTime()));
    }
};

}

