// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_metrics_entry.h"

namespace proton {

FieldMetricsEntry::FieldMetricsEntry(const std::string& name, const std::string& field_name, const std::string& description)
    : metrics::MetricSet(name, {{"field", field_name}}, description, nullptr),
      memoryUsage(this),
      size_on_disk("size_on_disk", {}, "Size on disk (bytes)", this)
{
}

FieldMetricsEntry::~FieldMetricsEntry() = default;

} // namespace proton
