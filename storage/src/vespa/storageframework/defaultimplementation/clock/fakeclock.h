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
    vespalib::duration      _absoluteTime;
    mutable time_t          _cycleCount;
    mutable std::mutex      _lock;

public:
    explicit FakeClock(Mode m = FAKE_ABSOLUTE, vespalib::duration startTime = 1us);

    void setMode(Mode m) {
        std::lock_guard guard(_lock);
        _mode = m;
    }
    virtual void setFakeCycleMode() { setMode(FAKE_ABSOLUTE_CYCLE); }

    virtual void setAbsoluteTimeInSeconds(uint32_t seconds) {
        std::lock_guard guard(_lock);
        _absoluteTime = std::chrono::seconds(seconds);
        _cycleCount = 0;
        _mode = FAKE_ABSOLUTE;
    }

    virtual void setAbsoluteTimeInMicroSeconds(uint64_t usecs) {
        std::lock_guard guard(_lock);
        _absoluteTime = std::chrono::microseconds(usecs);
        _cycleCount = 0;
        _mode = FAKE_ABSOLUTE;
    }

    virtual void addMilliSecondsToTime(uint64_t ms) {
        std::lock_guard guard(_lock);
        _absoluteTime += std::chrono::milliseconds(ms);
    }

    virtual void addSecondsToTime(uint32_t nr) {
        std::lock_guard guard(_lock);
        _absoluteTime += std::chrono::seconds(nr);
    }

    int64_t getTimeInMicros() const;

    vespalib::system_time getSystemTime() const override {
        // For simplicity, assume fake monotonic time follows fake wall clock.
        return vespalib::system_time(std::chrono::microseconds(getTimeInMicros()));
    }
    vespalib::steady_time getMonotonicTime() const override {
        // For simplicity, assume fake monotonic time follows fake wall clock.
        return vespalib::steady_time(std::chrono::microseconds(getTimeInMicros()));
    }
};

}

