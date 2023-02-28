// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/time.h>
#include <mutex>

namespace metrics {

using time_point = vespalib::system_time;

class MetricLockGuard {
public:
    MetricLockGuard(std::mutex & mutex);
    MetricLockGuard(const MetricLockGuard &) = delete;
    MetricLockGuard & operator =(const MetricLockGuard &) = delete;
    MetricLockGuard(MetricLockGuard &&) = default;
    MetricLockGuard & operator =(MetricLockGuard &&) = default;
    ~MetricLockGuard();

    bool owns(const std::mutex &) const;
    operator std::unique_lock<std::mutex> & () { return _guard; }
private:
    std::unique_lock<std::mutex> _guard;
};

class MetricManager;

class UpdateHook {
public:
    using MetricLockGuard = metrics::MetricLockGuard;
    UpdateHook(const char* name) : _name(name), _nextCall(), _period(vespalib::duration::zero()) {}
    virtual ~UpdateHook() = default;
    virtual void updateMetrics(const MetricLockGuard & guard) = 0;
    const char* getName() const { return _name; }
    void updateNextCall() { updateNextCall(_nextCall); }
    void updateNextCall(time_point now) { _nextCall = now + _period; }
    bool is_periodic() const noexcept { return _period == vespalib::duration::zero(); }
    bool expired(time_point now) { return _nextCall <= now; }
    bool has_valid_expiry() const noexcept { return _nextCall != time_point(); }
    vespalib::duration getPeriod() const noexcept { return _period; }
    time_point getNextCall() const noexcept { return _nextCall; }
    // This should be moved to constructor
    void setPeriod(vespalib::duration period) { _period = period; }
    void setNextCall(time_point now) { _nextCall = now; }
private:
    const char*         _name;
    time_point          _nextCall;
    vespalib::duration  _period;
};

}
