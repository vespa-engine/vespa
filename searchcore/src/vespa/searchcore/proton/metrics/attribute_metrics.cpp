// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_metrics.h"

namespace proton {

using Entry = AttributeMetrics::Entry;

AttributeMetrics::Entry::Entry(const vespalib::string &attrName)
    : metrics::MetricSet("attribute", {{"field", attrName}}, "Metrics for a given attribute vector", nullptr),
      memoryUsage(this)
{
}

AttributeMetrics::AttributeMetrics(metrics::MetricSet *parent)
    : _parent(parent),
      _attributes()
{
}

Entry::SP
AttributeMetrics::add(const vespalib::string &attrName)
{
    if (get(attrName).get() != nullptr) {
        return Entry::SP();
    }
    Entry::SP result = std::make_shared<Entry>(attrName);
    _attributes.insert(std::make_pair(attrName, result));
    return result;
}

Entry::SP
AttributeMetrics::get(const vespalib::string &attrName) const
{
    auto itr = _attributes.find(attrName);
    if (itr != _attributes.end()) {
        return itr->second;
    }
    return Entry::SP();
}

Entry::SP
AttributeMetrics::remove(const vespalib::string &attrName)
{
    Entry::SP result = get(attrName);
    if (result.get() != nullptr) {
        _attributes.erase(attrName);
    }
    return result;
}

std::vector<Entry::SP>
AttributeMetrics::release()
{
    std::vector<Entry::SP> result;
    for (const auto &attr : _attributes) {
        result.push_back(attr.second);
    }
    _attributes.clear();
    return result;
}

} // namespace proton
