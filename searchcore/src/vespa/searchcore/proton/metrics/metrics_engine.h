// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "content_proton_metrics.h"
#include "documentdb_metrics_collection.h"
#include "legacy_proton_metrics.h"
#include "metricswireservice.h"
#include <algorithm>
#include <vespa/fastlib/net/httpserver.h>
#include <vespa/metrics/metrics.h>
#include <vespa/metrics/state_api_adapter.h>
#include <vespa/vespalib/net/metrics_producer.h>

namespace metrics {
    class Metricmanager;
    class UpdateHook;
}

namespace config {
    class ConfigUri;
}
namespace proton {

class MetricsEngine : public MetricsWireService
{
private:
    ContentProtonMetrics                      _root;
    LegacyProtonMetrics                       _legacyRoot;
    std::unique_ptr<metrics::MetricManager>   _manager;
    metrics::StateApiAdapter                  _metrics_producer;

public:
    typedef std::unique_ptr<MetricsEngine> UP;

    MetricsEngine();
    virtual ~MetricsEngine();
    ContentProtonMetrics &root() { return _root; }
    LegacyProtonMetrics &legacyRoot() { return _legacyRoot; }
    void start(const config::ConfigUri & configUri);
    void addMetricsHook(metrics::UpdateHook &hook);
    void removeMetricsHook(metrics::UpdateHook &hook);
    void addExternalMetrics(metrics::Metric &child);
    void removeExternalMetrics(metrics::Metric &child);
    void addDocumentDBMetrics(DocumentDBMetricsCollection &child);
    void removeDocumentDBMetrics(DocumentDBMetricsCollection &child);
    virtual void addAttribute(const AttributeMetricsCollection &subAttributes,
                              LegacyAttributeMetrics *totalAttributes,
                              const std::string &name) override;
    virtual void removeAttribute(const AttributeMetricsCollection &subAttributes,
                                 LegacyAttributeMetrics *totalAttributes,
                                 const std::string &name) override;
    virtual void cleanAttributes(const AttributeMetricsCollection &subAttributes,
                                 LegacyAttributeMetrics *totalAttributes) override;
    virtual void addRankProfile(LegacyDocumentDBMetrics &owner,
                                const std::string &name,
                                size_t numDocIdPartitions) override;
    virtual void cleanRankProfiles(LegacyDocumentDBMetrics &owner) override;
    void stop();

    vespalib::MetricsProducer &metrics_producer() { return _metrics_producer; }
    metrics::MetricManager &getManager() { return *_manager; }
};

} // namespace proton
