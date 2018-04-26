// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "legacy_documentdb_metrics.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>

using vespalib::asciistream;
using vespalib::make_string;
using metrics::MetricSet;

namespace proton {

using matching::MatchingStats;

LegacyDocumentDBMetrics::IndexMetrics::IndexMetrics(MetricSet *parent)
    : MetricSet("index", "", "Index metrics", parent),
      memoryUsage("memoryusage", "", "Memory usage for memory indexes", this),
      docsInMemory("docsinmemory", "", "Number of documents in memory", this),
      diskUsage("diskusage", "", "Disk usage for disk indexes", this)
{ }

LegacyDocumentDBMetrics::IndexMetrics::~IndexMetrics() {}

LegacyDocumentDBMetrics::DocstoreMetrics::DocstoreMetrics(MetricSet *parent)
    : MetricSet("docstore", "", "Document store metrics", parent),
      memoryUsage("memoryusage", "", "Memory usage for docstore", this),
      cacheLookups("cachelookups", "", "Number of lookups in summary cache", this),
      hits(0),
      cacheHitRate("cachehitrate", "", "Rate of cache hits in summary cache", this),
      cacheElements("cacheelements", "", "Number of elements in summary cache", this),
      cacheMemoryUsed("cachememoryused", "", "Memory used by summary cache", this)
{ }

LegacyDocumentDBMetrics::DocstoreMetrics::~DocstoreMetrics() {}

void
LegacyDocumentDBMetrics::MatchingMetrics::update(const MatchingStats &stats)
{
    docsMatched.inc(stats.docsMatched());
    docsRanked.inc(stats.docsRanked());
    docsReRanked.inc(stats.docsReRanked());
    softDoomFactor.set(stats.softDoomFactor());
    queries.inc(stats.queries());
    queryCollateralTime.addValueBatch(stats.queryCollateralTimeAvg(), stats.queryCollateralTimeCount(),
                                      stats.queryCollateralTimeMin(), stats.queryCollateralTimeMax());
    queryLatency.addValueBatch(stats.queryLatencyAvg(), stats.queryLatencyCount(),
                               stats.queryLatencyMin(), stats.queryLatencyMax());
}

LegacyDocumentDBMetrics::MatchingMetrics::MatchingMetrics(MetricSet *parent)
    : MetricSet("matching", "", "Matching metrics", parent),
      docsMatched("docsmatched", "", "Number of documents matched", this),
      docsRanked("docsranked", "", "Number of documents ranked (first phase)", this),
      docsReRanked("docsreranked", "", "Number of documents re-ranked (second phase)", this),
      queries("queries", "", "Number of queries executed", this),
      softDoomFactor("softdoomfactor", "", "Factor used to compute soft-timeout", this),
      queryCollateralTime("querycollateraltime", "", "Average time spent setting up and tearing down queries", this),
      queryLatency("querylatency", "", "Average latency when matching a query", this)
{ }

LegacyDocumentDBMetrics::MatchingMetrics::~MatchingMetrics() {}

LegacyDocumentDBMetrics::MatchingMetrics::RankProfileMetrics::RankProfileMetrics(
        const std::string &name, size_t numDocIdPartitions, MetricSet *parent)
    : MetricSet(name, "", "Rank profile metrics", parent),
      queries("queries", "", "Number of queries executed", this),
      limited_queries("limitedqueries", "", "Number of queries limited in match phase", this),
      matchTime("match_time", "", "Average time for matching a query", this),
      groupingTime("grouping_time", "", "Average time spent on grouping", this),
      rerankTime("rerank_time", "", "Average time spent on 2nd phase ranking", this)
{
    for (size_t i=0; i < numDocIdPartitions; i++) {
        vespalib::string s(make_string("docid_part%02ld", i));
        partitions.push_back(DocIdPartition::UP(new DocIdPartition(s, this)));
    }
}

LegacyDocumentDBMetrics::MatchingMetrics::RankProfileMetrics::~RankProfileMetrics() {}

LegacyDocumentDBMetrics::MatchingMetrics::RankProfileMetrics::DocIdPartition::DocIdPartition(const std::string &name, MetricSet *parent) :
    MetricSet(name, "", "DocId Partition profile metrics", parent),
    docsMatched("docsmatched", "", "Number of documents matched", this),
    docsRanked("docsranked", "", "Number of documents ranked (first phase)", this),
    docsReRanked("docsreranked", "", "Number of documents re-ranked (second phase)", this),
    active_time("activetime", "", "Time spent doing actual work", this),
    wait_time("waittime", "", "Time spent waiting for other external threads and resources", this)
{ }

LegacyDocumentDBMetrics::MatchingMetrics::RankProfileMetrics::DocIdPartition::~DocIdPartition() {}

void
LegacyDocumentDBMetrics::MatchingMetrics::RankProfileMetrics::DocIdPartition::update(const MatchingStats::Partition &stats)
{
    docsMatched.inc(stats.docsMatched());
    docsRanked.inc(stats.docsRanked());
    docsReRanked.inc(stats.docsReRanked());
    active_time.addValueBatch(stats.active_time_avg(), stats.active_time_count(),
                              stats.active_time_min(), stats.active_time_max());
    wait_time.addValueBatch(stats.wait_time_avg(), stats.wait_time_count(),
                            stats.wait_time_min(), stats.wait_time_max());
}

void
LegacyDocumentDBMetrics::MatchingMetrics::RankProfileMetrics::update(const MatchingStats &stats)
{
    queries.inc(stats.queries());
    limited_queries.inc(stats.limited_queries());
    matchTime.addValueBatch(stats.matchTimeAvg(), stats.matchTimeCount(),
                            stats.matchTimeMin(), stats.matchTimeMax());
    groupingTime.addValueBatch(stats.groupingTimeAvg(), stats.groupingTimeCount(),
                               stats.groupingTimeMin(), stats.groupingTimeMax());
    rerankTime.addValueBatch(stats.rerankTimeAvg(), stats.rerankTimeCount(),
                             stats.rerankTimeMin(), stats.rerankTimeMax());
    if (stats.getNumPartitions() > 0) {
        if (stats.getNumPartitions() <= partitions.size()) {
            for (size_t i(0), m(stats.getNumPartitions()); i < m; i++) {
                DocIdPartition & partition(*partitions[i]);
                const MatchingStats::Partition & s(stats.getPartition(i));
                partition.update(s);
            }
        } else {
            vespalib::string msg(make_string("Num partitions used '%ld' is larger than number of partitions '%ld' configured.",
                                             stats.getNumPartitions(), partitions.size()));
            throw vespalib::IllegalStateException(msg, VESPA_STRLOC);
        }
    }
}

LegacyDocumentDBMetrics::SubDBMetrics::DocumentMetaStoreMetrics::DocumentMetaStoreMetrics(MetricSet *parent)
    : MetricSet("docmetastore", "", "Document meta store metrics", parent),
      lidLimit("lidlimit", "", "The size of the allocated lid space", this),
      usedLids("usedlids", "", "The number of lids used", this),
      lowestFreeLid("lowestfreelid", "", "The lowest free lid", this),
      highestUsedLid("highestusedlid", "", "The highest used lid", this),
      lidBloatFactor("lidbloatfactor", "", "The bloat factor of this lid space, indicating the total amount of holes in the allocated lid space "
              "((lidlimit - usedlids) / lidlimit)", this),
      lidFragmentationFactor("lid_fragmentation_factor", "",
              "The fragmentation factor of this lid space, indicating the amount of holes in the currently used part of the lid space "
              "((highestusedlid - usedlids) / highestusedlid)", this)
{
}

LegacyDocumentDBMetrics::SubDBMetrics::DocumentMetaStoreMetrics::~DocumentMetaStoreMetrics() {}

LegacyDocumentDBMetrics::SubDBMetrics::SubDBMetrics(const vespalib::string &name, MetricSet *parent)
    : MetricSet(name, "", "Sub database metrics", parent),
      attributes(this),
      docMetaStore(this)
{ }

LegacyDocumentDBMetrics::SubDBMetrics::~SubDBMetrics() {}

LegacyDocumentDBMetrics::LegacyDocumentDBMetrics(const std::string &docTypeName, size_t maxNumThreads)
    : MetricSet(make_string("%s", docTypeName.c_str()), "", "Document DB Metrics", 0),
      index(this),
      attributes(this),
      docstore(this),
      matching(this),
      executor("executor", this),
      indexExecutor("indexexecutor", this),
      summaryExecutor("summaryexecutor", this),
      sessionManager(this),
      ready("ready", this),
      notReady("notready", this),
      removed("removed", this),
      memoryUsage("memoryusage", "", "Memory usage for this Document DB", this),
      numDocs("numdocs", "", "Number of ready/indexed documents in this Document DB (aka number of documents in the 'ready' sub db)", this),
      numActiveDocs("numactivedocs", "", "Number of active/searchable documents in this Document DB (aka number of active/searchable documents in the 'ready' sub db)", this),
      numIndexedDocs("numindexeddocs", "", "Number of ready/indexed documents in this Document DB (aka number of documents in the 'ready' sub db)", this),
      numStoredDocs("numstoreddocs", "", "Total number of documents stored in this Document DB (aka number of documents in the 'ready' and 'notready' sub dbs)", this),
      numRemovedDocs("numremoveddocs", "", "Number of removed documents in this Document DB (aka number of documents in the 'removed' sub db)", this),
      numBadConfigs("numBadConfigs", "", "Number of bad configs for this Document DB", this),
      _maxNumThreads(maxNumThreads)
{
    memoryUsage.addMetricToSum(index.memoryUsage);
    memoryUsage.addMetricToSum(attributes.memoryUsage);
    memoryUsage.addMetricToSum(docstore.memoryUsage);
}

LegacyDocumentDBMetrics::~LegacyDocumentDBMetrics() { }

} // namespace proton
