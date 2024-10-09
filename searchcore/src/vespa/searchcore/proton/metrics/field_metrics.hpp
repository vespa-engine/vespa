// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_metrics.h"

namespace proton {

template <typename Entry>
FieldMetrics<Entry>::FieldMetrics(metrics::MetricSet* parent)
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
FieldMetrics<Entry>::add(const std::string& field_name)
{
    if (get(field_name)) {
        return {};
    }
    auto result = std::make_shared<Entry>(field_name);
    _fields.insert(std::make_pair(field_name, result));
    return result;
}

template <typename Entry>
std::shared_ptr<Entry>
FieldMetrics<Entry>::get(const std::string& field_name) const
{
    auto itr = _fields.find(field_name);
    if (itr != _fields.end()) {
        return itr->second;
    }
    return {};
}

template <typename Entry>
std::shared_ptr<Entry>
FieldMetrics<Entry>::remove(const std::string& field_name)
{
    auto result = get(field_name);
    if (result) {
        _fields.erase(field_name);
    }
    return result;
}

template <typename Entry>
std::vector<std::shared_ptr<Entry>>
FieldMetrics<Entry>::release()
{
    std::vector<std::shared_ptr<Entry>> result;
    for (const auto& field : _fields) {
        result.push_back(field.second);
    }
    _fields.clear();
    return result;
}

} // namespace proton
