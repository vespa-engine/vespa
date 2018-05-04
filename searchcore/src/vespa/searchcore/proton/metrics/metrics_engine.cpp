// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "metrics_engine.h"
#include "attribute_metrics_collection.h"
#include "documentdb_metrics_collection.h"
#include <vespa/metrics/jsonwriter.h>
#include <vespa/metrics/metricmanager.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.metricsengine");

namespace proton {

MetricsEngine::MetricsEngine()
    : _root(),
      _legacyRoot(),
      _manager(std::make_unique<metrics::MetricManager>()),
      _metrics_producer(*_manager)
{ }

MetricsEngine::~MetricsEngine() = default;

void
MetricsEngine::start(const config::ConfigUri &)
{
    {
        metrics::MetricLockGuard guard(_manager->getMetricLock());
        _manager->registerMetric(guard, _root);
        _manager->registerMetric(guard, _legacyRoot);
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
    _legacyRoot.registerMetric(child);
}

void
MetricsEngine::removeExternalMetrics(metrics::Metric &child)
{
    metrics::MetricLockGuard guard(_manager->getMetricLock());
    _legacyRoot.unregisterMetric(child);
}

namespace {

void
addLegacyDocumentDBMetrics(LegacyProtonMetrics &legacyRoot,
                           LegacyDocumentDBMetrics &metrics)
{
    legacyRoot.docTypes.registerMetric(metrics);
    // cannot use sum of sum due to metric clone issues
    legacyRoot.memoryUsage.addMetricToSum(metrics.index.memoryUsage);
    legacyRoot.memoryUsage.addMetricToSum(metrics.attributes.memoryUsage);
    legacyRoot.memoryUsage.addMetricToSum(metrics.docstore.memoryUsage);
    legacyRoot.diskUsage.addMetricToSum(metrics.index.diskUsage);
    legacyRoot.docsInMemory.addMetricToSum(metrics.index.docsInMemory);
    legacyRoot.numDocs.addMetricToSum(metrics.numDocs);
    legacyRoot.numActiveDocs.addMetricToSum(metrics.numActiveDocs);
    legacyRoot.numIndexedDocs.addMetricToSum(metrics.numIndexedDocs);
    legacyRoot.numStoredDocs.addMetricToSum(metrics.numStoredDocs);
    legacyRoot.numRemovedDocs.addMetricToSum(metrics.numRemovedDocs);
}

void
removeLegacyDocumentDBMetrics(LegacyProtonMetrics &legacyRoot,
                              LegacyDocumentDBMetrics &metrics)
{
    legacyRoot.docTypes.unregisterMetric(metrics);
    // cannot use sum of sum due to metric clone issues
    legacyRoot.memoryUsage.removeMetricFromSum(metrics.index.memoryUsage);
    legacyRoot.memoryUsage.removeMetricFromSum(metrics.attributes.memoryUsage);
    legacyRoot.memoryUsage.removeMetricFromSum(metrics.docstore.memoryUsage);
    legacyRoot.diskUsage.removeMetricFromSum(metrics.index.diskUsage);
    legacyRoot.docsInMemory.removeMetricFromSum(metrics.index.docsInMemory);
    legacyRoot.numDocs.removeMetricFromSum(metrics.numDocs);
    legacyRoot.numActiveDocs.removeMetricFromSum(metrics.numActiveDocs);
    legacyRoot.numIndexedDocs.removeMetricFromSum(metrics.numIndexedDocs);
    legacyRoot.numStoredDocs.removeMetricFromSum(metrics.numStoredDocs);
    legacyRoot.numRemovedDocs.removeMetricFromSum(metrics.numRemovedDocs);
}

}

void
MetricsEngine::addDocumentDBMetrics(DocumentDBMetricsCollection &child)
{
    metrics::MetricLockGuard guard(_manager->getMetricLock());
    addLegacyDocumentDBMetrics(_legacyRoot, child.getLegacyMetrics());

    _root.registerMetric(child.getTaggedMetrics());
}

void
MetricsEngine::removeDocumentDBMetrics(DocumentDBMetricsCollection &child)
{
    metrics::MetricLockGuard guard(_manager->getMetricLock());
    removeLegacyDocumentDBMetrics(_legacyRoot, child.getLegacyMetrics());

    _root.unregisterMetric(child.getTaggedMetrics());
}

namespace {

void
doAddAttribute(LegacyAttributeMetrics &attributes,
               const std::string &name)
{
    LegacyAttributeMetrics::List::Entry *entry = attributes.list.add(name);
    if (entry != nullptr) {
        LOG(debug, "doAddAttribute(): name='%s', attributes=%p",
                name.c_str(), (void*)&attributes);
        attributes.list.registerMetric(*entry);
    } else {
        LOG(warning, "multiple attributes have the same name: '%s'", name.c_str());
    }
}

void
doRemoveAttribute(LegacyAttributeMetrics &attributes,
                  const std::string &name)
{
    LegacyAttributeMetrics::List::Entry::UP entry = attributes.list.remove(name);
    if (entry.get() != 0) {
        LOG(debug, "doRemoveAttribute(): name='%s', attributes=%p",
                name.c_str(), (void*)&attributes);
        attributes.list.unregisterMetric(*entry);
    } else {
        LOG(debug, "Could not remove attribute with name '%s', not found", name.c_str());
    }
}

void
doCleanAttributes(LegacyAttributeMetrics &attributes)
{
    std::vector<LegacyAttributeMetrics::List::Entry::UP> entries = attributes.list.release();
    for (size_t i = 0; i < entries.size(); ++i) {
        attributes.list.unregisterMetric(*entries[i]);
    }
}

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
    for (const auto entry : entries) {
        attributes.parent()->unregisterMetric(*entry);
    }
}

}

void
MetricsEngine::addAttribute(const AttributeMetricsCollection &subAttributes,
                            LegacyAttributeMetrics *totalAttributes,
                            const std::string &name)
{
    metrics::MetricLockGuard guard(_manager->getMetricLock());
    doAddAttribute(subAttributes.getMetrics(), name);
    doAddAttribute(subAttributes.getLegacyMetrics(), name);
    if (totalAttributes != NULL) {
        doAddAttribute(*totalAttributes, name);
    }
}

void
MetricsEngine::removeAttribute(const AttributeMetricsCollection &subAttributes,
                               LegacyAttributeMetrics *totalAttributes,
                               const std::string &name)
{
    metrics::MetricLockGuard guard(_manager->getMetricLock());
    doRemoveAttribute(subAttributes.getMetrics(), name);
    doRemoveAttribute(subAttributes.getLegacyMetrics(), name);
    if (totalAttributes != NULL) {
        doRemoveAttribute(*totalAttributes, name);
    }
}

void
MetricsEngine::cleanAttributes(const AttributeMetricsCollection &subAttributes,
                               LegacyAttributeMetrics *totalAttributes)
{
    metrics::MetricLockGuard guard(_manager->getMetricLock());
    doCleanAttributes(subAttributes.getMetrics());
    doCleanAttributes(subAttributes.getLegacyMetrics());
    if (totalAttributes != NULL) {
        doCleanAttributes(*totalAttributes);
    }
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
MetricsEngine::addRankProfile(DocumentDBMetricsCollection &owner, const std::string &name, size_t numDocIdPartitions) {
    metrics::MetricLockGuard guard(_manager->getMetricLock());
    size_t adjustedNumDocIdPartitions = std::min(numDocIdPartitions, owner.getLegacyMetrics()._maxNumThreads);
    addRankProfileTo(owner.getTaggedMetrics().matching, name, adjustedNumDocIdPartitions);
    addRankProfileTo(owner.getLegacyMetrics().matching, name, adjustedNumDocIdPartitions);
}

void
MetricsEngine::cleanRankProfiles(DocumentDBMetricsCollection &owner) {
    metrics::MetricLockGuard guard(_manager->getMetricLock());
    cleanRankProfilesIn(owner.getTaggedMetrics().matching);
    cleanRankProfilesIn(owner.getLegacyMetrics().matching);
}

void
MetricsEngine::stop()
{
    _manager->stop();
}

} // namespace proton
