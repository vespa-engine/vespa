// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "metrics_engine.h"
#include "attribute_metrics.h"
#include "documentdb_tagged_metrics.h"
#include "content_proton_metrics.h"
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

    // Storage doesnt snapshot unset metrics to save memory. Currently
    // feature seems a bit bugged. Disabling this optimalization for search.
    // Can enable it later when it is confirmed to be working well.
    _manager->snapshotUnsetMetrics(true);

    // Currently, when injecting a metric manager into the content layer,
    // the content layer require to be the one initializing and starting it.
    // Thus not calling init here, but further out in the application when
    // one knows whether we are running in row/column mode or not
}

void
MetricsEngine::addMetricsHook(metrics::UpdateHook &hook)
{
    _manager->addMetricUpdateHook(hook, 5);
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

namespace {

void
doAddAttribute(AttributeMetrics &attributes, const std::string &attrName)
{
    auto entry = attributes.add(attrName);
    if (entry) {
        attributes.parent()->registerMetric(*entry);
    } else {
        LOG(warning, "Could not add metrics for attribute '%s', already existing", attrName.c_str());
    }
}

void
doRemoveAttribute(AttributeMetrics &attributes, const std::string &attrName)
{
    auto entry = attributes.remove(attrName);
    if (entry) {
        attributes.parent()->unregisterMetric(*entry);
    } else {
        LOG(warning, "Could not remove metrics for attribute '%s', not found", attrName.c_str());
    }
}

void
doCleanAttributes(AttributeMetrics &attributes)
{
    auto entries = attributes.release();
    for (const auto &entry : entries) {
        attributes.parent()->unregisterMetric(*entry);
    }
}

}

void
MetricsEngine::addAttribute(AttributeMetrics &subAttributes,
                            const std::string &name)
{
    metrics::MetricLockGuard guard(_manager->getMetricLock());
    doAddAttribute(subAttributes, name);
}

void
MetricsEngine::removeAttribute(AttributeMetrics &subAttributes,
                               const std::string &name)
{
    metrics::MetricLockGuard guard(_manager->getMetricLock());
    doRemoveAttribute(subAttributes, name);
}

void
MetricsEngine::cleanAttributes(AttributeMetrics &subAttributes)
{
    metrics::MetricLockGuard guard(_manager->getMetricLock());
    doCleanAttributes(subAttributes);
}

namespace {

template <typename MatchingMetricsType>
void
addRankProfileTo(MatchingMetricsType &matchingMetrics, const vespalib::string &name, size_t numDocIdPartitions)
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
