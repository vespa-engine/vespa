// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "service_mapping.h"
#include <memory>
#include <functional>

namespace slobrok {

struct MappingMonitorOwner {
    virtual void up(const ServiceMapping& mapping) = 0;
    virtual void down(const ServiceMapping& mapping) = 0;
protected:
    virtual ~MappingMonitorOwner() = default;
};

struct MappingMonitor {
    using UP = std::unique_ptr<MappingMonitor>;
    virtual void start(const ServiceMapping& mapping, bool hurry) = 0;
    virtual void stop(const ServiceMapping& mapping) = 0;
    virtual ~MappingMonitor() = default;
};

using MappingMonitorFactory = std::function<MappingMonitor::UP(MappingMonitorOwner &)>;

} // namespace slobrok

