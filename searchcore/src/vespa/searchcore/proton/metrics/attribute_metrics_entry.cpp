// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_metrics_entry.h"

namespace proton {

namespace {

const std::string entry_name("attribute");
const std::string entry_description("Metrics for a given attribute vector");

}

AttributeMetricsEntry::AttributeMetricsEntry(const std::string& field_name)
    : FieldMetricsEntry(entry_name, field_name, entry_description)
{
}

AttributeMetricsEntry::~AttributeMetricsEntry() = default;

} // namespace proton
