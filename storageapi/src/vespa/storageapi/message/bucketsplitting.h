// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storageapi/buckets/bucketinfo.h>
#include <vespa/storageapi/messageapi/bucketcommand.h>
#include <vespa/storageapi/messageapi/bucketinforeply.h>
#include <vespa/storageapi/messageapi/maintenancecommand.h>

namespace storage::api {

/**
 * @class SplitBucketCommand
 * @ingroup message
 *
 * @brief Split a bucket
 *
 * Splits a bucket into two parts using the next split bit that is unused.
 *
 * Distributors can issue splits for multiple reasons:
 *   - Inconsistent buckets, so we need to split buckets containing others until
 *     they are either split equally, or no longer contains others.
 *   - Buckets that are too large are split to reduce file size.
 *   - Buckets with too many entries are split to reduce amount of metadata.
 *
 * In the first case, min and max split bits can be set. This will make storage
 * able to split several bits at a time, but know where to stop.
 *
 * In the second case, min byte size can be set, to ensure that we don't split
 * bucket more one step if the copy at the time of processing is
 * actually smaller. Since removes can happen in the meantime, the min byte size
 * should be smaller than the limit we use for splitting. Suggesting half.
 *
 * Similarily we can do as the second case in the third case too, just using
 * min doc count as limiter instead.
 *
 * If neither are specified, min/max split bits limits nothing, but the sizes
 * are set to max, which ensures that only one split step is taken.
 */
class SplitBucketCommand : public MaintenanceCommand {
private:
    uint8_t _minSplitBits;
    uint8_t _maxSplitBits;
    uint32_t _minByteSize;
    uint32_t _minDocCount;

public:
    SplitBucketCommand(const document::Bucket& bucket);

    uint8_t getMinSplitBits() const { return _minSplitBits; }
    uint8_t getMaxSplitBits() const { return _maxSplitBits; }
    uint32_t getMinByteSize() const { return _minByteSize; }
    uint32_t getMinDocCount() const { return _minDocCount; }

    void setMinSplitBits(uint8_t v) { _minSplitBits = v; }
    void setMaxSplitBits(uint8_t v) { _maxSplitBits = v; }
    void setMinByteSize(uint32_t v) { _minByteSize = v; }
    void setMinDocCount(uint32_t v) { _minDocCount = v; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGECOMMAND(SplitBucketCommand, onSplitBucket)
};

/**
 * @class SplitBucketReply
 * @ingroup message
 *
 * @brief Reply of a split bucket command.
 */
class SplitBucketReply : public BucketReply {
public:
    typedef std::pair<document::BucketId, BucketInfo> Entry;
    explicit SplitBucketReply(const SplitBucketCommand& cmd);
    std::vector<Entry>& getSplitInfo() { return _result; }
    const std::vector<Entry>& getSplitInfo() const { return _result; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGEREPLY(SplitBucketReply, onSplitBucketReply)
private:
    std::vector<Entry> _result;
};

/**
 * @class JoinBucketCommand
 * @ingroup message
 *
 * @brief Join two buckets
 *
 * Joins two buckets on the same node into a bucket with one fewer split bit.
 */
class JoinBucketsCommand : public MaintenanceCommand {
    std::vector<document::BucketId> _sources;
    uint8_t _minJoinBits;
public:
    explicit JoinBucketsCommand(const document::Bucket &target);
    std::vector<document::BucketId>& getSourceBuckets() { return _sources; }
    const std::vector<document::BucketId>& getSourceBuckets() const { return _sources; }
    void setMinJoinBits(uint8_t minJoinBits) { _minJoinBits = minJoinBits; }
    uint8_t getMinJoinBits() const { return _minJoinBits; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGECOMMAND(JoinBucketsCommand, onJoinBuckets)
};

/**
 * @class JoinBucketsReply
 * @ingroup message
 *
 * @brief Reply of a join bucket command.
 */
class JoinBucketsReply : public BucketInfoReply {
    std::vector<document::BucketId> _sources;
public:
    explicit JoinBucketsReply(const JoinBucketsCommand& cmd);
    JoinBucketsReply(const JoinBucketsCommand& cmd, const BucketInfo& bucketInfo);
    const std::vector<document::BucketId>& getSourceBuckets() const { return _sources; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGEREPLY(JoinBucketsReply, onJoinBucketsReply)
};

}
