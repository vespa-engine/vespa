// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_metrics.h"
#include <algorithm>
#include <cassert>

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
void
FieldMetrics<Entry>::set_fields(std::vector<std::string> field_names)
{
    std::sort(field_names.begin(), field_names.end());
    auto itr = _fields.begin();
    for (auto& field_name : field_names) {
        while (itr != _fields.end() && itr->first < field_name) {
            _parent->unregisterMetric(*itr->second);
            itr = _fields.erase(itr);
        }
        if (itr == _fields.end() || itr->first > field_name) {
            auto entry = std::make_shared<Entry>(field_name);
            auto ins_res = _fields.insert(std::make_pair(field_name, entry));
            assert(ins_res.second);
            itr = ins_res.first;
            _parent->registerMetric(*entry);
        }
        ++itr;
    }
    while (itr != _fields.end()) {
        _parent->unregisterMetric(*itr->second);
        itr = _fields.erase(itr);
    }
}

template <typename Entry>
std::shared_ptr<Entry>
FieldMetrics<Entry>::get_field_metrics_entry(const std::string& field_name) const
{
    auto itr = _fields.find(field_name);
    if (itr != _fields.end()) {
        return itr->second;
    }
    return {};
}

} // namespace proton
