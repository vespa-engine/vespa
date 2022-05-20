// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * @class storage::StateReporter
 * @ingroup storageserver
 *
 * @brief Writes config generation or health status and metrics
 * as json to status page.
 */

#pragma once

#include "applicationgenerationfetcher.h"
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storageframework/generic/status/statusreporter.h>
#include <vespa/metrics/state_api_adapter.h>
#include <vespa/vespalib/net/http/metrics_producer.h>
#include <vespa/vespalib/net/http/state_api.h>

namespace vespalib {
    class StringTokenizer;
}

namespace storage {

class StateReporter : public framework::StatusReporter,
                      public vespalib::MetricsProducer,
                      public vespalib::HealthProducer,
                      public vespalib::ComponentConfigProducer
{
public:
    StateReporter(
            StorageComponentRegister&,
            metrics::MetricManager&,
            ApplicationGenerationFetcher& generationFetcher,
            const std::string& name = "status");
    ~StateReporter();

    vespalib::string getReportContentType(const framework::HttpUrlPath&) const override;
    bool reportStatus(std::ostream& out, const framework::HttpUrlPath& path) const override;
private:
    metrics::MetricManager &_manager;
    metrics::StateApiAdapter _metricsAdapter;
    vespalib::StateApi _stateApi;
    StorageComponent _component;
    ApplicationGenerationFetcher& _generationFetcher;
    std::string _name;

    vespalib::string getMetrics(const vespalib::string &consumer) override;
    vespalib::string getTotalMetrics(const vespalib::string &consumer) override;
    Health getHealth() const override;
    void getComponentConfig(Consumer &consumer) override;
};

} // storage
