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
    void set_fields(std::vector<std::string> field_names);
    std::shared_ptr<Entry> get_field_metrics_entry(const std::string& field_name) const;
    metrics::MetricSet* parent() noexcept { return _parent; }
};

} // namespace proton
