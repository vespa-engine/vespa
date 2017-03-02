// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "legacy_attribute_metrics.h"

namespace proton {

LegacyAttributeMetrics::List::Entry::Entry(const std::string &name)
    : metrics::MetricSet(name, "", "Attribute vector metrics", 0),
      memoryUsage("memoryusage", "", "Memory usage", this),
      bitVectors("bitvectors", "", "Number of bitvectors", this)
{
}

LegacyAttributeMetrics::List::Entry::LP
LegacyAttributeMetrics::List::add(const std::string &name)
{
    if (metrics.find(name) != metrics.end()) {
        return Entry::LP(0);
    }
    Entry::LP entry(new Entry(name));
    metrics[name] = entry;
    return entry;
}

LegacyAttributeMetrics::List::Entry::LP
LegacyAttributeMetrics::List::get(const std::string &name) const
{
    std::map<std::string, Entry::LP>::const_iterator pos = metrics.find(name);
    if (pos == metrics.end()) {
        return Entry::LP(0);
    }
    return pos->second;
}

LegacyAttributeMetrics::List::Entry::LP
LegacyAttributeMetrics::List::remove(const std::string &name)
{
    std::map<std::string, Entry::LP>::const_iterator pos = metrics.find(name);
    if (pos == metrics.end()) {
        return Entry::LP(0);
    }
    Entry::LP retval = pos->second;
    metrics.erase(name);
    return retval;
}

std::vector<LegacyAttributeMetrics::List::Entry::LP>
LegacyAttributeMetrics::List::release()
{
    std::vector<Entry::LP> entries;
    std::map<std::string, Entry::LP>::const_iterator pos = metrics.begin();
    for (; pos != metrics.end(); ++pos) {
        entries.push_back(pos->second);
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
