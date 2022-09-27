// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentdb_metrics_updater.h"
#include "ddbstate.h"
#include "document_meta_store_read_guards.h"
#include "documentsubdbcollection.h"
#include "executorthreadingservice.h"
#include "feedhandler.h"
#include "idocumentsubdb.h"
#include <vespa/searchcommon/attribute/status.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_filter.h>
#include <vespa/searchcore/proton/attribute/i_attribute_manager.h>
#include <vespa/searchcore/proton/docsummary/isummarymanager.h>
#include <vespa/searchcore/proton/matching/matching_stats.h>
#include <vespa/searchcore/proton/matching/matching_stats.h>
#include <vespa/searchcore/proton/metrics/documentdb_job_trackers.h>
#include <vespa/searchcore/proton/metrics/executor_threading_service_stats.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/vespalib/stllike/cache_stats.h>
#include <vespa/searchlib/util/searchable_stats.h>
#include <vespa/vespalib/util/memoryusage.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.documentdb_metrics_updater");

using search::LidUsageStats;
using vespalib::CacheStats;
using vespalib::MemoryUsage;

namespace proton {

using matching::MatchingStats;

DocumentDBMetricsUpdater::DocumentDBMetricsUpdater(const DocumentSubDBCollection &subDBs,
                                                   ExecutorThreadingService &writeService,
                                                   DocumentDBJobTrackers &jobTrackers,
                                                   matching::SessionManager &sessionManager,
                                                   const AttributeUsageFilter &writeFilter,
                                                   FeedHandler& feed_handler)
    : _subDBs(subDBs),
      _writeService(writeService),
      _jobTrackers(jobTrackers),
      _sessionManager(sessionManager),
      _writeFilter(writeFilter),
      _feed_handler(feed_handler),
      _lastDocStoreCacheStats(),
      _last_feed_handler_stats()
{
}

DocumentDBMetricsUpdater::~DocumentDBMetricsUpdater() = default;

namespace {

struct TotalStats {
    MemoryUsage memoryUsage;
    uint64_t diskUsage;
    TotalStats() : memoryUsage(), diskUsage() {}
};

void
updateMemoryUsageMetrics(MemoryUsageMetrics &metrics, const MemoryUsage &memoryUsage, TotalStats &totalStats)
{
    metrics.update(memoryUsage);
    totalStats.memoryUsage.merge(memoryUsage);
}

void
updateDiskUsageMetric(metrics::LongValueMetric &metric, uint64_t diskUsage, TotalStats &totalStats)
{
    metric.set(diskUsage);
    totalStats.diskUsage += diskUsage;
}

void
updateIndexMetrics(DocumentDBTaggedMetrics &metrics, const search::SearchableStats &stats, TotalStats &totalStats)
{
    DocumentDBTaggedMetrics::IndexMetrics &indexMetrics = metrics.index;
    updateDiskUsageMetric(indexMetrics.diskUsage, stats.sizeOnDisk(), totalStats);
    updateMemoryUsageMetrics(indexMetrics.memoryUsage, stats.memoryUsage(), totalStats);
    indexMetrics.docsInMemory.set(stats.docsInMemory());
}

struct TempAttributeMetric
{
    MemoryUsage memoryUsage;
    uint64_t    bitVectors;

    TempAttributeMetric()
        : memoryUsage(),
          bitVectors(0)
    {}
};

struct TempAttributeMetrics
{
    typedef std::map<vespalib::string, TempAttributeMetric> AttrMap;
    TempAttributeMetric total;
    AttrMap attrs;
};

bool
isReadySubDB(const IDocumentSubDB *subDb, const DocumentSubDBCollection &subDbs)
{
    return subDb == subDbs.getReadySubDB();
}

bool
isNotReadySubDB(const IDocumentSubDB *subDb, const DocumentSubDBCollection &subDbs)
{
    return subDb == subDbs.getNotReadySubDB();
}

void
fillTempAttributeMetrics(TempAttributeMetrics &metrics, const vespalib::string &attrName,
                         const MemoryUsage &memoryUsage, uint32_t bitVectors)
{
    metrics.total.memoryUsage.merge(memoryUsage);
    metrics.total.bitVectors += bitVectors;
    TempAttributeMetric &m = metrics.attrs[attrName];
    m.memoryUsage.merge(memoryUsage);
    m.bitVectors += bitVectors;
}

void
fillTempAttributeMetrics(TempAttributeMetrics &totalMetrics,
                         TempAttributeMetrics &readyMetrics,
                         TempAttributeMetrics &notReadyMetrics,
                         const DocumentSubDBCollection &subDbs)
{
    for (const auto subDb : subDbs) {
        proton::IAttributeManager::SP attrMgr(subDb->getAttributeManager());
        if (attrMgr) {
            TempAttributeMetrics *subMetrics =
                    (isReadySubDB(subDb, subDbs) ? &readyMetrics :
                     (isNotReadySubDB(subDb, subDbs) ? &notReadyMetrics : nullptr));
            std::vector<search::AttributeGuard> list;
            attrMgr->getAttributeListAll(list);
            for (const auto &attr : list) {
                const search::attribute::Status &status = attr->getStatus();
                MemoryUsage memoryUsage(status.getAllocated(), status.getUsed(), status.getDead(), status.getOnHold());
                uint32_t bitVectors = status.getBitVectors();
                fillTempAttributeMetrics(totalMetrics, attr->getName(), memoryUsage, bitVectors);
                if (subMetrics != nullptr) {
                    fillTempAttributeMetrics(*subMetrics, attr->getName(), memoryUsage, bitVectors);
                }
            }
        }
    }
}

void
updateAttributeMetrics(AttributeMetrics &metrics, const TempAttributeMetrics &tmpMetrics)
{
    for (const auto &attr : tmpMetrics.attrs) {
        auto entry = metrics.get(attr.first);
        if (entry) {
            entry->memoryUsage.update(attr.second.memoryUsage);
        }
    }
}

void
updateAttributeMetrics(DocumentDBTaggedMetrics &metrics, const DocumentSubDBCollection &subDbs, TotalStats &totalStats)
{
    TempAttributeMetrics totalMetrics;
    TempAttributeMetrics readyMetrics;
    TempAttributeMetrics notReadyMetrics;
    fillTempAttributeMetrics(totalMetrics, readyMetrics, notReadyMetrics, subDbs);

    updateAttributeMetrics(metrics.ready.attributes, readyMetrics);
    updateAttributeMetrics(metrics.notReady.attributes, notReadyMetrics);
    updateMemoryUsageMetrics(metrics.attribute.totalMemoryUsage, totalMetrics.total.memoryUsage, totalStats);
}

void
updateMatchingMetrics(const metrics::MetricLockGuard & guard, DocumentDBTaggedMetrics &metrics, const IDocumentSubDB &ready)
{
    MatchingStats totalStats;
    for (const auto &rankProfile : metrics.matching.rank_profiles) {
        MatchingStats matchingStats = ready.getMatcherStats(rankProfile.first);
        rankProfile.second->update(guard, matchingStats);

        totalStats.add(matchingStats);
    }
    metrics.matching.update(totalStats);
}

void
updateSessionCacheMetrics(DocumentDBTaggedMetrics &metrics, proton::matching::SessionManager &sessionManager)
{
    auto searchStats = sessionManager.getSearchStats();
    metrics.sessionCache.search.update(searchStats);

    auto groupingStats = sessionManager.getGroupingStats();
    metrics.sessionCache.grouping.update(groupingStats);
}

void
updateDocumentsMetrics(DocumentDBTaggedMetrics &metrics, const DocumentSubDBCollection &subDbs)
{
    DocumentMetaStoreReadGuards dms(subDbs);
    uint32_t active = dms.numActiveDocs();
    uint32_t ready = dms.numReadyDocs();
    uint32_t total = dms.numTotalDocs();
    uint32_t removed = dms.numRemovedDocs();

    auto &docsMetrics = metrics.documents;
    docsMetrics.active.set(active);
    docsMetrics.ready.set(ready);
    docsMetrics.total.set(total);
    docsMetrics.removed.set(removed);
}

void
updateDocumentStoreCacheHitRate(const CacheStats &current, const CacheStats &last,
                                metrics::LongAverageMetric &cacheHitRate)
{
    if (current.lookups() < last.lookups() || current.hits < last.hits) {
        LOG(warning, "Not adding document store cache hit rate metrics as values calculated "
                     "are corrupt. current.lookups=%zu, last.lookups=%zu, current.hits=%zu, last.hits=%zu.",
            current.lookups(), last.lookups(), current.hits, last.hits);
    } else {
        if ((current.lookups() - last.lookups()) > 0xffffffffull
            || (current.hits - last.hits) > 0xffffffffull)
        {
            LOG(warning, "Document store cache hit rate metrics to add are suspiciously high."
                         " lookups diff=%zu, hits diff=%zu.",
                current.lookups() - last.lookups(), current.hits - last.hits);
        }
        cacheHitRate.addTotalValueWithCount(current.hits - last.hits, current.lookups() - last.lookups());
    }
}

void
updateCountMetric(uint64_t currVal, uint64_t lastVal, metrics::LongCountMetric &metric)
{
    uint64_t delta = (currVal >= lastVal) ? (currVal - lastVal) : 0;
    metric.inc(delta);
}

void
updateDocumentStoreMetrics(DocumentDBTaggedMetrics::SubDBMetrics::DocumentStoreMetrics &metrics,
                           const IDocumentSubDB *subDb,
                           CacheStats &lastCacheStats,
                           TotalStats &totalStats)
{
    const ISummaryManager::SP &summaryMgr = subDb->getSummaryManager();
    search::IDocumentStore &backingStore = summaryMgr->getBackingStore();
    search::DataStoreStorageStats storageStats(backingStore.getStorageStats());
    updateDiskUsageMetric(metrics.diskUsage, storageStats.diskUsage(), totalStats);
    metrics.diskBloat.set(storageStats.diskBloat());
    metrics.maxBucketSpread.set(storageStats.maxBucketSpread());
    updateMemoryUsageMetrics(metrics.memoryUsage, backingStore.getMemoryUsage(), totalStats);

    vespalib::CacheStats cacheStats = backingStore.getCacheStats();
    totalStats.memoryUsage.incAllocatedBytes(cacheStats.memory_used);
    metrics.cache.memoryUsage.set(cacheStats.memory_used);
    metrics.cache.elements.set(cacheStats.elements);
    updateDocumentStoreCacheHitRate(cacheStats, lastCacheStats, metrics.cache.hitRate);
    updateCountMetric(cacheStats.lookups(), lastCacheStats.lookups(), metrics.cache.lookups);
    updateCountMetric(cacheStats.invalidations, lastCacheStats.invalidations, metrics.cache.invalidations);
    lastCacheStats = cacheStats;
}

void
updateDocumentStoreMetrics(DocumentDBTaggedMetrics &metrics, const DocumentSubDBCollection &subDBs,
                           DocumentDBMetricsUpdater::DocumentStoreCacheStats &lastDocStoreCacheStats, TotalStats &totalStats)
{
    updateDocumentStoreMetrics(metrics.ready.documentStore, subDBs.getReadySubDB(), lastDocStoreCacheStats.readySubDb, totalStats);
    updateDocumentStoreMetrics(metrics.removed.documentStore, subDBs.getRemSubDB(), lastDocStoreCacheStats.removedSubDb, totalStats);
    updateDocumentStoreMetrics(metrics.notReady.documentStore, subDBs.getNotReadySubDB(), lastDocStoreCacheStats.notReadySubDb, totalStats);
}

template <typename MetricSetType>
void
updateLidSpaceMetrics(MetricSetType &metrics, const search::IDocumentMetaStore &metaStore)
{
    LidUsageStats stats = metaStore.getLidUsageStats();
    metrics.lidLimit.set(stats.getLidLimit());
    metrics.usedLids.set(stats.getUsedLids());
    metrics.lowestFreeLid.set(stats.getLowestFreeLid());
    metrics.highestUsedLid.set(stats.getHighestUsedLid());
    metrics.lidBloatFactor.set(stats.getLidBloatFactor());
    metrics.lidFragmentationFactor.set(stats.getLidFragmentationFactor());
}

void
update_feeding_metrics(DocumentDBFeedingMetrics& metrics, FeedHandlerStats stats, std::optional<FeedHandlerStats>& last_stats)
{
    auto delta_stats = stats;
    if (last_stats.has_value()) {
        delta_stats -= last_stats.value();
    }
    last_stats = stats;
    uint32_t commits = delta_stats.get_commits();
    if (commits != 0) {
        double min_operations = delta_stats.get_min_operations().value_or(0);
        double max_operations = delta_stats.get_max_operations().value_or(0);
        double avg_operations = ((double) delta_stats.get_operations()) / commits;
        metrics.commit.operations.addValueBatch(avg_operations, commits, min_operations, max_operations);
        double min_latency = delta_stats.get_min_latency().value_or(0.0);
        double max_latency = delta_stats.get_max_latency().value_or(0.0);
        double avg_latency = delta_stats.get_total_latency() / commits;
        metrics.commit.latency.addValueBatch(avg_latency, commits, min_latency, max_latency);
    }
}

}

void
DocumentDBMetricsUpdater::updateMetrics(const metrics::MetricLockGuard & guard, DocumentDBTaggedMetrics &metrics)
{
    TotalStats totalStats;
    ExecutorThreadingServiceStats threadingServiceStats = _writeService.getStats();
    updateIndexMetrics(metrics, _subDBs.getReadySubDB()->getSearchableStats(), totalStats);
    updateAttributeMetrics(metrics, _subDBs, totalStats);
    updateMatchingMetrics(guard, metrics, *_subDBs.getReadySubDB());
    updateSessionCacheMetrics(metrics, _sessionManager);
    updateDocumentsMetrics(metrics, _subDBs);
    updateDocumentStoreMetrics(metrics, _subDBs, _lastDocStoreCacheStats, totalStats);
    updateMiscMetrics(metrics, threadingServiceStats);

    metrics.totalMemoryUsage.update(totalStats.memoryUsage);
    metrics.totalDiskUsage.set(totalStats.diskUsage);
    update_feeding_metrics(metrics.feeding, _feed_handler.get_stats(true), _last_feed_handler_stats);
}

void
DocumentDBMetricsUpdater::updateAttributeResourceUsageMetrics(DocumentDBTaggedMetrics::AttributeMetrics &metrics)
{
    AttributeUsageStats stats = _writeFilter.getAttributeUsageStats();
    bool feedBlocked = !_writeFilter.acceptWriteOperation();
    double address_space_used = stats.max_address_space_usage().getUsage().usage();
    metrics.resourceUsage.address_space.set(address_space_used);
    metrics.resourceUsage.feedingBlocked.set(feedBlocked ? 1 : 0);
}

void
DocumentDBMetricsUpdater::updateMiscMetrics(DocumentDBTaggedMetrics &metrics, const ExecutorThreadingServiceStats &threadingServiceStats)
{
    metrics.threadingService.update(threadingServiceStats);
    _jobTrackers.updateMetrics(metrics.job);

    updateAttributeResourceUsageMetrics(metrics.attribute);

    DocumentMetaStoreReadGuards dmss(_subDBs);
    updateLidSpaceMetrics(metrics.ready.lidSpace, dmss.readydms->get());
    updateLidSpaceMetrics(metrics.notReady.lidSpace, dmss.notreadydms->get());
    updateLidSpaceMetrics(metrics.removed.lidSpace, dmss.remdms->get());
}

}
