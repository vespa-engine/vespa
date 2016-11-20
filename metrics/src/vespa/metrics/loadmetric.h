// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \file loadmetric.h
 * \ingroup metrics
 *
 * \brief Utility class for creating metrics for all load types.
 *
 * To better see how different load types behave in the system we want to log
 * separate metrics for various loadtypes. To make it easy to create and use
 * such metrics, this class is a wrapper class that sets up one metric per load
 * type.
 *
 * In order to make it easy to use load metrics, they are templated on the type,
 * such that you get the correct type out of operator[]. Load metric needs to
 * clone metrics on creation though, so if you want load metrics of a metric set
 * you need to properly implement clone() for that set.
 */

#pragma once

#include "metricset.h"
#include "summetric.h"
#include <vespa/vespalib/stllike/hash_map.h>

namespace metrics {

class MetricSet;

class LoadType {
public:
    using string = vespalib::string;
    LoadType(uint32_t id, const string& name) : _id(id), _name(name) {}

    uint32_t getId() const { return _id; }
    const string& getName() const { return _name; }

    string toString() const;
private:
    uint32_t _id;
    string _name;
};

typedef std::vector<LoadType> LoadTypeSet;

template<typename MetricType>
class LoadMetric : public MetricSet {
    std::vector<Metric::LP> _ownerList;
    typedef vespalib::LinkedPtr<MetricType> MetricTypeLP;
    vespalib::hash_map<uint32_t, MetricTypeLP> _metrics;
    SumMetric<MetricType> _sum;

public:
    /**
     * Create a load metric using the given metric as a template to how they
     * shuold look. They will get prefix names based on load types existing.
     */
    LoadMetric(const LoadTypeSet& loadTypes, const MetricType& metric, MetricSet* owner = 0);

    /**
     * A load metric implements a copy constructor and a clone functions that
     * clone content, and resetting name/tags/description, just so metric
     * implementors can implement clone() by using regular construction and
     * then assign the values to the new set. (Without screwing up copying as
     * the load metric alters this data in supplied metric)
     */
    LoadMetric(const LoadMetric<MetricType>& other, MetricSet* owner);
    ~LoadMetric();
    MetricSet* clone(std::vector<Metric::LP>& ownerList,
                  CopyType copyType, MetricSet* owner,
                  bool includeUnused = false) const override;

    MetricType& operator[](const LoadType& type) { return getMetric(type); }
    const MetricType& operator[](const LoadType& type) const
        { return const_cast<LoadMetric<MetricType>*>(this)->getMetric(type); }
    MetricType& getMetric(const LoadType& type) {
        MetricType* metric;

        typename vespalib::hash_map<uint32_t, MetricTypeLP>::iterator it(
                _metrics.find(type.getId()));
        if (it == _metrics.end()) {
            it = _metrics.find(0);
            assert(it != _metrics.end()); // Default should always exist
        }
        metric = it->second.get();
        assert(metric);

        return *metric;
    }

    void addMemoryUsage(MemoryConsumption& mc) const override;
};

} // documentapi

