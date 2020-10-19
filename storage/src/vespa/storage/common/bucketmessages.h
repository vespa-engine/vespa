// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/persistence/spi/result.h>
#include <vespa/storageapi/message/internal.h>
#include <vespa/storageapi/buckets/bucketinfo.h>

namespace storage {

/**
 * @class ReadBucketList
 * @ingroup common
 *
 * @brief List buckets existing on a partition.
 */
class ReadBucketList : public api::InternalCommand {
    document::BucketSpace _bucketSpace;
    spi::PartitionId _partition;

public:
    typedef std::unique_ptr<ReadBucketList> UP;
    static const uint32_t ID = 2003;

    ReadBucketList(document::BucketSpace bucketSpace, spi::PartitionId partition);
    ~ReadBucketList();
    document::BucketSpace getBucketSpace() const { return _bucketSpace; }
    spi::PartitionId getPartition() const { return _partition; }
    document::Bucket getBucket() const override;

    std::unique_ptr<api::StorageReply> makeReply() override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};


/**
 * @class ReadBucketListReply
 * @ingroup common
 */
class ReadBucketListReply : public api::InternalReply {
    document::BucketSpace _bucketSpace;
    spi::PartitionId _partition;
    spi::BucketIdListResult::List _buckets;

public:
    typedef std::unique_ptr<ReadBucketListReply> UP;
    typedef std::shared_ptr<ReadBucketListReply> SP;
    static const uint32_t ID = 2004;

    ReadBucketListReply(const ReadBucketList& cmd);
    ~ReadBucketListReply();

    document::BucketSpace getBucketSpace() const { return _bucketSpace; }
    spi::PartitionId getPartition() const { return _partition; }
    document::Bucket getBucket() const override;

    spi::BucketIdListResult::List& getBuckets() { return _buckets; }
    const spi::BucketIdListResult::List& getBuckets() const {
        return _buckets;
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

/**
 * @class ReadBucketInfo
 * @ingroup common
 *
 * @brief Get more detailed information about a set of buckets.
 *
 * The distributor wants some information for each bucket, that one
 * have to open the bucket and read its headers to find. This class is
 * used to retrieve such information.
 */
class ReadBucketInfo : public api::InternalCommand {
    document::Bucket _bucket;

public:
    static const uint32_t ID = 2005;

    ReadBucketInfo(const document::Bucket &bucket);
    ~ReadBucketInfo();

    document::Bucket getBucket() const override { return _bucket; }
    bool hasSingleBucketId() const override { return true; }

    std::unique_ptr<api::StorageReply> makeReply() override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
private:
    vespalib::string getSummary() const override;
};


/**
 * @class ReadBucketInfoReply
 * @ingroup common
 */
class ReadBucketInfoReply : public api::InternalReply {
    document::Bucket _bucket;

public:
    static const uint32_t ID = 2006;

    ReadBucketInfoReply(const ReadBucketInfo& cmd);
    ~ReadBucketInfoReply();

    document::Bucket getBucket() const override { return _bucket; }
    bool hasSingleBucketId() const override { return true; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

/**
 * @class InternalBucketJoinCommand
 * @ingroup common
 *
 * @brief Joins multiple versions of the same bucket.
 *
 * In case disks are reintroduced, we might have several copies of the same
 * bucket on multiple disks. In such cases we should join these buckets during
 * initialization as we cannot cope with multiple versions of the same bucket
 * while storage is running.
 */
class InternalBucketJoinCommand : public api::InternalCommand {
    document::Bucket _bucket;
    uint16_t _keepOnDisk;
    uint16_t _joinFromDisk;

public:
    static const uint32_t ID = 2015;

    InternalBucketJoinCommand(const document::Bucket &bucket, uint16_t keepOnDisk, uint16_t joinFromDisk);
    ~InternalBucketJoinCommand();

    document::Bucket getBucket() const override { return _bucket; }
    bool hasSingleBucketId() const override { return true; }

    uint16_t getDiskOfInstanceToKeep() const { return _keepOnDisk; }
    uint16_t getDiskOfInstanceToJoin() const { return _joinFromDisk; }

    std::unique_ptr<api::StorageReply> makeReply() override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

/**
 * @class InternalBucketJoinReply
 * @ingroup common
 */
class InternalBucketJoinReply : public api::InternalReply {
    document::Bucket _bucket;
    api::BucketInfo _bucketInfo;

public:
    static const uint32_t ID = 2016;

    InternalBucketJoinReply(const InternalBucketJoinCommand& cmd,
                            const api::BucketInfo& info = api::BucketInfo());
    ~InternalBucketJoinReply();

    document::Bucket getBucket() const override { return _bucket; }
    bool hasSingleBucketId() const override { return true; }

    const api::BucketInfo& getBucketInfo() const { return _bucketInfo; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

} // storage
