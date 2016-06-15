// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::FileStorMetrics
 * @ingroup filestorage
 *
 * @brief Metrics for the file store threads.
 *
 * @version $Id$
 */

#pragma once

#include <vespa/metrics/metrics.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>

namespace storage {

struct FileStorThreadMetrics : public metrics::MetricSet
{
    typedef std::shared_ptr<FileStorThreadMetrics> SP;

    struct Op : public metrics::MetricSet {
        std::string _name;
        metrics::LongCountMetric count;
        metrics::LongAverageMetric latency;
        metrics::LongCountMetric failed;

        Op(const std::string& id, const std::string name,
           metrics::MetricSet* owner = 0)
            : MetricSet(id,
                        id,
                        name + " load in filestor thread",
                        owner,
                        "operationtype"),
              _name(name),
              count("count",
                    "yamasdefault",
                    "Number of requests processed.",
                    this),
              latency("latency",
                      "yamasdefault",
                      "Latency of successful requests.",
                      this),
              failed("failed",
                     "yamasdefault",
                     "Number of failed requests.",
                     this)
        {
        }

        virtual Metric* clone(std::vector<Metric::LP>& ownerList,
                              CopyType copyType,
                              metrics::MetricSet* owner,
                              bool includeUnused) const
        {
            if (copyType == INACTIVE) {
                return MetricSet::clone(
                        ownerList, INACTIVE, owner, includeUnused);
            }
            return (Op*) (new Op(getName(), _name, owner))->assignValues(*this);
        }
        Op* operator&() { return this; }
    };
    struct OpWithNotFound : public Op {
        metrics::LongCountMetric notFound;

        OpWithNotFound(const std::string& id, const std::string name,
                       metrics::MetricSet* owner = 0)
            : Op(id, name, owner),
              notFound("not_found", "", "Number of requests that could not be "
                       "completed due to source document not found.", this)
        {
        }

        virtual Metric* clone(std::vector<Metric::LP>& ownerList,
                              CopyType copyType,
                              metrics::MetricSet* owner,
                              bool includeUnused) const
        {
            if (copyType == INACTIVE) {
                return MetricSet::clone(
                        ownerList, INACTIVE, owner, includeUnused);
            }
            return (OpWithNotFound*)
                    (new OpWithNotFound(getName(), _name, owner))
                        ->assignValues(*this);
        }
        OpWithNotFound* operator&() { return this; }
    };

    struct Update : public OpWithNotFound {
        metrics::LongAverageMetric latencyRead;

        Update(metrics::MetricSet* owner = 0)
            : OpWithNotFound("update", "Update", owner),
              latencyRead("latency_read", "", "Latency of the source read in "
                          "the request.", this)
        {
        }

        virtual Metric* clone(std::vector<Metric::LP>& ownerList,
                              CopyType copyType,
                              metrics::MetricSet* owner,
                              bool includeUnused) const
        {
            if (copyType == INACTIVE) {
                return MetricSet::clone(
                        ownerList, INACTIVE, owner, includeUnused);
            }
            return (Update*) (new Update(owner))->assignValues(*this);
        }
        Update* operator&() { return this; }
    };

    struct Visitor : public Op {
        metrics::LongAverageMetric documentsPerIterate;

        Visitor(metrics::MetricSet* owner = 0)
            : Op("visit", "Visit", owner),
              documentsPerIterate("docs", "", "Number of entries read per iterate call",
                                  this)
        {
        }

        virtual Metric* clone(std::vector<Metric::LP>& ownerList,
                              CopyType copyType,
                              metrics::MetricSet* owner,
                              bool includeUnused) const
        {
            if (copyType == INACTIVE) {
                return MetricSet::clone(
                        ownerList, INACTIVE, owner, includeUnused);
            }
            return (Visitor*) (new Visitor(owner))->assignValues(*this);
        }
        Visitor* operator&() { return this; }
    };

    metrics::LongCountMetric operations;
    metrics::LongCountMetric failedOperations;
    metrics::LoadMetric<Op> put;
    metrics::LoadMetric<OpWithNotFound> get;
    metrics::LoadMetric<OpWithNotFound> remove;
    metrics::LoadMetric<Op> removeLocation;
    metrics::LoadMetric<Op> statBucket;
    metrics::LoadMetric<Update> update;
    metrics::LoadMetric<OpWithNotFound> revert;
    Op createIterator;
    metrics::LoadMetric<Visitor> visit;
    metrics::LoadMetric<Op> multiOp;
    Op createBuckets;
    Op deleteBuckets;
    Op repairs;
    metrics::LongCountMetric repairFixed;
    Op recheckBucketInfo;
    Op splitBuckets;
    Op joinBuckets;
    Op setBucketStates;
    Op movedBuckets;
    Op readBucketList;
    Op readBucketInfo;
    Op internalJoin;
    Op mergeBuckets;
    Op getBucketDiff;
    Op applyBucketDiff;

    metrics::LongCountMetric bytesMerged;
    metrics::LongCountMetric getBucketDiffReply;
    metrics::LongCountMetric applyBucketDiffReply;
    metrics::LongAverageMetric mergeLatencyTotal;
    metrics::LongAverageMetric mergeMetadataReadLatency;
    metrics::LongAverageMetric mergeDataReadLatency;
    metrics::LongAverageMetric mergeDataWriteLatency;
    metrics::DoubleAverageMetric mergeAverageDataReceivedNeeded;
    metrics::LongAverageMetric batchingSize;

    FileStorThreadMetrics(const std::string& name, const std::string& desc,
                          const metrics::LoadTypeSet& lt)
        : metrics::MetricSet(name, "filestor partofsum thread", desc, NULL, "thread"),
          operations("operations", "",
                  "Number of operations processed.", this),
          failedOperations("failedoperations", "",
                  "Number of operations throwing exceptions.", this),

          put(lt, *&Op("put", "Put"), this),
          get(lt, *&OpWithNotFound("get", "Get"), this),
          remove(lt, *&OpWithNotFound("remove", "Remove"), this),
          removeLocation(lt, *&Op("remove_location", "Remove location"), this),
          statBucket(lt, *&Op("stat_bucket", "Stat bucket"), this),
          update(lt, *&Update(), this),
          revert(lt, *&OpWithNotFound("revert", "Revert"), this),
          createIterator("createiterator", "", this),
          visit(lt, *&Visitor(), this),
          multiOp(lt, *&Op("multioperations",
                           "The number of multioperations that have been created"), this),
          createBuckets("createbuckets",
                        "Number of buckets that has been created.", this),
          deleteBuckets("deletebuckets",
                        "Number of buckets that has been deleted.", this),
          repairs("bucketverified", "Number of times buckets have been checked.", this),
          repairFixed("bucketfixed", "",
                  "Number of times bucket has been fixed because of "
                  "corruption", this),
          recheckBucketInfo("recheckbucketinfo",
                            "Number of times bucket info has been explicitly "
                            "rechecked due to buckets being marked modified by "
                            "the persistence provider",
                            this),
          splitBuckets("splitbuckets",
                  "Number of times buckets have been split.", this),
          joinBuckets("joinbuckets",
                  "Number of times buckets have been joined.", this),
          setBucketStates("setbucketstates",
                  "Number of times buckets have been activated or deactivated.", this),
          movedBuckets("movedbuckets",
                  "Number of buckets moved between disks", this),
          readBucketList("readbucketlist",
                  "Number of read bucket list requests", this),
          readBucketInfo("readbucketinfo",
                  "Number of read bucket info requests", this),
          internalJoin("internaljoin",
                 "Number of joins to join buckets on multiple disks during "
                 "storage initialization.", this),
          mergeBuckets("mergebuckets",
                 "Number of times buckets have been merged.", this),
          getBucketDiff("getbucketdiff",
                 "Number of getbucketdiff commands that have been processed.", this),
          applyBucketDiff("applybucketdiff",
                 "Number of applybucketdiff commands that have been processed.", this),
          bytesMerged("bytesmerged", "",
                 "Total number of bytes merged into this node.", this),
          getBucketDiffReply("getbucketdiffreply", "",
                 "Number of getbucketdiff replies that have been processed.", this),
          applyBucketDiffReply("applybucketdiffreply", "",
                 "Number of applybucketdiff replies that have been processed.", this),
          mergeLatencyTotal("mergelatencytotal", "",
                 "Latency of total merge operation, from master node receives "
                 "it, until merge is complete and master node replies.", this),
          mergeMetadataReadLatency("mergemetadatareadlatency", "",
                 "Latency of time used in a merge step to check metadata of "
                 "current node to see what data it has.", this),
          mergeDataReadLatency("mergedatareadlatency", "",
                 "Latency of time used in a merge step to read data other "
                 "nodes need.", this),
          mergeDataWriteLatency("mergedatawritelatency", "",
                "Latency of time used in a merge step to write data needed to "
                "current node.", this),
          mergeAverageDataReceivedNeeded("mergeavgdatareceivedneeded",
                                         "",
                                         "Amount of data transferred from previous node "
                                         "in chain that "
                                         "we needed to apply locally.", this),
          batchingSize("batchingsize",
                       "",
                       "Number of operations batched per bucket (only counts "
                       "batches of size > 1)", this)
    {
    }

};

class FileStorDiskMetrics : public metrics::MetricSet
{
public:
    typedef std::shared_ptr<FileStorDiskMetrics> SP;

    std::vector<FileStorThreadMetrics::SP> threads;
    metrics::SumMetric<MetricSet> sum;
    metrics::LongAverageMetric queueSize;
    metrics::LoadMetric<metrics::LongAverageMetric> averageQueueWaitingTime;
    metrics::LongAverageMetric pendingMerges;
    metrics::DoubleAverageMetric waitingForLockHitRate;
    metrics::LongAverageMetric lockWaitTime;

    FileStorDiskMetrics(const std::string& name,
                        const std::string& description,
                        const metrics::LoadTypeSet& loadTypes,
                        metrics::MetricSet* owner)
        : MetricSet(name, "partofsum disk", description, owner, "disk"),
          sum("allthreads", "sum", "", this),
          queueSize("queuesize", "", "Size of input message queue.", this),
          averageQueueWaitingTime(loadTypes, metrics::LongAverageMetric(
                  "averagequeuewait", "",
                  "Average time an operation spends in input queue."), this),
          pendingMerges("pendingmerge", "",
                  "Number of buckets currently being merged.", this),
          waitingForLockHitRate("waitingforlockrate", "",
                  "Amount of times a filestor thread has needed to wait for "
                  "lock to take next message in queue.", this),
          lockWaitTime("lockwaittime", "",
                  "Amount of time waiting used waiting for lock.", this)
    {
        pendingMerges.unsetOnZeroValue();
        waitingForLockHitRate.unsetOnZeroValue();
    }

    void initDiskMetrics(const metrics::LoadTypeSet& loadTypes,
                         uint32_t threadsPerDisk)
    {
        threads.clear();
        threads.resize(threadsPerDisk);
        for (uint32_t i=0; i<threadsPerDisk; ++i) {
            std::ostringstream desc;
            std::ostringstream name;
            name << "thread" << i;
            desc << "Thread " << i << '/' << threadsPerDisk;
            threads[i]
                = std::shared_ptr<FileStorThreadMetrics>(
                        new FileStorThreadMetrics(name.str(), desc.str(),
                                                  loadTypes));
            registerMetric(*threads[i]);
            sum.addMetricToSum(*threads[i]);
        }
    }
};

struct FileStorMetrics : public metrics::MetricSet
{
    std::vector<FileStorDiskMetrics::SP> disks;
    metrics::SumMetric<MetricSet> sum;
    metrics::LongCountMetric directoryEvents;
    metrics::LongCountMetric partitionEvents;
    metrics::LongCountMetric diskEvents;

    FileStorMetrics(const metrics::LoadTypeSet&)
        : metrics::MetricSet("filestor", "filestor", ""),
          sum("alldisks", "sum", "", this),
          directoryEvents("directoryevents", "",
                  "Number of directory events received.", this),
          partitionEvents("partitionevents", "",
                  "Number of partition events received.", this),
          diskEvents("diskevents", "",
                  "Number of disk events received.", this)
    {
    }

    void initDiskMetrics(uint16_t numDisks,
                         const metrics::LoadTypeSet& loadTypes,
                         uint32_t threadsPerDisk)
    {
        if (!disks.empty()) {
            throw vespalib::IllegalStateException(
                    "Can't initialize disks twice", VESPA_STRLOC);
        }
        disks.clear();
        disks.resize(numDisks);
        for (uint32_t i=0; i<numDisks; ++i) {
            // Currently FileStorHandlerImpl expects metrics to exist for
            // disks that are not in use too.
            std::ostringstream desc;
            std::ostringstream name;
            name << "disk_" << i;
            desc << "Disk " << i;
            disks[i] = FileStorDiskMetrics::SP(new FileStorDiskMetrics(
                            name.str(), desc.str(), loadTypes, this));
            sum.addMetricToSum(*disks[i]);
            disks[i]->initDiskMetrics(loadTypes, threadsPerDisk);
        }
    }
};

}

