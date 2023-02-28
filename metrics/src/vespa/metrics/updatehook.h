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
    const char* _name;
    time_t      _nextCall;
    uint32_t    _period;
    friend class MetricManager;

public:
    using MetricLockGuard = metrics::MetricLockGuard;
    UpdateHook(const char* name) : _name(name), _nextCall(0), _period(0) {}
    virtual ~UpdateHook() = default;
    virtual void updateMetrics(const MetricLockGuard & guard) = 0;
    const char* getName() const { return _name; }
};

}
