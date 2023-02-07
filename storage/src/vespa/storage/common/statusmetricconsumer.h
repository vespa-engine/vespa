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
#include <vespa/vespalib/util/jsonstream.h>

namespace vespalib { class StringTokenizer; }
namespace metrics { class MetricManager; }

namespace storage {

class StatusMetricConsumer : public framework::StatusReporter,
                             private vespalib::JsonStreamTypes
{
public:
    StatusMetricConsumer(StorageComponentRegister&, metrics::MetricManager&, const std::string& name = "status");
    ~StatusMetricConsumer() override;

    // Metric reporting requires the "vespa.content.metrics_api" capability
    CapabilitySet required_capabilities() const noexcept override {
        return CapabilitySet::of({ Capability::content_metrics_api() });
    }
    vespalib::string getReportContentType(const framework::HttpUrlPath&) const override;
    bool reportStatus(std::ostream& out, const framework::HttpUrlPath&) const override;
private:
    metrics::MetricManager& _manager;
    StorageComponent        _component;
    std::string             _name;
    mutable std::mutex      _lock;
};

} // storage

