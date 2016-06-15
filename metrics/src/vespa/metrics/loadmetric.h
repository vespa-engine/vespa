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

#include <vespa/metrics/metricset.h>
#include <vespa/metrics/summetric.h>
#include <vespa/vespalib/util/linkedptr.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/stllike/hash_map.h>

namespace metrics {

class MetricSet;

class LoadType {
public:
    typedef vespalib::string string;
    LoadType(uint32_t id, const string& name) : _id(id), _name(name) {}

    uint32_t getId() const { return _id; }
    const string& getName() const { return _name; }

    string toString() const {
        return vespalib::make_string("%s(%u)", _name.c_str(), _id);
    }
private:
    uint32_t _id;
    string _name;
};

typedef std::vector<LoadType> LoadTypeSet;

template<typename MetricType>
class LoadMetric : public metrics::MetricSet {
    std::vector<metrics::Metric::LP> _ownerList;
    typedef vespalib::LinkedPtr<MetricType> MetricTypeLP;
    vespalib::hash_map<uint32_t, MetricTypeLP> _metrics;
    metrics::SumMetric<MetricType> _sum;

public:
    /**
     * Create a load metric using the given metric as a template to how they
     * shuold look. They will get prefix names based on load types existing.
     */
    LoadMetric(const LoadTypeSet& loadTypes, const MetricType& metric,
               metrics::MetricSet* owner = 0)
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
            MetricTypeLP copy(
                    dynamic_cast<MetricType*>(
                        metric.clone(_ownerList, CLONE, 0, false)));
            assert(copy.get());
            copy->setName(loadTypes[i].getName());
            copy->setTags(noTags);
            _metrics[loadTypes[i].getId()] = copy;
            registerMetric(*copy);
            _sum.addMetricToSum(*copy);
        }
        metrics::trim(_ownerList);
    }

    /**
     * A load metric implements a copy constructor and a clone functions that
     * clone content, and resetting name/tags/description, just so metric
     * implementors can implement clone() by using regular construction and
     * then assign the values to the new set. (Without screwing up copying as
     * the load metric alters this data in supplied metric)
     */
    LoadMetric(const LoadMetric<MetricType>& other, metrics::MetricSet* owner)
        : MetricSet(other.getName(), "", other.getDescription(), owner),
          _metrics(),
          _sum("sum", "loadsum sum", "Sum of all load metrics", this)
    {
        _metrics.resize(2 * other._metrics.size());
        setTags(other.getTags());
        Tags noTags;
        for (typename vespalib::hash_map<uint32_t, MetricTypeLP>::const_iterator
                it = other._metrics.begin(); it != other._metrics.end(); ++it)
        {
            MetricTypeLP copy(dynamic_cast<MetricType*>(
                        it->second->clone(_ownerList, CLONE, 0, false)));
            assert(copy.get());
            copy->setName(it->second->getName());
            copy->setTags(noTags);
            _metrics[it->first] = copy;
            registerMetric(*copy);
            _sum.addMetricToSum(*copy);
        }
        metrics::trim(_ownerList);
    }

    virtual Metric* clone(std::vector<Metric::LP>& ownerList,
                          CopyType copyType, MetricSet* owner,
                          bool includeUnused = false) const
    {
        if (copyType == INACTIVE) {
            return MetricSet::clone(ownerList, INACTIVE, owner, includeUnused);
        }
        return new LoadMetric<MetricType>(*this, owner);
    }

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
            metric = it->second.get();
            assert(metric);
        } else {
            metric = it->second.get();
            assert(metric);
        }

        return *metric;
    }

    virtual void addMemoryUsage(metrics::MemoryConsumption& mc) const {
        ++mc._loadMetricCount;
        mc._loadMetricMeta += sizeof(metrics::Metric::LP) * _ownerList.size();
        for (typename vespalib::hash_map<uint32_t, MetricTypeLP>::const_iterator
                it = _metrics.begin(); it != _metrics.end(); ++it)
        {
            mc._loadMetricMeta += sizeof(uint32_t) + sizeof(MetricTypeLP);
        }
        _sum.addMemoryUsage(mc);
        mc._loadMetricMeta += sizeof(LoadMetric<MetricType>)
                            - sizeof(metrics::MetricSet)
                            - sizeof(metrics::SumMetric<MetricType>);
        metrics::MetricSet::addMemoryUsage(mc);
    }

};

} // documentapi

