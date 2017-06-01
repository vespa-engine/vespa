// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "legacy_attribute_metrics.h"

namespace proton {

LegacyAttributeMetrics::List::Entry::Entry(const std::string &name)
    : metrics::MetricSet(name, "", "Attribute vector metrics", 0),
      memoryUsage("memoryusage", "", "Memory usage", this),
      bitVectors("bitvectors", "", "Number of bitvectors", this)
{
}

LegacyAttributeMetrics::List::Entry *
LegacyAttributeMetrics::List::add(const std::string &name)
{
    if (metrics.find(name) != metrics.end()) {
        return nullptr;
    }
    auto &pos = metrics[name];
    pos = std::make_unique<Entry>(name);
    return pos.get();
}

LegacyAttributeMetrics::List::Entry *
LegacyAttributeMetrics::List::get(const std::string &name) const
{
    const auto pos = metrics.find(name);
    if (pos == metrics.end()) {
        return nullptr;
    }
    return pos->second.get();
}

LegacyAttributeMetrics::List::Entry::UP
LegacyAttributeMetrics::List::remove(const std::string &name)
{
    auto pos = metrics.find(name);
    if (pos == metrics.end()) {
        return Entry::UP();
    }
    Entry::UP retval = std::move(pos->second);
    metrics.erase(name);
    return retval;
}

std::vector<LegacyAttributeMetrics::List::Entry::UP>
LegacyAttributeMetrics::List::release()
{
    std::vector<Entry::UP> entries;
    for (auto &pos: metrics) {
        entries.push_back(std::move(pos.second));
    }
    metrics.clear();
    return entries;
}

LegacyAttributeMetrics::List::List(metrics::MetricSet *parent)
    : metrics::MetricSet("list", "", "Metrics per attribute vector", parent),
      metrics()
{
}

LegacyAttributeMetrics::List::~List() {}

LegacyAttributeMetrics::LegacyAttributeMetrics(metrics::MetricSet *parent)
    : metrics::MetricSet("attributes", "", "Attribute metrics", parent),
      list(this),
      memoryUsage("memoryusage", "", "Memory usage for attributes", this),
      bitVectors("bitvectors", "", "Number of bitvectors for attributes", this)
{
}

LegacyAttributeMetrics::~LegacyAttributeMetrics() {}

} // namespace proton
