// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "filestormetrics.h"
#include <vespa/metrics/summetric.hpp>
#include <sstream>

namespace storage {

using metrics::MetricSet;

// TODO Vespa 8 all metrics with .sum in the name should have that removed.
FileStorThreadMetrics::Op::Op(const std::string& id, const std::string& name, MetricSet* owner)
    : MetricSet(id, {}, name + " load in filestor thread", owner),
      _name(name),
      count("count", {{"yamasdefault"}}, "Number of requests processed.", this),
      latency("latency", {{"yamasdefault"}}, "Latency of successful requests.", this),
      failed("failed", {{"yamasdefault"}}, "Number of failed requests.", this)
{ }

FileStorThreadMetrics::Op::~Op() = default;

MetricSet *
FileStorThreadMetrics::Op::clone(std::vector<Metric::UP>& ownerList,
                                 CopyType copyType,
                                 MetricSet* owner,
                                 bool includeUnused) const
{
    if (copyType == INACTIVE) {
        return MetricSet::clone(ownerList, INACTIVE, owner, includeUnused);
    }
    return (Op*) (new Op(getName(), _name, owner))->assignValues(*this);
}

template <typename BaseOp>
FileStorThreadMetrics::OpWithRequestSize<BaseOp>::OpWithRequestSize(const std::string& id, const std::string& name, MetricSet* owner)
        : BaseOp(id, name, owner),
          request_size("request_size", {}, "Size of requests, in bytes", this)
{
}

template <typename BaseOp>
FileStorThreadMetrics::OpWithRequestSize<BaseOp>::~OpWithRequestSize() = default;

// FIXME this has very non-intuitive semantics, ending up with copy&paste patterns
template <typename BaseOp>
MetricSet*
FileStorThreadMetrics::OpWithRequestSize<BaseOp>::clone(
        std::vector<Metric::UP>& ownerList,
        CopyType copyType,
        MetricSet* owner,
        bool includeUnused) const
{
    if (copyType == INACTIVE) {
        return MetricSet::clone(ownerList, INACTIVE, owner, includeUnused);
    }
    return static_cast<OpWithRequestSize<BaseOp>*>((new OpWithRequestSize<BaseOp>(this->getName(), this->_name, owner))
            ->assignValues(*this));
}

template <typename BaseOp>
FileStorThreadMetrics::OpWithTestAndSetFailed<BaseOp>::OpWithTestAndSetFailed(const std::string& id, const std::string& name, MetricSet* owner)
    : BaseOp(id, name, owner),
      test_and_set_failed("test_and_set_failed", {{"yamasdefault"}},
                           "Number of times operations were failed due to a "
                           "test-and-set condition mismatch", this)
{
}

template <typename BaseOp>
FileStorThreadMetrics::OpWithTestAndSetFailed<BaseOp>::~OpWithTestAndSetFailed() = default;

// FIXME this has very non-intuitive semantics, ending up with copy&paste patterns (yet again...)
template <typename BaseOp>
MetricSet*
FileStorThreadMetrics::OpWithTestAndSetFailed<BaseOp>::clone(
        std::vector<Metric::UP>& ownerList,
        CopyType copyType,
        MetricSet* owner,
        bool includeUnused) const
{
    if (copyType == INACTIVE) {
        return MetricSet::clone(ownerList, INACTIVE, owner, includeUnused);
    }
    return static_cast<OpWithTestAndSetFailed<BaseOp>*>((new OpWithTestAndSetFailed<BaseOp>(this->getName(), this->_name, owner))
            ->assignValues(*this));
}

FileStorThreadMetrics::OpWithNotFound::OpWithNotFound(const std::string& id, const std::string& name, MetricSet* owner)
    : Op(id, name, owner),
      notFound("not_found", {},
               "Number of requests that could not be completed due to source document not found.",
               this)
{ }

FileStorThreadMetrics::OpWithNotFound::~OpWithNotFound() = default;

MetricSet *
FileStorThreadMetrics::OpWithNotFound::clone(std::vector<Metric::UP>& ownerList,
                                             CopyType copyType,
                                             MetricSet* owner,
                                             bool includeUnused) const
{
    if (copyType == INACTIVE) {
        return MetricSet::clone(ownerList, INACTIVE, owner, includeUnused);
    }
    return (OpWithNotFound*)
            (new OpWithNotFound(getName(), _name, owner))
                ->assignValues(*this);
}

FileStorThreadMetrics::Update::Update(MetricSet* owner)
    : OpWithTestAndSetFailed("update.sum", "Update", owner),
      latencyRead("latency_read", {}, "Latency of the source read in the request.", this)
{ }

FileStorThreadMetrics::Update::~Update() = default;

MetricSet *
FileStorThreadMetrics::Update::clone(std::vector<Metric::UP>& ownerList,
                                     CopyType copyType,
                                     MetricSet* owner,
                                     bool includeUnused) const
{
    if (copyType == INACTIVE) {
        return MetricSet::clone(ownerList, INACTIVE, owner, includeUnused);
    }
    return (Update*) (new Update(owner))->assignValues(*this);
}

FileStorThreadMetrics::Visitor::Visitor(MetricSet* owner)
    : Op("visit.sum", "Visit", owner),
      documentsPerIterate("docs", {}, "Number of entries read per iterate call", this)
{ }

FileStorThreadMetrics::Visitor::~Visitor() = default;

MetricSet *
FileStorThreadMetrics::Visitor::clone(std::vector<Metric::UP>& ownerList,
                                      CopyType copyType,
                                      MetricSet* owner,
                                      bool includeUnused) const
{
    if (copyType == INACTIVE) {
        return MetricSet::clone(ownerList, INACTIVE, owner, includeUnused);
    }
    return (Visitor*) (new Visitor(owner))->assignValues(*this);
}

FileStorThreadMetrics::FileStorThreadMetrics(const std::string& name, const std::string& desc)
    : MetricSet(name, {{"filestor"},{"partofsum"}}, desc),
      operations("operations", {}, "Number of operations processed.", this),
      failedOperations("failedoperations", {}, "Number of operations throwing exceptions.", this),
      put("put.sum", "Put", this),
      get("get.sum", "Get", this),
      remove("remove.sum", "Remove", this),
      removeLocation("remove_location.sum", "Remove location", this),
      statBucket("stat_bucket", "Stat bucket", this),
      update(this),
      revert("revert", "Revert", this),
      createIterator("createiterator", {}, this),
      visit(this),
      multiOp("multioperations", "The number of multioperations that have been created", this),
      createBuckets("createbuckets", "Number of buckets that has been created.", this),
      deleteBuckets("deletebuckets", "Number of buckets that has been deleted.", this),
      repairs("bucketverified", "Number of times buckets have been checked.", this),
      repairFixed("bucketfixed", {}, "Number of times bucket has been fixed because of corruption", this),
      recheckBucketInfo("recheckbucketinfo",
                        "Number of times bucket info has been explicitly "
                        "rechecked due to buckets being marked modified by "
                        "the persistence provider",
                        this),
      splitBuckets("splitbuckets", "Number of times buckets have been split.", this),
      joinBuckets("joinbuckets", "Number of times buckets have been joined.", this),
      setBucketStates("setbucketstates", "Number of times buckets have been activated or deactivated.", this),
      movedBuckets("movedbuckets", "Number of buckets moved between disks", this),
      readBucketList("readbucketlist", "Number of read bucket list requests", this),
      readBucketInfo("readbucketinfo", "Number of read bucket info requests", this),
      internalJoin("internaljoin", "Number of joins to join buckets on multiple disks during "
                                   "storage initialization.", this),
      mergeBuckets("mergebuckets", "Number of times buckets have been merged.", this),
      getBucketDiff("getbucketdiff", "Number of getbucketdiff commands that have been processed.", this),
      applyBucketDiff("applybucketdiff", "Number of applybucketdiff commands that have been processed.", this),
      getBucketDiffReply("getbucketdiffreply", {}, "Number of getbucketdiff replies that have been processed.", this),
      applyBucketDiffReply("applybucketdiffreply", {}, "Number of applybucketdiff replies that have been processed.", this),
      merge_handler_metrics(this),
      batchingSize("batchingsize", {}, "Number of operations batched per bucket (only counts "
                   "batches of size > 1)", this)
{ }

FileStorThreadMetrics::~FileStorThreadMetrics() = default;

FileStorStripeMetrics::FileStorStripeMetrics(const std::string& name, const std::string& description)
    : MetricSet(name, {{"partofsum"}}, description),
      averageQueueWaitingTime("averagequeuewait", {}, "Average time an operation spends in input queue.", this)
{
}

FileStorStripeMetrics::~FileStorStripeMetrics() = default;

FileStorDiskMetrics::FileStorDiskMetrics(const std::string& name, const std::string& description, MetricSet* owner)
    : MetricSet(name, {{"partofsum"}}, description, owner),
      sumThreads("allthreads", {{"sum"}}, "", this),
      sumStripes("allstripes", {{"sum"}}, "", this),
      averageQueueWaitingTime("averagequeuewait.sum", {}, "Average time an operation spends in input queue.", this),
      queueSize("queuesize", {}, "Size of input message queue.", this),
      pendingMerges("pendingmerge", {}, "Number of buckets currently being merged.", this),
      waitingForLockHitRate("waitingforlockrate", {},
              "Amount of times a filestor thread has needed to wait for "
              "lock to take next message in queue.", this),
      lockWaitTime("lockwaittime", {}, "Amount of time waiting used waiting for lock.", this)
{
    pendingMerges.unsetOnZeroValue();
    waitingForLockHitRate.unsetOnZeroValue();
}

FileStorDiskMetrics::~FileStorDiskMetrics() = default;

void
FileStorDiskMetrics::initDiskMetrics(uint32_t numStripes, uint32_t threadsPerDisk)
{
    threads.clear();
    threads.resize(threadsPerDisk);
    for (uint32_t i=0; i<threadsPerDisk; ++i) {
        std::ostringstream desc;
        std::ostringstream name;
        name << "thread" << i;
        desc << "Thread " << i << '/' << threadsPerDisk;
        threads[i] = std::make_shared<FileStorThreadMetrics>(name.str(), desc.str());
        registerMetric(*threads[i]);
        sumThreads.addMetricToSum(*threads[i]);
    }
    stripes.clear();
    stripes.resize(numStripes);
    for (uint32_t i=0; i<numStripes; ++i) {
        std::ostringstream desc;
        std::ostringstream name;
        name << "stripe" << i;
        desc << "Stripe " << i << '/' << numStripes;
        stripes[i] = std::make_shared<FileStorStripeMetrics>(name.str(), desc.str());
        registerMetric(*stripes[i]);
        sumStripes.addMetricToSum(*stripes[i]);
    }
}

FileStorMetrics::FileStorMetrics()
    : MetricSet("filestor", {{"filestor"}}, ""),
      sum("alldisks", {{"sum"}}, "", this),
      directoryEvents("directoryevents", {}, "Number of directory events received.", this),
      partitionEvents("partitionevents", {}, "Number of partition events received.", this),
      diskEvents("diskevents", {}, "Number of disk events received.", this),
      bucket_db_init_latency("bucket_db_init_latency", {}, "Time taken (in ms) to initialize bucket databases with "
                                                           "information from the persistence provider", this)
{ }

FileStorMetrics::~FileStorMetrics() = default;

void FileStorMetrics::initDiskMetrics(uint32_t numStripes, uint32_t threadsPerDisk)
{
    assert( ! disk);
    // Currently FileStorHandlerImpl expects metrics to exist for
    // disks that are not in use too.
    disk = std::make_shared<FileStorDiskMetrics>( "disk_0", "Disk 0", this);
    sum.addMetricToSum(*disk);
    disk->initDiskMetrics(numStripes, threadsPerDisk);
}

}
