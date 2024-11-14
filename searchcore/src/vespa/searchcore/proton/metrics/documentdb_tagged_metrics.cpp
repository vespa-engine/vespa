// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentdb_tagged_metrics.h"
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.metrics.documentdb_tagged_metrics");

namespace proton {

using matching::MatchingStats;

DocumentDBTaggedMetrics::JobMetrics::JobMetrics(metrics::MetricSet* parent)
    : MetricSet("job", {}, "Job load average for various jobs in a document database", parent),
      attributeFlush("attribute_flush", {}, "Flushing of attribute vector(s) to disk", this),
      memoryIndexFlush("memory_index_flush", {}, "Flushing of memory index to disk", this),
      diskIndexFusion("disk_index_fusion", {}, "Fusion of disk indexes", this),
      documentStoreFlush("document_store_flush", {}, "Flushing of document store to disk", this),
      documentStoreCompact("document_store_compact", {},
              "Compaction of document store on disk", this),
      bucketMove("bucket_move", {},
              "Moving of buckets between 'ready' and 'notready' sub databases", this),
      lidSpaceCompact("lid_space_compact", {},
              "Compaction of lid space in document meta store and attribute vectors", this),
      removedDocumentsPrune("removed_documents_prune", {},
              "Pruning of removed documents in 'removed' sub database", this),
      total("total", {}, "The job load average total of all job metrics", this)
{
}

DocumentDBTaggedMetrics::JobMetrics::~JobMetrics() = default;

DocumentDBTaggedMetrics::SubDBMetrics::SubDBMetrics(const std::string &name, MetricSet *parent)
    : MetricSet(name, {}, "Sub database metrics", parent),
      lidSpace(this),
      documentStore(this),
      attributes(this),
      index(this)
{
}

DocumentDBTaggedMetrics::SubDBMetrics::~SubDBMetrics() = default;

DocumentDBTaggedMetrics::SubDBMetrics::LidSpaceMetrics::LidSpaceMetrics(MetricSet *parent)
    : MetricSet("lid_space", {}, "Local document id (lid) space metrics for this document sub DB", parent),
      lidLimit("lid_limit", {}, "The size of the allocated lid space", this),
      usedLids("used_lids", {}, "The number of lids used", this),
      lowestFreeLid("lowest_free_lid", {}, "The lowest free lid", this),
      highestUsedLid("highest_used_lid", {}, "The highest used lid", this),
      lidBloatFactor("lid_bloat_factor", {}, "The bloat factor of this lid space, indicating the total amount of holes in the allocated lid space "
              "((lid_limit - used_lids) / lid_limit)", this),
      lidFragmentationFactor("lid_fragmentation_factor", {},
              "The fragmentation factor of this lid space, indicating the amount of holes in the currently used part of the lid space "
              "((highest_used_lid - used_lids) / highest_used_lid)", this)
{
}

DocumentDBTaggedMetrics::SubDBMetrics::LidSpaceMetrics::~LidSpaceMetrics() = default;

DocumentDBTaggedMetrics::SubDBMetrics::DocumentStoreMetrics::DocumentStoreMetrics(MetricSet *parent)
    : MetricSet("document_store", {}, "Document store metrics for this document sub DB", parent),
      diskUsage("disk_usage", {}, "Disk space usage in bytes", this),
      diskBloat("disk_bloat", {}, "Disk space bloat in bytes", this),
      maxBucketSpread("max_bucket_spread", {}, "Max bucket spread in underlying files (sum(unique buckets in each chunk)/unique buckets in file)", this),
      memoryUsage(this),
      cache(this, "cache", "Document store cache metrics", "Document store")
{
}

DocumentDBTaggedMetrics::SubDBMetrics::DocumentStoreMetrics::~DocumentStoreMetrics() = default;

DocumentDBTaggedMetrics::AttributeMetrics::AttributeMetrics(MetricSet *parent)
    : MetricSet("attribute", {}, "Attribute vector metrics for this document db", parent),
      diskUsage("disk_usage", {}, "Disk space usage in bytes", this),
      resourceUsage(this),
      totalMemoryUsage(this)
{
}

DocumentDBTaggedMetrics::AttributeMetrics::~AttributeMetrics() = default;

DocumentDBTaggedMetrics::AttributeMetrics::ResourceUsageMetrics::ResourceUsageMetrics(MetricSet *parent)
    : MetricSet("resource_usage", {}, "Metrics for various attribute vector resources usage", parent),
      address_space("address_space", {}, "The max relative address space used among "
              "components in all attribute vectors in this document db (value in the range [0, 1])", this),
      feedingBlocked("feeding_blocked", {}, "Whether feeding is blocked due to attribute resource limits being reached (value is either 0 or 1)", this)
{
}

DocumentDBTaggedMetrics::AttributeMetrics::ResourceUsageMetrics::~ResourceUsageMetrics() = default;

DocumentDBTaggedMetrics::IndexMetrics::IndexMetrics(MetricSet *parent)
    : MetricSet("index", {}, "Index metrics (memory and disk) for this document db", parent),
      diskUsage("disk_usage", {}, "Disk space usage in bytes", this),
      memoryUsage(this),
      docsInMemory("docs_in_memory", {}, "Number of documents in memory index", this)
{
}

DocumentDBTaggedMetrics::IndexMetrics::~IndexMetrics() = default;

void
DocumentDBTaggedMetrics::MatchingMetrics::update(const MatchingStats &stats)
{
    docsMatched.inc(stats.docsMatched());
    docsRanked.inc(stats.docsRanked());
    docsReRanked.inc(stats.docsReRanked());
    softDoomedQueries.inc(stats.softDoomed());
    queries.inc(stats.queries());
    querySetupTime.addValueBatch(stats.querySetupTimeAvg(), stats.querySetupTimeCount(),
                                      stats.querySetupTimeMin(), stats.querySetupTimeMax());
    queryLatency.addValueBatch(stats.queryLatencyAvg(), stats.queryLatencyCount(),
                               stats.queryLatencyMin(), stats.queryLatencyMax());
}

DocumentDBTaggedMetrics::MatchingMetrics::MatchingMetrics(MetricSet *parent)
    : MetricSet("matching", {}, "Matching metrics", parent),
      docsMatched("docs_matched", {}, "Number of documents matched", this),
      docsRanked("docs_ranked", {}, "Number of documents ranked (first phase)", this),
      docsReRanked("docs_reranked", {}, "Number of documents re-ranked (second phase)", this),
      queries("queries", {}, "Number of queries executed", this),
      softDoomedQueries("soft_doomed_queries", {}, "Number of queries hitting the soft timeout", this),
      querySetupTime("query_setup_time", {}, "Average time (sec) spent setting up and tearing down queries", this),
      queryLatency("query_latency", {}, "Total average latency (sec) when matching and ranking a query", this)
{
}

DocumentDBTaggedMetrics::MatchingMetrics::~MatchingMetrics() = default;

DocumentDBTaggedMetrics::MatchingMetrics::RankProfileMetrics::RankProfileMetrics(const std::string &name,
                                                                                 size_t numDocIdPartitions,
                                                                                 MetricSet *parent)
    : MetricSet("rank_profile", {{"rankProfile", name}}, "Rank profile metrics", parent),
      docsMatched("docs_matched", {}, "Number of documents matched", this),
      docsRanked("docs_ranked", {}, "Number of documents ranked (first phase)", this),
      docsReRanked("docs_reranked", {}, "Number of documents re-ranked (second phase)", this),
      queries("queries", {}, "Number of queries executed", this),
      limitedQueries("limited_queries", {}, "Number of queries limited in match phase", this),
      softDoomedQueries("soft_doomed_queries", {}, "Number of queries hitting the soft timeout", this),
      softDoomFactor("soft_doom_factor", {}, "Factor used to compute soft-timeout", this),
      matchTime("match_time", {}, "Average time (sec) for matching a query (1st phase)", this),
      groupingTime("grouping_time", {}, "Average time (sec) spent on grouping", this),
      rerankTime("rerank_time", {}, "Average time (sec) spent on 2nd phase ranking", this),
      querySetupTime("query_setup_time", {}, "Average time (sec) spent setting up and tearing down queries", this),
      queryLatency("query_latency", {}, "Total average latency (sec) when matching and ranking a query", this)
{
    softDoomFactor.set(MatchingStats::INITIAL_SOFT_DOOM_FACTOR);
    for (size_t i = 0; i < numDocIdPartitions; ++i) {
        std::string partition(vespalib::make_string("docid_part%02ld", i));
        partitions.push_back(std::make_unique<DocIdPartition>(partition, this));
    }
}

DocumentDBTaggedMetrics::MatchingMetrics::RankProfileMetrics::~RankProfileMetrics() = default;

DocumentDBTaggedMetrics::MatchingMetrics::RankProfileMetrics::DocIdPartition::DocIdPartition(const std::string &name, MetricSet *parent)
    : MetricSet("docid_partition", {{"docidPartition", name}}, "DocId Partition profile metrics", parent),
      docsMatched("docs_matched", {}, "Number of documents matched", this),
      docsRanked("docs_ranked", {}, "Number of documents ranked (first phase)", this),
      docsReRanked("docs_reranked", {}, "Number of documents re-ranked (second phase)", this),
      activeTime("active_time", {}, "Time (sec) spent doing actual work", this),
      waitTime("wait_time", {}, "Time (sec) spent waiting for other external threads and resources", this)
{ }

DocumentDBTaggedMetrics::MatchingMetrics::RankProfileMetrics::DocIdPartition::~DocIdPartition() = default;

void
DocumentDBTaggedMetrics::MatchingMetrics::RankProfileMetrics::DocIdPartition::update(const MatchingStats::Partition &stats)
{
    docsMatched.inc(stats.docsMatched());
    docsRanked.inc(stats.docsRanked());
    docsReRanked.inc(stats.docsReRanked());
    activeTime.addValueBatch(stats.active_time_avg(), stats.active_time_count(),
                             stats.active_time_min(), stats.active_time_max());
    waitTime.addValueBatch(stats.wait_time_avg(), stats.wait_time_count(),
                           stats.wait_time_min(), stats.wait_time_max());
}

void
DocumentDBTaggedMetrics::MatchingMetrics::RankProfileMetrics::update(const metrics::MetricLockGuard &,
                                                                     const MatchingStats &stats)
{
    docsMatched.inc(stats.docsMatched());
    docsRanked.inc(stats.docsRanked());
    docsReRanked.inc(stats.docsReRanked());
    queries.inc(stats.queries());
    limitedQueries.inc(stats.limited_queries());
    softDoomedQueries.inc(stats.softDoomed());
    softDoomFactor.set(stats.softDoomFactor());
    matchTime.addValueBatch(stats.matchTimeAvg(), stats.matchTimeCount(),
                            stats.matchTimeMin(), stats.matchTimeMax());
    groupingTime.addValueBatch(stats.groupingTimeAvg(), stats.groupingTimeCount(),
                               stats.groupingTimeMin(), stats.groupingTimeMax());
    rerankTime.addValueBatch(stats.rerankTimeAvg(), stats.rerankTimeCount(),
                             stats.rerankTimeMin(), stats.rerankTimeMax());
    querySetupTime.addValueBatch(stats.querySetupTimeAvg(), stats.querySetupTimeCount(),
                                      stats.querySetupTimeMin(), stats.querySetupTimeMax());
    queryLatency.addValueBatch(stats.queryLatencyAvg(), stats.queryLatencyCount(),
                               stats.queryLatencyMin(), stats.queryLatencyMax());
    if (stats.getNumPartitions() > 0) {
        for (size_t i = partitions.size(); i < stats.getNumPartitions(); ++i) {
            // This loop is to handle live reconfigs that changes how many partitions(number of threads) might be used per query.
            std::string partition(vespalib::make_string("docid_part%02ld", i));
            partitions.push_back(std::make_unique<DocIdPartition>(partition, this));
            LOG(info, "Number of partitions has been increased to '%ld' from '%ld' previously configured. Adding part %ld",
                stats.getNumPartitions(), partitions.size(), i);
        }
        for (size_t i = 0; i < stats.getNumPartitions(); ++i) {
            partitions[i]->update(stats.getPartition(i));
        }
    }
}

DocumentDBTaggedMetrics::DocumentsMetrics::DocumentsMetrics(metrics::MetricSet *parent)
    : metrics::MetricSet("documents", {}, "Metrics for various document counts in this document db", parent),
      active("active", {}, "The number of active / searchable documents in this document db", this),
      ready("ready", {}, "The number of ready documents in this document db", this),
      total("total", {}, "The total number of documents in this documents db (ready + not-ready)", this),
      removed("removed", {}, "The number of removed documents in this document db", this)
{
}

DocumentDBTaggedMetrics::DocumentsMetrics::~DocumentsMetrics() = default;

DocumentDBTaggedMetrics::BucketMoveMetrics::BucketMoveMetrics(metrics::MetricSet *parent)
        : metrics::MetricSet("bucket_move", {}, "Metrics for bucket move job in this document db", parent),
          bucketsPending("buckets_pending", {}, "The number of buckets left to move", this)
{ }

DocumentDBTaggedMetrics::BucketMoveMetrics::~BucketMoveMetrics() = default;

DocumentDBTaggedMetrics::DocumentDBTaggedMetrics(const std::string &docTypeName, size_t maxNumThreads_)
    : MetricSet("documentdb", {{"documenttype", docTypeName}}, "Document DB metrics", nullptr),
      job(this),
      attribute(this),
      index(this),
      ready("ready", this),
      notReady("notready", this),
      removed("removed", this),
      threadingService("threading_service", this),
      matching(this),
      documents(this),
      bucketMove(this),
      feeding(this),
      totalMemoryUsage(this),
      totalDiskUsage("disk_usage", {}, "The total disk usage (in bytes) for this document db", this),
      heart_beat_age("heart_beat_age", {}, "How long ago (in seconds) heart beat maintenace job was run", this),
      maxNumThreads(maxNumThreads_)
{
}

DocumentDBTaggedMetrics::~DocumentDBTaggedMetrics() = default;

}
