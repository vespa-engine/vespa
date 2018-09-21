// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ddbstate.h"
#include "document_meta_store_read_guards.h"
#include "documentdb_metrics_updater.h"
#include "documentsubdbcollection.h"
#include "executorthreadingservice.h"
#include "idocumentsubdb.h"
#include <vespa/searchcommon/attribute/status.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_filter.h>
#include <vespa/searchcore/proton/attribute/i_attribute_manager.h>
#include <vespa/searchcore/proton/docsummary/isummarymanager.h>
#include <vespa/searchcore/proton/matching/matching_stats.h>
#include <vespa/searchcore/proton/matching/matching_stats.h>
#include <vespa/searchcore/proton/metrics/documentdb_job_trackers.h>
#include <vespa/searchcore/proton/metrics/documentdb_metrics_collection.h>
#include <vespa/searchcore/proton/metrics/executor_threading_service_stats.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/docstore/cachestats.h>
#include <vespa/searchlib/util/memoryusage.h>
#include <vespa/searchlib/util/searchable_stats.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.documentdb_metrics_updater");

using search::LidUsageStats;
using search::CacheStats;
using search::MemoryUsage;

namespace proton {

using matching::MatchingStats;

DocumentDBMetricsUpdater::DocumentDBMetricsUpdater(const DocumentSubDBCollection &subDBs,
                                                   ExecutorThreadingService &writeService,
                                                   DocumentDBJobTrackers &jobTrackers,
                                                   matching::SessionManager &sessionManager,
                                                   const AttributeUsageFilter &writeFilter,
                                                   const DDBState &state)
    : _subDBs(subDBs),
      _writeService(writeService),
      _jobTrackers(jobTrackers),
      _sessionManager(sessionManager),
      _writeFilter(writeFilter),
      _state(state)
{
}

DocumentDBMetricsUpdater::~DocumentDBMetricsUpdater() = default;

namespace {

void
updateMemoryUsageMetrics(MemoryUsageMetrics &metrics, const MemoryUsage &memoryUsage, MemoryUsage &totalMemoryUsage)
{
    metrics.update(memoryUsage);
    totalMemoryUsage.merge(memoryUsage);
}

void
updateIndexMetrics(DocumentDBMetricsCollection &metrics, const search::SearchableStats &stats, MemoryUsage &totalMemoryUsage)
{
    DocumentDBTaggedMetrics::IndexMetrics &indexMetrics = metrics.getTaggedMetrics().index;
    indexMetrics.diskUsage.set(stats.sizeOnDisk());
    updateMemoryUsageMetrics(indexMetrics.memoryUsage, stats.memoryUsage(), totalMemoryUsage);
    indexMetrics.docsInMemory.set(stats.docsInMemory());

    LegacyDocumentDBMetrics::IndexMetrics &legacyIndexMetrics = metrics.getLegacyMetrics().index;
    legacyIndexMetrics.memoryUsage.set(stats.memoryUsage().allocatedBytes());
    legacyIndexMetrics.docsInMemory.set(stats.docsInMemory());
    legacyIndexMetrics.diskUsage.set(stats.sizeOnDisk());
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
updateLegacyAttributeMetrics(LegacyAttributeMetrics &metrics, const TempAttributeMetrics &tmpMetrics)
{
    for (const auto &attr : tmpMetrics.attrs) {
        LegacyAttributeMetrics::List::Entry *entry = metrics.list.get(attr.first);
        if (entry) {
            entry->memoryUsage.set(attr.second.memoryUsage.allocatedBytes());
            entry->bitVectors.set(attr.second.bitVectors);
        } else {
            LOG(debug, "Could not update metrics for attribute: '%s'", attr.first.c_str());
        }
    }
    metrics.memoryUsage.set(tmpMetrics.total.memoryUsage.allocatedBytes());
    metrics.bitVectors.set(tmpMetrics.total.bitVectors);
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
updateAttributeMetrics(DocumentDBMetricsCollection &metrics, const DocumentSubDBCollection &subDbs, MemoryUsage &totalMemoryUsage)
{
    TempAttributeMetrics totalMetrics;
    TempAttributeMetrics readyMetrics;
    TempAttributeMetrics notReadyMetrics;
    fillTempAttributeMetrics(totalMetrics, readyMetrics, notReadyMetrics, subDbs);

    updateLegacyAttributeMetrics(metrics.getLegacyMetrics().attributes, totalMetrics);
    updateLegacyAttributeMetrics(metrics.getLegacyMetrics().ready.attributes, readyMetrics);
    updateLegacyAttributeMetrics(metrics.getLegacyMetrics().notReady.attributes, notReadyMetrics);

    updateAttributeMetrics(metrics.getTaggedMetrics().ready.attributes, readyMetrics);
    updateAttributeMetrics(metrics.getTaggedMetrics().notReady.attributes, notReadyMetrics);
    updateMemoryUsageMetrics(metrics.getTaggedMetrics().attribute.totalMemoryUsage, totalMetrics.total.memoryUsage, totalMemoryUsage);
}

void
updateLegacyRankProfileMetrics(LegacyDocumentDBMetrics::MatchingMetrics &matchingMetrics,
                               const vespalib::string &rankProfileName,
                               const MatchingStats &stats)
{
    auto itr = matchingMetrics.rank_profiles.find(rankProfileName);
    assert(itr != matchingMetrics.rank_profiles.end());
    itr->second->update(stats);
}

void
updateMatchingMetrics(DocumentDBMetricsCollection &metrics, const IDocumentSubDB &ready)
{
    MatchingStats totalStats;
    for (const auto &rankProfile : metrics.getTaggedMetrics().matching.rank_profiles) {
        MatchingStats matchingStats = ready.getMatcherStats(rankProfile.first);
        rankProfile.second->update(matchingStats);
        updateLegacyRankProfileMetrics(metrics.getLegacyMetrics().matching, rankProfile.first, matchingStats);

        totalStats.add(matchingStats);
    }
    metrics.getTaggedMetrics().matching.update(totalStats);
    metrics.getLegacyMetrics().matching.update(totalStats);
}

void
updateSessionCacheMetrics(DocumentDBMetricsCollection &metrics, proton::matching::SessionManager &sessionManager)
{
    auto searchStats = sessionManager.getSearchStats();
    metrics.getTaggedMetrics().sessionCache.search.update(searchStats);

    auto groupingStats = sessionManager.getGroupingStats();
    metrics.getTaggedMetrics().sessionCache.grouping.update(groupingStats);
    metrics.getLegacyMetrics().sessionManager.update(groupingStats);
}

void
updateDocumentsMetrics(DocumentDBMetricsCollection &metrics, const DocumentSubDBCollection &subDbs)
{
    DocumentMetaStoreReadGuards dms(subDbs);
    uint32_t active = dms.numActiveDocs();
    uint32_t ready = dms.numReadyDocs();
    uint32_t total = dms.numTotalDocs();
    uint32_t removed = dms.numRemovedDocs();

    auto &docsMetrics = metrics.getTaggedMetrics().documents;
    docsMetrics.active.set(active);
    docsMetrics.ready.set(ready);
    docsMetrics.total.set(total);
    docsMetrics.removed.set(removed);

    auto &legacyMetrics = metrics.getLegacyMetrics();
    legacyMetrics.numDocs.set(ready);
    legacyMetrics.numActiveDocs.set(active);
    legacyMetrics.numIndexedDocs.set(ready);
    legacyMetrics.numStoredDocs.set(total);
    legacyMetrics.numRemovedDocs.set(removed);
}

void
updateDocumentStoreCacheHitRate(const CacheStats &current, const CacheStats &last,
                                metrics::LongAverageMetric &cacheHitRate)
{
    if (current.lookups() < last.lookups() || current.hits < last.hits) {
        LOG(warning, "Not adding document store cache hit rate metrics as values calculated "
                     "are corrupt. current.lookups=%" PRIu64 ", last.lookups=%" PRIu64 ", current.hits=%" PRIu64 ", last.hits=%" PRIu64 ".",
            current.lookups(), last.lookups(), current.hits, last.hits);
    } else {
        if ((current.lookups() - last.lookups()) > 0xffffffffull
            || (current.hits - last.hits) > 0xffffffffull)
        {
            LOG(warning, "Document store cache hit rate metrics to add are suspiciously high."
                         " lookups diff=%" PRIu64 ", hits diff=%" PRIu64 ".",
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
updateDocstoreMetrics(LegacyDocumentDBMetrics::DocstoreMetrics &metrics,
                      const DocumentSubDBCollection &sub_dbs,
                      CacheStats &lastCacheStats)
{
    size_t memoryUsage = 0;
    CacheStats cache_stats;
    for (const auto subDb : sub_dbs) {
        const ISummaryManager::SP &summaryMgr = subDb->getSummaryManager();
        if (summaryMgr) {
            cache_stats += summaryMgr->getBackingStore().getCacheStats();
            memoryUsage += summaryMgr->getBackingStore().memoryUsed();
        }
    }
    metrics.memoryUsage.set(memoryUsage);
    updateCountMetric(cache_stats.lookups(), lastCacheStats.lookups(), metrics.cacheLookups);
    updateDocumentStoreCacheHitRate(cache_stats, lastCacheStats, metrics.cacheHitRate);
    metrics.cacheElements.set(cache_stats.elements);
    metrics.cacheMemoryUsed.set(cache_stats.memory_used);
    lastCacheStats = cache_stats;
}

void
updateDocumentStoreMetrics(DocumentDBTaggedMetrics::SubDBMetrics::DocumentStoreMetrics &metrics,
                           const IDocumentSubDB *subDb,
                           CacheStats &lastCacheStats,
                           MemoryUsage &totalMemoryUsage)
{
    const ISummaryManager::SP &summaryMgr = subDb->getSummaryManager();
    search::IDocumentStore &backingStore = summaryMgr->getBackingStore();
    search::DataStoreStorageStats storageStats(backingStore.getStorageStats());
    metrics.diskUsage.set(storageStats.diskUsage());
    metrics.diskBloat.set(storageStats.diskBloat());
    metrics.maxBucketSpread.set(storageStats.maxBucketSpread());
    updateMemoryUsageMetrics(metrics.memoryUsage, backingStore.getMemoryUsage(), totalMemoryUsage);

    search::CacheStats cacheStats = backingStore.getCacheStats();
    totalMemoryUsage.incAllocatedBytes(cacheStats.memory_used);
    metrics.cache.memoryUsage.set(cacheStats.memory_used);
    metrics.cache.elements.set(cacheStats.elements);
    updateDocumentStoreCacheHitRate(cacheStats, lastCacheStats, metrics.cache.hitRate);
    updateCountMetric(cacheStats.lookups(), lastCacheStats.lookups(), metrics.cache.lookups);
    updateCountMetric(cacheStats.invalidations, lastCacheStats.invalidations, metrics.cache.invalidations);
    lastCacheStats = cacheStats;
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

}

void
DocumentDBMetricsUpdater::updateMetrics(DocumentDBMetricsCollection &metrics)
{
    MemoryUsage totalMemoryUsage;
    ExecutorThreadingServiceStats threadingServiceStats = _writeService.getStats();
    updateLegacyMetrics(metrics.getLegacyMetrics(), threadingServiceStats);
    updateIndexMetrics(metrics, _subDBs.getReadySubDB()->getSearchableStats(), totalMemoryUsage);
    updateAttributeMetrics(metrics, _subDBs, totalMemoryUsage);
    updateMatchingMetrics(metrics, *_subDBs.getReadySubDB());
    updateSessionCacheMetrics(metrics, _sessionManager);
    updateDocumentsMetrics(metrics, _subDBs);
    updateMiscMetrics(metrics.getTaggedMetrics(), threadingServiceStats, totalMemoryUsage);
    metrics.getTaggedMetrics().totalMemoryUsage.update(totalMemoryUsage);
}

void
DocumentDBMetricsUpdater::updateLegacyMetrics(LegacyDocumentDBMetrics &metrics, const ExecutorThreadingServiceStats &threadingServiceStats)
{
    metrics.executor.update(threadingServiceStats.getMasterExecutorStats());
    metrics.summaryExecutor.update(threadingServiceStats.getSummaryExecutorStats());
    metrics.indexExecutor.update(threadingServiceStats.getIndexExecutorStats());
    updateDocstoreMetrics(metrics.docstore, _subDBs, _lastDocStoreCacheStats.total);

    DocumentMetaStoreReadGuards dmss(_subDBs);
    updateLidSpaceMetrics(metrics.ready.docMetaStore, dmss.readydms->get());
    updateLidSpaceMetrics(metrics.notReady.docMetaStore, dmss.notreadydms->get());
    updateLidSpaceMetrics(metrics.removed.docMetaStore, dmss.remdms->get());

    metrics.numBadConfigs.set(_state.getDelayedConfig() ? 1u : 0u);
}

void
DocumentDBMetricsUpdater::updateAttributeResourceUsageMetrics(DocumentDBTaggedMetrics::AttributeMetrics &metrics)
{
    AttributeUsageStats attributeUsageStats = _writeFilter.getAttributeUsageStats();
    bool feedBlocked = !_writeFilter.acceptWriteOperation();
    double enumStoreUsed = attributeUsageStats.enumStoreUsage().getUsage().usage();
    double multiValueUsed = attributeUsageStats.multiValueUsage().getUsage().usage();
    metrics.resourceUsage.enumStore.set(enumStoreUsed);
    metrics.resourceUsage.multiValue.set(multiValueUsed);
    metrics.resourceUsage.feedingBlocked.set(feedBlocked ? 1 : 0);
}

void
DocumentDBMetricsUpdater::updateMiscMetrics(DocumentDBTaggedMetrics &metrics, const ExecutorThreadingServiceStats &threadingServiceStats, MemoryUsage &totalMemoryUsage)
{
    metrics.threadingService.update(threadingServiceStats);
    _jobTrackers.updateMetrics(metrics.job);

    updateAttributeResourceUsageMetrics(metrics.attribute);
    updateDocumentStoreMetrics(metrics.ready.documentStore, _subDBs.getReadySubDB(), _lastDocStoreCacheStats.readySubDb, totalMemoryUsage);
    updateDocumentStoreMetrics(metrics.removed.documentStore, _subDBs.getRemSubDB(), _lastDocStoreCacheStats.removedSubDb, totalMemoryUsage);
    updateDocumentStoreMetrics(metrics.notReady.documentStore, _subDBs.getNotReadySubDB(), _lastDocStoreCacheStats.notReadySubDb, totalMemoryUsage);

    DocumentMetaStoreReadGuards dmss(_subDBs);
    updateLidSpaceMetrics(metrics.ready.lidSpace, dmss.readydms->get());
    updateLidSpaceMetrics(metrics.notReady.lidSpace, dmss.notreadydms->get());
    updateLidSpaceMetrics(metrics.removed.lidSpace, dmss.remdms->get());
}

}
