// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_metrics.h"

namespace proton {

template <typename Entry>
FieldMetrics<Entry>::FieldMetrics(metrics::MetricSet *parent)
    : _parent(parent),
      _fields()
{
}

template <typename Entry>
FieldMetrics<Entry>::~FieldMetrics()
{
}

template <typename Entry>
std::shared_ptr<Entry>
FieldMetrics<Entry>::add(const std::string &attrName)
{
    if (get(attrName)) {
        return {};
    }
    auto result = std::make_shared<Entry>(attrName);
    _fields.insert(std::make_pair(attrName, result));
    return result;
}

template <typename Entry>
std::shared_ptr<Entry>
FieldMetrics<Entry>::get(const std::string &attrName) const
{
    auto itr = _fields.find(attrName);
    if (itr != _fields.end()) {
        return itr->second;
    }
    return {};
}

template <typename Entry>
std::shared_ptr<Entry>
FieldMetrics<Entry>::remove(const std::string &attrName)
{
    auto result = get(attrName);
    if (result) {
        _fields.erase(attrName);
    }
    return result;
}

template <typename Entry>
std::vector<std::shared_ptr<Entry>>
FieldMetrics<Entry>::release()
{
    std::vector<std::shared_ptr<Entry>> result;
    for (const auto &attr : _fields) {
        result.push_back(attr.second);
    }
    _fields.clear();
    return result;
}

} // namespace proton
