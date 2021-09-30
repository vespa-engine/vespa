// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
#include <memory>

namespace metrics {

struct SeenStrings;
struct SnapShotUsage;

class MemoryConsumption : public vespalib::Printable {
public:
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

    MemoryConsumption();
    ~MemoryConsumption();

    /** Get memory usage of a string that is not included when doing sizeof */
    uint32_t getStringMemoryUsage(const std::string& s, uint32_t& uniqueCount);
    uint32_t getStringMemoryUsage(const vespalib::string& s, uint32_t& uniqueCount);
    void addSnapShotUsage(const std::string& name, uint32_t usage);

    uint32_t getTotalMemoryUsage() const;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    static std::string bval(uint32_t bytes);
private:
    std::unique_ptr<SeenStrings>   _seenStrings;
    std::unique_ptr<SnapShotUsage> _snapShotUsage;
};

} // metrics

