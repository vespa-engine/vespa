// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <mutex>

namespace metrics {

class MetricManager;

class UpdateHook {
    const char* _name;
    time_t      _nextCall;
    uint32_t    _period;
    friend class MetricManager;

public:
    using UP = std::unique_ptr<UpdateHook>;
    using MetricLockGuard = std::unique_lock<std::mutex>;

    UpdateHook(const char* name) : _name(name), _nextCall(0), _period(0) {}
    virtual ~UpdateHook() = default;
    virtual void updateMetrics(const MetricLockGuard & guard) = 0;
    const char* getName() const { return _name; }
};

}
