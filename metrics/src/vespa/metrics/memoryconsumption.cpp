// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "memoryconsumption.h"
#include <vespa/vespalib/stllike/hash_set.hpp>
#include <vespa/vespalib/util/size_literals.h>
#include <sstream>

namespace metrics {

struct SeenStrings : public vespalib::hash_set<const char*> { };
struct SnapShotUsage : public std::vector<std::pair<std::string, uint32_t> > { };

MemoryConsumption::MemoryConsumption()
    : _seenStrings(std::make_unique<SeenStrings>()),
      _snapShotUsage(std::make_unique<SnapShotUsage>())
{
    memset(&_consumerCount, 0, reinterpret_cast<size_t>(&_seenStrings) - reinterpret_cast<size_t>(&_consumerCount));
    _seenStrings->resize(1000);
}

MemoryConsumption::~MemoryConsumption() = default;

uint32_t
MemoryConsumption::getStringMemoryUsage(const std::string& s, uint32_t& uniqueCount) {
    ++_totalStringCount;
    const char* internalString = s.c_str();
    if (_seenStrings->find(internalString) != _seenStrings->end()) {
        return 0;
    }
    ++uniqueCount;
    _seenStrings->insert(internalString);
    return s.capacity();
}


uint32_t
MemoryConsumption::getStringMemoryUsage(const vespalib::string& s, uint32_t& uniqueCount) {
    ++_totalStringCount;
    const char* internalString = s.c_str();
    if (_seenStrings->find(internalString) != _seenStrings->end()) {
        return 0;
    }
    ++uniqueCount;
    _seenStrings->insert(internalString);
    const void *p = &s;
    if ((p <= internalString) && (internalString - sizeof(vespalib::string) < p)) {
        // no extra space allocated outside object
        return 0;
    }
    return s.capacity();
}

void
MemoryConsumption::addSnapShotUsage(const std::string& name, uint32_t usage) {
    _snapShotUsage->push_back(std::pair<std::string, uint32_t>(name, usage));
}

uint32_t
MemoryConsumption::getTotalMemoryUsage() const {
    return _consumerId + _consumerMetricIds + _consumerMeta
            + _snapshotSetMeta + _snapshotName + _snapshotMeta + _metricMeta
            + _metricName + _metricPath + _metricDescription
            + _metricTags + _metricSetMeta + _nameHash + _nameHashStrings
            + _metricSetOrder + _countMetricValues + _countMetricMeta
            + _valueMetricValues + _valueMetricMeta + _sumMetricMeta
            + _sumMetricParentPath + _loadMetricMeta;
}

void
MemoryConsumption::print(std::ostream& out, bool verbose,
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
        << newl << "Unique string count: " << _seenStrings->size()
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
    for (const auto & entry : *_snapShotUsage) {
        out << newl << "Snapshot " << entry.first << ": " << bval(entry.second);
    }
    out << "\n" << indent << ")";
}

std::string
MemoryConsumption::bval(uint32_t bytes) {
    std::ostringstream ost;
    if (bytes < 10_Ki) {
        ost << bytes << " B";
    } else if (bytes < 10_Mi) {
        ost << (bytes / 1_Ki) << " kB";
    } else {
        ost << (bytes / 1_Mi) << " MB";
    }
    return ost.str();
}

} // metrics

