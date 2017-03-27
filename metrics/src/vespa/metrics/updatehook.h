// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/sync.h>

namespace metrics {

class MetricManager;

class UpdateHook {
    const char* _name;
    time_t _nextCall;
    uint32_t _period;
    friend class MetricManager;

public:
    using UP = std::unique_ptr<UpdateHook>;
    using MetricLockGuard = vespalib::MonitorGuard;

    UpdateHook(const char* name) : _name(name), _nextCall(0), _period(0) {}
    virtual ~UpdateHook() {}
    virtual void updateMetrics(const MetricLockGuard & guard) = 0;
    const char* getName() const { return _name; }
};

}
