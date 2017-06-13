// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "loadmetric.h"
#include "memoryconsumption.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace metrics {

template<typename MetricType>
LoadMetric<MetricType>::LoadMetric(const LoadTypeSet& loadTypes, const MetricType& metric, MetricSet* owner)
    : MetricSet(metric.getName(), "", metric.getDescription(), owner),
      _metrics(),
      _sum("sum", "loadsum sum", "Sum of all load metrics", this)
{
    _metrics.resize(loadTypes.size());
        // Currently, we only set tags and description on the metric set
        // itself, to cut down on size of output when downloading metrics,
        // and since matching tags of parent is just as good as matching
        // them specifically.
    setTags(metric.getTags());
    Tags noTags;
    for (uint32_t i=0; i<loadTypes.size(); ++i) {
        MetricTypeUP copy(dynamic_cast<MetricType*>(metric.clone(_ownerList, CLONE, 0, false)));
        assert(copy.get());
        copy->setName(loadTypes[i].getName());
        copy->setTags(noTags);
        registerMetric(*copy);
        _sum.addMetricToSum(*copy);
        _metrics[loadTypes[i].getId()] = std::move(copy);
    }
    _ownerList.shrink_to_fit();
}

template<typename MetricType>
LoadMetric<MetricType>::LoadMetric(const LoadMetric<MetricType>& other, MetricSet* owner)
    : MetricSet(other.getName(), "", other.getDescription(), owner),
      _metrics(),
      _sum("sum", "loadsum sum", "Sum of all load metrics", this)
{
    _metrics.resize(2 * other._metrics.size());
    setTags(other.getTags());
    Tags noTags;
    for (const auto & metric : other._metrics) {
        MetricTypeUP copy(dynamic_cast<MetricType*>(metric.second->clone(_ownerList, CLONE, 0, false)));
        assert(copy.get());
        copy->setName(metric.second->getName());
        copy->setTags(noTags);
        registerMetric(*copy);
        _sum.addMetricToSum(*copy);
        _metrics[metric.first] = std::move(copy);
    }
    _ownerList.shrink_to_fit();
}

template<typename MetricType>
LoadMetric<MetricType>::~LoadMetric() { }

template<typename MetricType>
MetricSet*
LoadMetric<MetricType>::clone(std::vector<Metric::UP> &ownerList,
                              CopyType copyType, MetricSet* owner,
                              bool includeUnused) const
{
    if (copyType == INACTIVE) {
        return MetricSet::clone(ownerList, INACTIVE, owner, includeUnused);
    }
    return new LoadMetric<MetricType>(*this, owner);
}

template<typename MetricType>
MetricType&
LoadMetric<MetricType>::getMetric(const LoadType& type) {
    MetricType* metric;

    typename vespalib::hash_map<uint32_t, MetricTypeUP>::iterator it(
            _metrics.find(type.getId()));
    if (it == _metrics.end()) {
        it = _metrics.find(0);
        assert(it != _metrics.end()); // Default should always exist
    }
    metric = it->second.get();
    assert(metric);

    return *metric;
}

template<typename MetricType>
void
LoadMetric<MetricType>::addMemoryUsage(MemoryConsumption& mc) const {
    ++mc._loadMetricCount;
    mc._loadMetricMeta += (sizeof(Metric::UP) * _ownerList.capacity())
                        + (sizeof(typename MetricMap::value_type) * _metrics.capacity());
    _sum.addMemoryUsage(mc);
    mc._loadMetricMeta += sizeof(LoadMetric<MetricType>)
                        - sizeof(MetricSet)
                        - sizeof(SumMetric<MetricType>);
    MetricSet::addMemoryUsage(mc);
}

}

