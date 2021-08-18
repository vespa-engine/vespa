// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "service_mapping.h"

namespace slobrok {

struct MappingMonitorListener {
    virtual void up(const ServiceMapping& mapping) = 0;
    virtual void down(const ServiceMapping& mapping) = 0;
protected:
    ~MappingMonitorListener() = default;
};

struct MappingMonitor {
    virtual void target(MappingMonitorListener *listener) = 0;
    virtual void start(const ServiceMapping& mapping) = 0;
    virtual void stop(const ServiceMapping& mapping) = 0;
protected:
    ~MappingMonitor() = default;
};

} // namespace slobrok

