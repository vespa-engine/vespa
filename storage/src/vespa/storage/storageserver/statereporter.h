// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * @class storage::StateReporter
 * @ingroup storageserver
 *
 * @brief Writes config generation or health status and metrics
 * as json to status page.
 */

#pragma once

#include <vespa/metrics/metrics.h>
#include <vespa/metrics/state_api_adapter.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storage/storageserver/applicationgenerationfetcher.h>
#include <vespa/storageframework/storageframework.h>
#include <vespa/vespalib/net/metrics_producer.h>
#include <vespa/vespalib/net/state_api.h>

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

    vespalib::string getReportContentType(
            const framework::HttpUrlPath&) const override;
    bool reportStatus(std::ostream& out,
                      const framework::HttpUrlPath& path) const override;

private:
    metrics::MetricManager &_manager;
    metrics::StateApiAdapter _metricsAdapter;
    vespalib::StateApi _stateApi;
    StorageComponent _component;
    ApplicationGenerationFetcher& _generationFetcher;
    std::string _name;

    // Implements vespalib::MetricsProducer
    virtual vespalib::string getMetrics(const vespalib::string &consumer) override;
    virtual vespalib::string getTotalMetrics(const vespalib::string &consumer) override;

    // Implements vespalib::HealthProducer
    virtual Health getHealth() const override;

    // Implements vespalib::ComponentConfigProducer
    virtual void getComponentConfig(Consumer &consumer) override;
};

} // storage

