// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_metrics_entry.h"

namespace proton {

FieldMetricsEntry::FieldMetricsEntry(const std::string& name, const std::string& field_name, const std::string& description)
    : metrics::MetricSet(name, {{"field", field_name}}, description, nullptr),
      memoryUsage(this),
      disk_usage("disk_usage", {}, "Disk space usage (in bytes)", this)
{
}

FieldMetricsEntry::~FieldMetricsEntry() = default;

}
