// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#include <vespa/metrics/metricmanager.h>
#include <map>

namespace vespalib { class StringTokenizer; }
namespace metrics { class MetricManager; }

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

    // Metric reporting requires the "vespa.content.metrics_api" capability
    CapabilitySet required_capabilities() const noexcept override {
        return CapabilitySet::of({ Capability::content_metrics_api() });
    }
    vespalib::string getReportContentType(const framework::HttpUrlPath&) const override;
    bool reportStatus(std::ostream& out, const framework::HttpUrlPath&) const override;
    void updateMetrics(const MetricLockGuard & guard) override;
private:
    metrics::MetricManager& _manager;
    StorageComponent        _component;
    std::string             _name;
    mutable std::mutex      _lock;
    framework::SecondTime   _startTime;
    framework::SecondTime   _processedTime;
};

} // storage

