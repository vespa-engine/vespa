// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class metrics::MemoryConsumption
 * \ingroup metrics
 *
 * \brief An instance of this class describes memory usage for a set of metrics.
 *
 * Typically, you ask the memory manager for memory consumption of all metrics.
 * An instance of this class is created and sent through all metrics to track
 * memory consumption. Tracking may be a bit expensive, so this shouldn't be
 * checked too often. Primary use is to detect what parts actually use the most
 * memory. Secondary use would be to add it as a metric periodically updated.
 *
 * The memory consumption object keeps track of various groups of memory users,
 * such as to give a good overview of where the memory is used.
 */

#pragma once

#include <vespa/vespalib/util/printable.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <sstream>

namespace metrics {

struct MemoryConsumption : public vespalib::Printable {
    typedef std::unique_ptr<MemoryConsumption> UP;

    uint32_t _consumerCount;
    uint32_t _consumerId;
    uint32_t _consumerIdUnique;
    uint32_t _consumerMetricsInTotal;
    uint32_t _consumerMetricIds;
    uint32_t _consumerMetricIdsUnique;
    uint32_t _consumerMeta;

    uint32_t _snapshotSetCount;
    uint32_t _snapshotSetMeta;

    uint32_t _nameHash;
    uint32_t _nameHashStrings;
    uint32_t _nameHashUnique;

    uint32_t _snapshotCount;
    uint32_t _snapshotName;
    uint32_t _snapshotNameUnique;
    uint32_t _snapshotMeta;

    uint32_t _metricCount;
    uint32_t _metricMeta;
    uint32_t _metricName;
    uint32_t _metricNameUnique;
    uint32_t _metricPath;
    uint32_t _metricPathUnique;
    uint32_t _metricDescription;
    uint32_t _metricDescriptionUnique;
    uint32_t _metricTagCount;
    uint32_t _metricTags;
    uint32_t _metricTagsUnique;

    uint32_t _metricSetCount;
    uint32_t _metricSetMeta;
    uint32_t _metricSetOrder;

    uint32_t _countMetricCount;
    uint32_t _countMetricValues;
    uint32_t _countMetricMeta;

    uint32_t _valueMetricCount;
    uint32_t _valueMetricValues;
    uint32_t _valueMetricMeta;

    uint32_t _sumMetricCount;
    uint32_t _sumMetricMeta;
    uint32_t _sumMetricParentPath;
    uint32_t _sumMetricParentPathUnique;

    uint32_t _loadMetricCount;
    uint32_t _loadMetricMeta;

    uint32_t _totalStringCount;

    vespalib::hash_set<const void*> _seenStrings;
    std::vector<std::pair<std::string, uint32_t> > _snapShotUsage;

    MemoryConsumption() {
        memset(&_consumerCount, 0,
               reinterpret_cast<size_t>(&_seenStrings)
               - reinterpret_cast<size_t>(&_consumerCount));
        _seenStrings.resize(1000);
    }

    /** Get memory usage of a string that is not included when doing sizeof */
    uint32_t getStringMemoryUsage(const std::string& s, uint32_t& uniqueCount) {
        ++_totalStringCount;
        const char* internalString = s.c_str();
        if (_seenStrings.find(internalString) != _seenStrings.end()) {
            return 0;
        }
        ++uniqueCount;
        _seenStrings.insert(internalString);
        return s.capacity();
    }

    void addSnapShotUsage(const std::string& name, uint32_t usage) {
        _snapShotUsage.push_back(std::pair<std::string, uint32_t>(name, usage));
    }

    uint32_t getTotalMemoryUsage() const {
        return _consumerId + _consumerMetricIds + _consumerMeta
                + _snapshotSetMeta + _snapshotName + _snapshotMeta + _metricMeta
                + _metricName + _metricPath + _metricDescription
                + _metricTags + _metricSetMeta + _nameHash + _nameHashStrings
                + _metricSetOrder + _countMetricValues + _countMetricMeta
                + _valueMetricValues + _valueMetricMeta + _sumMetricMeta
                + _sumMetricParentPath + _loadMetricMeta;
    }

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const
    {
        (void) verbose;
        std::string newl = "\n" + indent + "  ";
        out << "MemoryConsumption("
            << newl << "Total memory used: " << bval(getTotalMemoryUsage())
            << newl << "Consumer count: " << _consumerCount
            << newl << "Consumer ids: " << bval(_consumerId)
            << newl << "Consumer metric count: " << _consumerMetricsInTotal
            << newl << "Consumer metric ids: " << bval(_consumerMetricIds)
            << newl << "Consumer meta: " << bval(_consumerMeta)
            << newl << "Name hash: " << bval(_nameHash)
            << newl << "Name hash strings: " << bval(_nameHashStrings)
            << newl << "Snapshot set count: " << _snapshotSetCount
            << newl << "Snapshot set meta: " << bval(_snapshotSetMeta)
            << newl << "Snapshot count: " << _snapshotCount
            << newl << "Snapshot name: " << bval(_snapshotName)
            << newl << "Snapshot meta: " << bval(_snapshotMeta)
            << newl << "Metric count: " << _metricCount
            << newl << "Metric meta: " << bval(_metricMeta)
            << newl << "Metric names: " << bval(_metricName)
            << newl << "Metric paths: " << bval(_metricPath)
            << newl << "Metric descriptions: " << bval(_metricDescription)
            << newl << "Metric tag count: " << _metricTagCount
            << newl << "Metric tags: " << bval(_metricTags)
            << newl << "Metric set count: " << _metricSetCount
            << newl << "Metric set meta: " << bval(_metricSetMeta)
            << newl << "Metric set order list: " << bval(_metricSetOrder)
            << newl << "Count metric count: " << _countMetricCount
            << newl << "Count metric values: " << bval(_countMetricValues)
            << newl << "Count metric meta: " << bval(_countMetricMeta)
            << newl << "Value metric count: " << _valueMetricCount
            << newl << "Value metric values: " << bval(_valueMetricValues)
            << newl << "Value metric meta: " << bval(_valueMetricMeta)
            << newl << "Sum metric count: " << _sumMetricCount
            << newl << "Sum metric meta: " << bval(_sumMetricMeta)
            << newl << "Sum metric parent path: " << bval(_sumMetricParentPath)
            << newl << "Load metric count: " << _loadMetricCount
            << newl << "Load metric meta: " << bval(_loadMetricMeta)
            << newl << "Unique string count: " << _seenStrings.size()
            << newl << "Strings stored: " << _totalStringCount
            << newl << "Unique consumer ids: " << _consumerIdUnique
            << newl << "Unique cons metric ids: " << _consumerMetricIdsUnique
            << newl << "Unique snapshot names: " << _snapshotNameUnique
            << newl << "Unique metric names: " << _metricNameUnique
            << newl << "Unique metric paths: " << _metricPathUnique
            << newl << "Unique metric descs: " << _metricDescriptionUnique
            << newl << "Unique metric tags: " << _metricTagsUnique
            << newl << "Unique sum metric paths: " << _sumMetricParentPathUnique
            << newl << "Unique name hash strings: " << _nameHashUnique;
        for (uint32_t i=0; i<_snapShotUsage.size(); ++i) {
            out << newl << "Snapshot " << _snapShotUsage[i].first << ": "
                << bval(_snapShotUsage[i].second);
        }
        out << "\n" << indent << ")";
    }

    static std::string bval(uint32_t bytes) {
        std::ostringstream ost;
        if (bytes < 10 * 1024) {
            ost << bytes << " B";
        } else if (bytes < 10 * 1024 * 1024) {
            ost << (bytes / 1024) << " kB";
        } else {
            ost << (bytes / (1024 * 1024)) << " MB";
        }
        return ost.str();
    }

};

} // metrics

