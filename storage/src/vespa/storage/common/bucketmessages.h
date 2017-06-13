// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/storageapi/message/internal.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/storageapi/buckets/bucketinfo.h>
#include <vector>
#include <set>

namespace storage {

/**
 * @class ReadBucketList
 * @ingroup common
 *
 * @brief List buckets existing on a partition.
 */
class ReadBucketList : public api::InternalCommand {
    spi::PartitionId _partition;

public:
    typedef std::unique_ptr<ReadBucketList> UP;
    static const uint32_t ID = 2003;

    ReadBucketList(spi::PartitionId partition);
    ~ReadBucketList();
    spi::PartitionId getPartition() const { return _partition; }

    std::unique_ptr<api::StorageReply> makeReply() override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};


/**
 * @class ReadBucketListReply
 * @ingroup common
 */
class ReadBucketListReply : public api::InternalReply {
    spi::PartitionId _partition;
    spi::BucketIdListResult::List _buckets;

public:
    typedef std::unique_ptr<ReadBucketListReply> UP;
    typedef std::shared_ptr<ReadBucketListReply> SP;
    static const uint32_t ID = 2004;

    ReadBucketListReply(const ReadBucketList& cmd);
    ~ReadBucketListReply();

    spi::PartitionId getPartition() const { return _partition; }

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
    document::BucketId _bucketId;

public:
    static const uint32_t ID = 2005;

    ReadBucketInfo(const document::BucketId& bucketId);
    ~ReadBucketInfo();

    document::BucketId getBucketId() const override { return _bucketId; }
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
    document::BucketId _bucketId;

public:
    static const uint32_t ID = 2006;

    ReadBucketInfoReply(const ReadBucketInfo& cmd);
    ~ReadBucketInfoReply();

    document::BucketId getBucketId() const override { return _bucketId; }
    bool hasSingleBucketId() const override { return true; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};


/**
 * @class RepairBucketCommand
 * @ingroup common
 *
 * @brief Repair a given bucket (if it contain errors).
 *
 * This message is sent continually by the bucket integrity checker.
 * Errors found are reported back.
 */
class RepairBucketCommand : public api::InternalCommand {
    document::BucketId _bucket;
    uint16_t _disk;
    bool _verifyBody; // Optional as it is expensive
    bool _moveToIdealDisk; // Optional as it is expensive

public:
    typedef std::unique_ptr<RepairBucketCommand> UP;

    static const uint32_t ID = 2007;

    RepairBucketCommand(const document::BucketId& bucket, uint16_t disk);
    ~RepairBucketCommand();

    bool hasSingleBucketId() const override { return true; }
    document::BucketId getBucketId() const override { return _bucket; }

    uint16_t getDisk() const { return _disk; }
    bool verifyBody() const { return _verifyBody; }
    bool moveToIdealDisk() const { return _moveToIdealDisk; }

    void setBucketId(const document::BucketId& id) { _bucket = id; }
    void verifyBody(bool doIt) { _verifyBody = doIt; }
    void moveToIdealDisk(bool doIt) { _moveToIdealDisk = doIt; }

    std::unique_ptr<api::StorageReply> makeReply() override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
private:
    vespalib::string getSummary() const override;
};

/**
 * @class RepairBucketReply
 * @ingroup common
 */
class RepairBucketReply : public api::InternalReply {
    document::BucketId _bucket;
    api::BucketInfo _bucketInfo;
    uint16_t _disk;
    bool _altered;

public:
    typedef std::unique_ptr<RepairBucketReply> UP;
    static const uint32_t ID = 2008;

    RepairBucketReply(const RepairBucketCommand& cmd, const api::BucketInfo& bucketInfo = api::BucketInfo());
    ~RepairBucketReply();
    document::BucketId getBucketId() const override { return _bucket; }
    bool hasSingleBucketId() const override { return true; }

    const api::BucketInfo& getBucketInfo() const { return _bucketInfo; }
    uint16_t getDisk() const { return _disk; }

    bool bucketAltered() const { return _altered; }
    void setAltered(bool altered) { _altered = altered; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

/**
 * @class BucketDiskMoveCommand
 * @ingroup common
 *
 * @brief Move a given bucket (from src disk to dst disk).
 *
 * This message is sent continually by the bucket mover.
 * Size of the bucket moved is reported back.
 */
class BucketDiskMoveCommand : public api::InternalCommand {
    document::BucketId _bucket;
    uint16_t _srcDisk;
    uint16_t _dstDisk;

public:
    typedef std::shared_ptr<BucketDiskMoveCommand> SP;
    static const uint32_t ID = 2012;

    BucketDiskMoveCommand(const document::BucketId& bucket, uint16_t srcDisk, uint16_t dstDisk);
    ~BucketDiskMoveCommand();

    document::BucketId getBucketId() const override { return _bucket; }
    bool hasSingleBucketId() const override { return true; }

    uint16_t getSrcDisk() const { return _srcDisk; }
    uint16_t getDstDisk() const { return _dstDisk; }

    void setBucketId(const document::BucketId& id) { _bucket = id; }

    std::unique_ptr<api::StorageReply> makeReply() override;

    void print(std::ostream& out, bool, const std::string&) const override;
};

/**
 * @class BucketDiskMoveReply
 * @ingroup common
 */
class BucketDiskMoveReply : public api::InternalReply {
    document::BucketId _bucket;
    api::BucketInfo _bucketInfo;
    uint64_t _fileSizeOnSrc;
    uint64_t _fileSizeOnDst;
    uint16_t _srcDisk;
    uint16_t _dstDisk;

public:
    typedef std::shared_ptr<BucketDiskMoveReply> SP;
    static const uint32_t ID = 2013;

    BucketDiskMoveReply(const BucketDiskMoveCommand& cmd,
                        const api::BucketInfo& bucketInfo = api::BucketInfo(),
                        uint32_t sourceFileSize = 0,
                        uint32_t destinationFileSize = 0);
    ~BucketDiskMoveReply();

    document::BucketId getBucketId() const override { return _bucket; }
    bool hasSingleBucketId() const override { return true; }

    const api::BucketInfo& getBucketInfo() const { return _bucketInfo; }
    void setFileSizeOnSrc(uint64_t fileSize) { _fileSizeOnSrc = fileSize; }
    void setFileSizeOnDst(uint64_t fileSize) { _fileSizeOnDst = fileSize; }
    uint64_t getFileSizeOnSrc() const { return _fileSizeOnSrc; }
    uint64_t getFileSizeOnDst() const { return _fileSizeOnDst; }
    uint16_t getSrcDisk() const { return _srcDisk; }
    uint16_t getDstDisk() const { return _dstDisk; }

    void print(std::ostream& out, bool, const std::string&) const override;
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
    document::BucketId _bucket;
    uint16_t _keepOnDisk;
    uint16_t _joinFromDisk;

public:
    static const uint32_t ID = 2015;

    InternalBucketJoinCommand(const document::BucketId& bucket, uint16_t keepOnDisk, uint16_t joinFromDisk);
    ~InternalBucketJoinCommand();

    document::BucketId getBucketId() const override { return _bucket; }
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
    document::BucketId _bucket;
    api::BucketInfo _bucketInfo;

public:
    static const uint32_t ID = 2016;

    InternalBucketJoinReply(const InternalBucketJoinCommand& cmd,
                            const api::BucketInfo& info = api::BucketInfo());
    ~InternalBucketJoinReply();

    document::BucketId getBucketId() const override { return _bucket; }
    bool hasSingleBucketId() const override { return true; }

    const api::BucketInfo& getBucketInfo() const { return _bucketInfo; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

} // storage
