// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * @class storage::StatusMetricConsumer
 * @ingroup common
 *
 * @brief Writes metrics to status page.
 */

#pragma once

#include "storagecomponent.h"
#include <vespa/storageframework/generic/status/statusreporter.h>
#include <vespa/storageframework/generic/metric/metricupdatehook.h>
#include <vespa/vespalib/util/document_runnable.h>
#include <vespa/metrics/metrics.h>
#include <map>

namespace vespalib {
    class StringTokenizer;
}

namespace storage {

namespace framework { class MemoryToken; }

class StatusMetricConsumer : public framework::StatusReporter,
                             private framework::MetricUpdateHook,
                             private vespalib::JsonStreamTypes
{
public:
    StatusMetricConsumer(
            StorageComponentRegister&,
            metrics::MetricManager&,
            const std::string& name = "status");
    ~StatusMetricConsumer() override;

    vespalib::string getReportContentType(const framework::HttpUrlPath&) const override;
    bool reportStatus(std::ostream& out, const framework::HttpUrlPath&) const override;

    void waitUntilTimeProcessed(framework::SecondTime t) const;

    void updateMetrics(const MetricLockGuard & guard) override;

private:
    typedef metrics::Metric Metric;
    typedef metrics::Metric::String String;

    metrics::MetricManager& _manager;
    StorageComponent        _component;
    std::string             _name;
    mutable std::mutex      _lock;
    framework::SecondTime   _startTime;
    framework::SecondTime   _processedTime;

    void writeXmlTags(std::ostream& out,
                      const vespalib::StringTokenizer& name,
                      std::vector<std::string>& xmlTags) const;

    void printHtmlMetricsReport(std::ostream& out,
                                const metrics::MetricSnapshot& data,
                                bool includeNotUsed) const;

    void printStorageHtmlReport(std::ostream& out,
                                std::map<String, Metric::SP>& usedMetrics,
                                const metrics::MetricSnapshot&) const;
    void printOperationHtmlReport(std::ostream& out,
                                  std::map<String, Metric::SP>& usedMetrics,
                                  const metrics::MetricSnapshot&) const;
    void printMaintOpHtmlReport(std::ostream& out,
                                std::map<String, Metric::SP>& usedMetrics,
                                const metrics::MetricSnapshot&) const;
    void printMergeHtmlReport(std::ostream& out,
                              std::map<String, Metric::SP>& usedMetrics,
                              const metrics::MetricSnapshot& snapshot) const;
    void printVisitHtmlReport(std::ostream& out,
                              std::map<String, Metric::SP>& usedMetrics,
                              const metrics::MetricSnapshot&) const;

};

} // storage

