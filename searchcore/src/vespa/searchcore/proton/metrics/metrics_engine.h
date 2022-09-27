// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "metricswireservice.h"
#include <vespa/metrics/state_api_adapter.h>
#include <memory>

namespace metrics {
    class Metric;
    class Metricmanager;
    class UpdateHook;
}

namespace config {
    class ConfigUri;
}
namespace proton {

class AttributeMetrics;
struct DocumentDBTaggedMetrics;
struct ContentProtonMetrics;

class MetricsEngine : public MetricsWireService
{
private:
    std::unique_ptr<ContentProtonMetrics>     _root;
    std::unique_ptr<metrics::MetricManager>   _manager;
    metrics::StateApiAdapter                  _metrics_producer;

public:
    typedef std::unique_ptr<MetricsEngine> UP;

    MetricsEngine();
    ~MetricsEngine() override;
    ContentProtonMetrics &root() { return *_root; }
    void start(const config::ConfigUri & configUri);
    void addMetricsHook(metrics::UpdateHook &hook);
    void removeMetricsHook(metrics::UpdateHook &hook);
    void addExternalMetrics(metrics::Metric &child);
    void removeExternalMetrics(metrics::Metric &child);
    void addDocumentDBMetrics(DocumentDBTaggedMetrics &child);
    void removeDocumentDBMetrics(DocumentDBTaggedMetrics &child);
    void addAttribute(AttributeMetrics &subAttributes,
                      const std::string &name) override;
    void removeAttribute(AttributeMetrics &subAttributes,
                         const std::string &name) override;
    void cleanAttributes(AttributeMetrics &subAttributes) override;
    void addRankProfile(DocumentDBTaggedMetrics &owner, const std::string &name, size_t numDocIdPartitions) override;
    void cleanRankProfiles(DocumentDBTaggedMetrics &owner) override;
    void stop();

    vespalib::MetricsProducer &metrics_producer() { return _metrics_producer; }
    metrics::MetricManager &getManager() { return *_manager; }
};

} // namespace proton
