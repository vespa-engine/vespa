// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "index_metrics_entry.h"

namespace proton {

namespace {

const std::string entry_name("index_field");
const std::string entry_description("Metrics for indexes for a given field");

}

IndexMetricsEntry::IndexMetricsEntry(const std::string& field_name)
    : FieldMetricsEntry(entry_name, field_name, entry_description),
      _disk_usage(this)
{
}

IndexMetricsEntry::~IndexMetricsEntry() = default;

} // namespace proton
