// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentdb_tagged_metrics.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace proton {

DocumentDBTaggedMetrics::JobMetrics::JobMetrics(metrics::MetricSet* parent)
    : MetricSet("job", "", "Job load average for various jobs in a document database", parent),
      attributeFlush("attribute_flush", "", "Flushing of attribute vector(s) to disk", this),
      memoryIndexFlush("memory_index_flush", "", "Flushing of memory index to disk", this),
      diskIndexFusion("disk_index_fusion", "", "Fusion of disk indexes", this),
      documentStoreFlush("document_store_flush", "", "Flushing of document store to disk", this),
      documentStoreCompact("document_store_compact", "",
              "Compaction of document store on disk", this),
      bucketMove("bucket_move", "",
              "Moving of buckets between 'ready' and 'notready' sub databases", this),
      lidSpaceCompact("lid_space_compact", "",
              "Compaction of lid space in document meta store and attribute vectors", this),
      removedDocumentsPrune("removed_documents_prune", "",
              "Pruning of removed documents in 'removed' sub database", this),
      total("total", "", "The job load average total of all job metrics", this)
{ }

DocumentDBTaggedMetrics::JobMetrics::~JobMetrics() { }

DocumentDBTaggedMetrics::SubDBMetrics::SubDBMetrics(const vespalib::string &name, MetricSet *parent)
    : MetricSet(name, "", "Sub database metrics", parent),
      lidSpace(this),
      documentStore(this),
      attributes(this)
{ }

DocumentDBTaggedMetrics::SubDBMetrics::~SubDBMetrics() { }

DocumentDBTaggedMetrics::SubDBMetrics::LidSpaceMetrics::LidSpaceMetrics(MetricSet *parent)
    : MetricSet("lid_space", "", "Local document id (lid) space metrics for this document sub DB", parent),
      lidLimit("lid_limit", "", "The size of the allocated lid space", this),
      usedLids("used_lids", "", "The number of lids used", this),
      lowestFreeLid("lowest_free_lid", "", "The lowest free lid", this),
      highestUsedLid("highest_used_lid", "", "The highest used lid", this),
      lidBloatFactor("lid_bloat_factor", "", "The bloat factor of this lid space, indicating the total amount of holes in the allocated lid space "
              "((lid_limit - used_lids) / lid_limit)", this),
      lidFragmentationFactor("lid_fragmentation_factor", "",
              "The fragmentation factor of this lid space, indicating the amount of holes in the currently used part of the lid space "
              "((highest_used_lid - used_lids) / highest_used_lid)", this)
{ }

DocumentDBTaggedMetrics::SubDBMetrics::LidSpaceMetrics::~LidSpaceMetrics() { }

DocumentDBTaggedMetrics::SubDBMetrics::DocumentStoreMetrics::DocumentStoreMetrics(MetricSet *parent)
    : MetricSet("document_store", "", "document store metrics for this document sub DB", parent),
      diskUsage("disk_usage", "", "Disk space usage in bytes", this),
      diskBloat("disk_bloat", "", "Disk space bloat in bytes", this),
      maxBucketSpread("max_bucket_spread", "", "Max bucket spread in underlying files (sum(unique buckets in each chunk)/unique buckets in file)", this),
      memoryUsage(this)
{ }

DocumentDBTaggedMetrics::SubDBMetrics::DocumentStoreMetrics::~DocumentStoreMetrics() { }

DocumentDBTaggedMetrics::AttributeMetrics::AttributeMetrics(MetricSet *parent)
    : MetricSet("attribute", "", "Attribute vector metrics for this document db", parent),
      resourceUsage(this)
{ }

DocumentDBTaggedMetrics::AttributeMetrics::~AttributeMetrics() { }

DocumentDBTaggedMetrics::AttributeMetrics::ResourceUsageMetrics::ResourceUsageMetrics(MetricSet *parent)
    : MetricSet("resource_usage", "", "Usage metrics for various attribute vector resources", parent),
      enumStore("enum_store", "", "The highest relative amount of enum store address space used among "
              "all enumerated attribute vectors in this document db (value in the range [0, 1])", this),
      multiValue("multi_value", "", "The highest relative amount of multi-value address space used among "
              "all multi-value attribute vectors in this document db (value in the range [0, 1])", this),
      feedingBlocked("feeding_blocked", "", "Whether feeding is blocked due to attribute resource limits being reached (value is either 0 or 1)", this)
{
}

DocumentDBTaggedMetrics::AttributeMetrics::ResourceUsageMetrics::~ResourceUsageMetrics() { }

DocumentDBTaggedMetrics::IndexMetrics::IndexMetrics(MetricSet *parent)
    : MetricSet("index", "", "Index metrics (memory and disk) for this document db", parent),
      diskUsage("disk_usage", "", "Disk space usage in bytes", this),
      memoryUsage(this)
{ }

DocumentDBTaggedMetrics::IndexMetrics::~IndexMetrics() { }

DocumentDBTaggedMetrics::DocumentDBTaggedMetrics(const vespalib::string &docTypeName)
    : MetricSet("documentdb", {{"documenttype", docTypeName}}, "Document DB metrics", nullptr),
      job(this),
      attribute(this),
      index(this),
      ready("ready", this),
      notReady("notready", this),
      removed("removed", this),
      threadingService("threading_service", this)
{ }

DocumentDBTaggedMetrics::~DocumentDBTaggedMetrics() { }

} // namespace proton
