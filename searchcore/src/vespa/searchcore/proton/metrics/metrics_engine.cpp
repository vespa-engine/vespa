// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "metrics_engine.h"
#include "attribute_metrics.h"
#include "documentdb_tagged_metrics.h"
#include "content_proton_metrics.h"
#include "index_metrics.h"
#include <vespa/metrics/jsonwriter.h>
#include <vespa/metrics/metricmanager.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.metricsengine");

namespace proton {

MetricsEngine::MetricsEngine()
    : _root(std::make_unique<ContentProtonMetrics>()),
      _manager(std::make_unique<metrics::MetricManager>()),
      _metrics_producer(*_manager)
{ }

MetricsEngine::~MetricsEngine() = default;

void
MetricsEngine::start(const config::ConfigUri &)
{
    {
        metrics::MetricLockGuard guard(_manager->getMetricLock());
        _manager->registerMetric(guard, *_root);
    }
    _manager->snapshotUnsetMetrics(true);
    // Starting the metric manager worker thread (MetricManager::init()) is not done here, as the service
    // layer code has not had the opportunity to create its metrics yet. Deferred to service layer init code.
}

void
MetricsEngine::addMetricsHook(metrics::UpdateHook &hook)
{
    _manager->addMetricUpdateHook(hook);
}

void
MetricsEngine::removeMetricsHook(metrics::UpdateHook &hook)
{
    _manager->removeMetricUpdateHook(hook);
}

void
MetricsEngine::addExternalMetrics(metrics::Metric &child)
{
    metrics::MetricLockGuard guard(_manager->getMetricLock());
    _root->registerMetric(child);
}

void
MetricsEngine::removeExternalMetrics(metrics::Metric &child)
{
    metrics::MetricLockGuard guard(_manager->getMetricLock());
    _root->unregisterMetric(child);
}

void
MetricsEngine::addDocumentDBMetrics(DocumentDBTaggedMetrics &child)
{
    metrics::MetricLockGuard guard(_manager->getMetricLock());
    _root->registerMetric(child);
}

void
MetricsEngine::removeDocumentDBMetrics(DocumentDBTaggedMetrics &child)
{
    metrics::MetricLockGuard guard(_manager->getMetricLock());
    _root->unregisterMetric(child);
}

void
MetricsEngine::set_attributes(AttributeMetrics& subAttributes, std::vector<std::string> field_names)
{
    metrics::MetricLockGuard guard(_manager->getMetricLock());
    subAttributes.set_fields(std::move(field_names));
}

void
MetricsEngine::set_index_fields(IndexMetrics& index_fields, std::vector<std::string> field_names)
{
    metrics::MetricLockGuard guard(_manager->getMetricLock());
    index_fields.set_fields(std::move(field_names));
}

namespace {

template <typename MatchingMetricsType>
void
addRankProfileTo(MatchingMetricsType &matchingMetrics, const std::string &name, size_t numDocIdPartitions)
{
    auto &entry = matchingMetrics.rank_profiles[name];
    if (entry.get()) {
        LOG(warning, "Two rank profiles have the same name: %s", name.c_str());
    } else {
        matchingMetrics.rank_profiles[name].reset(
                new typename MatchingMetricsType::RankProfileMetrics(name, numDocIdPartitions, &matchingMetrics));
    }
}

template <typename MatchingMetricsType>
void
cleanRankProfilesIn(MatchingMetricsType &matchingMetrics)
{
    typename MatchingMetricsType::RankProfileMap rankMetrics;
    matchingMetrics.rank_profiles.swap(rankMetrics);
    for (const auto &metric : rankMetrics) {
        matchingMetrics.unregisterMetric(*metric.second);
    }
}

}

void
MetricsEngine::addRankProfile(DocumentDBTaggedMetrics &owner, const std::string &name, size_t numDocIdPartitions)
{
    metrics::MetricLockGuard guard(_manager->getMetricLock());
    size_t adjustedNumDocIdPartitions = std::min(numDocIdPartitions, owner.maxNumThreads);
    addRankProfileTo(owner.matching, name, adjustedNumDocIdPartitions);
}

void
MetricsEngine::cleanRankProfiles(DocumentDBTaggedMetrics &owner) {
    metrics::MetricLockGuard guard(_manager->getMetricLock());
    cleanRankProfilesIn(owner.matching);
}

void
MetricsEngine::stop()
{
    _manager->stop();
}

}
