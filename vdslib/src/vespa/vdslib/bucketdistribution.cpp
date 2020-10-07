// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "bucketdistribution.h"

#include <vespa/log/log.h>
LOG_SETUP(".bucketdistribution");

namespace vdslib {

BucketDistribution::BucketDistribution(uint32_t numColumns, uint32_t numBucketBits) :
    _numColumns(0),
    _numBucketBits(numBucketBits),
    _bucketToColumn(),
    _lock()
{
    _bucketToColumn.resize(getNumBuckets());
    reset();
    setNumColumns(numColumns);
}

BucketDistribution::~BucketDistribution() = default;

void
BucketDistribution::getBucketCount(uint32_t numColumns, uint32_t numBucketBits, std::vector<uint32_t> &ret)
{
    ret.resize(numColumns);
    uint32_t cnt = getNumBuckets(numBucketBits) / numColumns;
    uint32_t rst = getNumBuckets(numBucketBits) % numColumns;
    for (uint32_t i = 0; i < numColumns; ++i) {
        ret[i] = cnt + (i < rst ? 1 : 0);
    }
}

void
BucketDistribution::getBucketMigrateCount(uint32_t numColumns, uint32_t numBucketBits, std::vector<uint32_t> &ret)
{
    getBucketCount(numColumns++, numBucketBits, ret);
    uint32_t cnt = getNumBuckets(numBucketBits) / numColumns;
    uint32_t rst = getNumBuckets(numBucketBits) % numColumns;
    for (uint32_t i = 0; i < numColumns - 1; ++i) {
        ret[i] -= cnt + (i < rst ? 1 : 0);
    }
}

void
BucketDistribution::reset()
{
    for (uint32_t & value : _bucketToColumn) {
        value = 0;
    }
    _numColumns = 1;
}

void
BucketDistribution::addColumn()
{
    uint32_t newColumns = _numColumns + 1;
    std::vector<uint32_t> migrate;
    getBucketMigrateCount(_numColumns, _numBucketBits, migrate);
    uint32_t numBuckets = getNumBuckets(_numBucketBits);
    for (uint32_t i = 0; i < numBuckets; ++i) {
        uint32_t old = _bucketToColumn[i];
        if (migrate[old] > 0) {
            _bucketToColumn[i] = _numColumns; // move this bucket to the new column
            migrate[old]--;
        }
    }
    _numColumns = newColumns;
}

void
BucketDistribution::setNumColumns(uint32_t numColumns)
{
    vespalib::LockGuard guard(_lock);
    if (numColumns < _numColumns) {
        reset();
    }
    if (numColumns == _numColumns) {
        return;
    }
    for (int i = numColumns - _numColumns; --i >= 0; ) {
        addColumn();
    }
}

void
BucketDistribution::setNumBucketBits(uint32_t numBucketBits)
{
    uint32_t numColumns;
    {
        vespalib::LockGuard guard(_lock);
        if (numBucketBits == _numBucketBits) {
            return;
        }
        _numBucketBits = numBucketBits;
        _bucketToColumn.resize(getNumBuckets(numBucketBits));
        numColumns = _numColumns;
        reset();
    }
    setNumColumns(numColumns);
}

uint32_t
BucketDistribution::getColumn(const document::BucketId &bucketId) const
{
    uint32_t ret = (uint32_t)(bucketId.getId() & (getNumBuckets(_numBucketBits) - 1));
    if (ret >= _bucketToColumn.size()) {
        LOG(error,
            "The bucket distribution map is not in sync with the number of bucket bits. "
            "This should never happen! Distribution is broken!!");
        return 0;
    }
    return _bucketToColumn[ret];
}

}
