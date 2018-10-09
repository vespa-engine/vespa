// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "metricpersistenceprovider.h"
#include <vespa/metrics/valuemetric.h>
#include <vespa/metrics/metrictimer.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.spi.metrics");

#define PRE_PROCESS(opIndex) \
    metrics::MetricTimer metricTimer;

#define POST_PROCESS(opIndex, result) \
    metricTimer.stop( \
        *_functionMetrics[opIndex]->_metric[result.getErrorCode()]); \
    if (result.hasError()) { \
        LOG(debug, "SPI::%s failed: %s", \
            _functionMetrics[opIndex]->getName().c_str(), \
            result.toString().c_str()); \
    }

namespace storage {
namespace spi {

namespace {
    typedef MetricPersistenceProvider Impl;
}

using metrics::DoubleAverageMetric;
using std::make_unique;

Impl::ResultMetrics::~ResultMetrics() { }

Impl::ResultMetrics::ResultMetrics(const char* opName)
    : metrics::MetricSet(opName, {}, ""),
      _metric(Result::ERROR_COUNT)
{
    metrics::Metric::Tags noTags;
    _metric[Result::NONE] = make_unique<DoubleAverageMetric>("success", noTags, "", this);
    _metric[Result::TRANSIENT_ERROR] = make_unique<DoubleAverageMetric>("transient_error", noTags, "", this);
    _metric[Result::PERMANENT_ERROR] = make_unique<DoubleAverageMetric>("permanent_error", noTags, "", this);
    _metric[Result::TIMESTAMP_EXISTS] = make_unique<DoubleAverageMetric>("timestamp_exists", noTags, "", this);
    _metric[Result::FATAL_ERROR] = make_unique<DoubleAverageMetric>("fatal_error", noTags, "", this);
    _metric[Result::RESOURCE_EXHAUSTED] = make_unique<DoubleAverageMetric>("resource_exhausted", noTags, "", this);
    // Assert that the above initialized all entries in vector
    for (size_t i=0; i<_metric.size(); ++i) assert(_metric[i].get());
}

Impl::MetricPersistenceProvider(PersistenceProvider& next)
    : metrics::MetricSet("spi", {}, ""),
      _next(&next),
      _functionMetrics(23)
{
    defineResultMetrics(0, "initialize");
    defineResultMetrics(1, "getPartitionStates");
    defineResultMetrics(2, "listBuckets");
    defineResultMetrics(3, "setClusterState");
    defineResultMetrics(4, "setActiveState");
    defineResultMetrics(5, "getBucketInfo");
    defineResultMetrics(6, "put");
    defineResultMetrics(7, "remove");
    defineResultMetrics(8, "removeIfFound");
    defineResultMetrics(9, "removeEntry");
    defineResultMetrics(10, "update");
    defineResultMetrics(11, "flush");
    defineResultMetrics(12, "get");
    defineResultMetrics(13, "createIterator");
    defineResultMetrics(14, "iterate");
    defineResultMetrics(15, "destroyIterator");
    defineResultMetrics(16, "createBucket");
    defineResultMetrics(17, "deleteBucket");
    defineResultMetrics(18, "getModifiedBuckets");
    defineResultMetrics(19, "maintain");
    defineResultMetrics(20, "split");
    defineResultMetrics(21, "join");
    defineResultMetrics(22, "move");
}

Impl::~MetricPersistenceProvider() { }

void
Impl::defineResultMetrics(int index, const char* name)
{
    _functionMetrics[index] = make_unique<ResultMetrics>(name);
    registerMetric(*_functionMetrics[index]);
}

// Implementation of SPI functions

Result
Impl::initialize()
{
    PRE_PROCESS(0);
    Result r(_next->initialize());
    POST_PROCESS(0, r);
    return r;
}

PartitionStateListResult
Impl::getPartitionStates() const
{
    PRE_PROCESS(1);
    PartitionStateListResult r(_next->getPartitionStates());
    POST_PROCESS(1, r);
    return r;
}

BucketIdListResult
Impl::listBuckets(BucketSpace bucketSpace, PartitionId v1) const
{
    PRE_PROCESS(2);
    BucketIdListResult r(_next->listBuckets(bucketSpace, v1));
    POST_PROCESS(2, r);
    return r;
}

Result
Impl::setClusterState(BucketSpace bucketSpace, const ClusterState& v1)
{
    PRE_PROCESS(3);
    Result r(_next->setClusterState(bucketSpace, v1));
    POST_PROCESS(3, r);
    return r;
}

Result
Impl::setActiveState(const Bucket& v1, BucketInfo::ActiveState v2)
{
    PRE_PROCESS(4);
    Result r(_next->setActiveState(v1, v2));
    POST_PROCESS(4, r);
    return r;
}

BucketInfoResult
Impl::getBucketInfo(const Bucket& v1) const
{
    PRE_PROCESS(5);
    BucketInfoResult r(_next->getBucketInfo(v1));
    POST_PROCESS(5, r);
    return r;
}

Result
Impl::put(const Bucket& v1, Timestamp v2, const DocumentSP& v3, Context& v4)
{
    PRE_PROCESS(6);
    Result r(_next->put(v1, v2, v3, v4));
    POST_PROCESS(6, r);
    return r;
}

RemoveResult
Impl::remove(const Bucket& v1, Timestamp v2, const DocumentId& v3, Context& v4)
{
    PRE_PROCESS(7);
    RemoveResult r(_next->remove(v1, v2, v3, v4));
    POST_PROCESS(7, r);
    return r;
}

RemoveResult
Impl::removeIfFound(const Bucket& v1, Timestamp v2, const DocumentId& v3,
                    Context& v4)
{
    PRE_PROCESS(8);
    RemoveResult r(_next->removeIfFound(v1, v2, v3, v4));
    POST_PROCESS(8, r);
    return r;
}

Result
Impl::removeEntry(const Bucket& v1, Timestamp v2, Context& v3)
{
    PRE_PROCESS(9);
    Result r(_next->removeEntry(v1, v2, v3));
    POST_PROCESS(9, r);
    return r;
}

UpdateResult
Impl::update(const Bucket& v1, Timestamp v2, const DocumentUpdateSP& v3, Context& v4)
{
    PRE_PROCESS(10);
    UpdateResult r(_next->update(v1, v2, v3, v4));
    POST_PROCESS(10, r);
    return r;
}

Result
Impl::flush(const Bucket& v1, Context& v2)
{
    PRE_PROCESS(11);
    Result r(_next->flush(v1, v2));
    POST_PROCESS(11, r);
    return r;
}

GetResult
Impl::get(const Bucket& v1, const document::FieldSet& v2, const DocumentId& v3, Context& v4) const
{
    PRE_PROCESS(12);
    GetResult r(_next->get(v1, v2, v3, v4));
    POST_PROCESS(12, r);
    return r;
}

CreateIteratorResult
Impl::createIterator(const Bucket& v1, const document::FieldSet& v2,
                     const Selection& v3, IncludedVersions v4, Context& v5)
{
    PRE_PROCESS(13);
    CreateIteratorResult r(_next->createIterator(v1, v2, v3, v4, v5));
    POST_PROCESS(13, r);
    return r;
}

IterateResult
Impl::iterate(IteratorId v1, uint64_t v2, Context& v3) const
{
    PRE_PROCESS(14);
    IterateResult r(_next->iterate(v1, v2, v3));
    POST_PROCESS(14, r);
    return r;
}

Result
Impl::destroyIterator(IteratorId v1, Context& v2)
{
    PRE_PROCESS(15);
    Result r(_next->destroyIterator(v1, v2));
    POST_PROCESS(15, r);
    return r;
}

Result
Impl::createBucket(const Bucket& v1, Context& v2)
{
    PRE_PROCESS(16);
    Result r(_next->createBucket(v1, v2));
    POST_PROCESS(16, r);
    return r;
}

Result
Impl::deleteBucket(const Bucket& v1, Context& v2)
{
    PRE_PROCESS(17);
    Result r(_next->deleteBucket(v1, v2));
    POST_PROCESS(17, r);
    return r;
}

BucketIdListResult
Impl::getModifiedBuckets(BucketSpace bucketSpace) const
{
    PRE_PROCESS(18);
    BucketIdListResult r(_next->getModifiedBuckets(bucketSpace));
    POST_PROCESS(18, r);
    return r;
}

Result
Impl::maintain(const Bucket& v1, MaintenanceLevel v2)
{
    PRE_PROCESS(19);
    Result r(_next->maintain(v1, v2));
    POST_PROCESS(19, r);
    return r;
}

Result
Impl::split(const Bucket& v1, const Bucket& v2, const Bucket& v3, Context& v4)
{
    PRE_PROCESS(20);
    Result r(_next->split(v1, v2, v3, v4));
    POST_PROCESS(20, r);
    return r;
}

Result
Impl::join(const Bucket& v1, const Bucket& v2, const Bucket& v3, Context& v4)
{
    PRE_PROCESS(21);
    Result r(_next->join(v1, v2, v3, v4));
    POST_PROCESS(21, r);
    return r;
}

Result
Impl::move(const Bucket& v1, PartitionId v2, Context& v3)
{
    PRE_PROCESS(22);
    Result r(_next->move(v1, v2, v3));
    POST_PROCESS(22, r);
    return r;
}

} // spi
} // storage
