// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "memory_usage_metrics.h"
#include <map>

namespace proton {

/*
 * Class containing metrics for an aspect (attribute or index) of multiple fields.
 */
template <typename Entry>
class FieldMetrics
{
    using Map = std::map<std::string, std::shared_ptr<Entry>>;

    metrics::MetricSet *_parent;
    Map _fields;

public:
    FieldMetrics(metrics::MetricSet* parent);
    ~FieldMetrics();
    std::shared_ptr<Entry> add(const std::string& field_name);
    std::shared_ptr<Entry> get(const std::string& field_name) const;
    std::shared_ptr<Entry> remove(const std::string& field_name);
    std::vector<std::shared_ptr<Entry>> release();
    metrics::MetricSet* parent() noexcept { return _parent; }
};

} // namespace proton
