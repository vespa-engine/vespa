// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "index_metrics_entry.h"
#include <vespa/searchlib/util/field_index_io_stats.h>

namespace proton {

namespace {

const std::string entry_name("index");
const std::string entry_description("Metrics for indexes for a given field");

}

IndexMetricsEntry::IndexMetricsEntry(const std::string& field_name)
    : FieldMetricsEntry(entry_name, field_name, entry_description),
      _disk_io(this)
{
}

IndexMetricsEntry::~IndexMetricsEntry() = default;

} // namespace proton
