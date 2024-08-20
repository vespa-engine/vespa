// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "memory_usage_metrics.h"
#include <map>

namespace proton {

class AttributeMetrics
{
public:
    struct Entry : public metrics::MetricSet {
        using SP = std::shared_ptr<Entry>;
        MemoryUsageMetrics memoryUsage;
        Entry(const std::string &attrName);
    };
private:
    using Map = std::map<std::string, Entry::SP>;

    metrics::MetricSet *_parent;
    Map _attributes;

public:
    AttributeMetrics(metrics::MetricSet *parent);
    Entry::SP add(const std::string &attrName);
    Entry::SP get(const std::string &attrName) const;
    Entry::SP remove(const std::string &attrName);
    std::vector<Entry::SP> release();
    metrics::MetricSet *parent() { return _parent; }
};

} // namespace proton
