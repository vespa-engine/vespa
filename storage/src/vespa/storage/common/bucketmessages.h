// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    ReadBucketList(spi::PartitionId partition)
        : api::InternalCommand(ID), _partition(partition)
    {
    }

    spi::PartitionId getPartition() const { return _partition; }

    std::unique_ptr<api::StorageReply> makeReply();

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const
    {
        out << "ReadBucketList(" << _partition << ")";

        if (verbose) {
            out << " : ";
            InternalCommand::print(out, true, indent);
        }
    }
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

    ReadBucketListReply(const ReadBucketList& cmd)
        : api::InternalReply(ID, cmd),
          _partition(cmd.getPartition())
    {
    }

    spi::PartitionId getPartition() const { return _partition; }

    spi::BucketIdListResult::List& getBuckets() { return _buckets; }
    const spi::BucketIdListResult::List& getBuckets() const {
        return _buckets;
    }

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const
    {
        out << "ReadBucketListReply(" << _buckets.size() << " buckets)";
        if (verbose) {
            out << " : ";
            InternalReply::print(out, true, indent);
        }
    }
};

inline std::unique_ptr<api::StorageReply> ReadBucketList::makeReply() {
    return std::unique_ptr<api::StorageReply>(
            new ReadBucketListReply(*this));
}

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

    ReadBucketInfo(const document::BucketId& bucketId)
        : api::InternalCommand(ID), _bucketId(bucketId)
    {
    }

    document::BucketId getBucketId() const { return _bucketId; }
    virtual bool hasSingleBucketId() const { return true; }

    std::unique_ptr<api::StorageReply> makeReply();

    virtual void print(std::ostream& out, bool verbose, const std::string& indent) const
    {
        out << "ReadBucketInfo(" << _bucketId << ")";

        if (verbose) {
            out << " : ";
            InternalCommand::print(out, true, indent);
        }
    }
private:
    virtual vespalib::string getSummary() const {
        vespalib::string s("ReadBucketInfo(");
        s.append(_bucketId.toString());
        s.append(')');
        return s;
    }

};


/**
 * @class ReadBucketInfoReply
 * @ingroup common
 */
class ReadBucketInfoReply : public api::InternalReply {
    document::BucketId _bucketId;

public:
    static const uint32_t ID = 2006;

    ReadBucketInfoReply(const ReadBucketInfo& cmd)
        : api::InternalReply(ID, cmd),
         _bucketId(cmd.getBucketId())
    {
    }

    document::BucketId getBucketId() const { return _bucketId; }
    virtual bool hasSingleBucketId() const { return true; }

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const
    {
        out << "ReadBucketInfoReply()";
        if (verbose) {
            out << " : ";
            InternalReply::print(out, true, indent);
        }
    }
};

inline std::unique_ptr<api::StorageReply> ReadBucketInfo::makeReply() {
    return std::unique_ptr<api::StorageReply>(
            new ReadBucketInfoReply(*this));
}


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

    RepairBucketCommand(const document::BucketId& bucket, uint16_t disk)
        : api::InternalCommand(ID),
          _bucket(bucket),
          _disk(disk),
          _verifyBody(false),
          _moveToIdealDisk(false)
    {
        setPriority(LOW);
    }

    virtual bool hasSingleBucketId() const { return true; }
    document::BucketId getBucketId() const { return _bucket; }

    uint16_t getDisk() const { return _disk; }
    bool verifyBody() const { return _verifyBody; }
    bool moveToIdealDisk() const { return _moveToIdealDisk; }

    void setBucketId(const document::BucketId& id) { _bucket = id; }
    void verifyBody(bool doIt) { _verifyBody = doIt; }
    void moveToIdealDisk(bool doIt) { _moveToIdealDisk = doIt; }

    std::unique_ptr<api::StorageReply> makeReply();

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const
    {
        out << getSummary();
        if (verbose) {
            out << " : ";
            InternalCommand::print(out, true, indent);
        }
    }
private:
    virtual vespalib::string getSummary() const {
        vespalib::asciistream s;
        s << "ReadBucketInfo(" << _bucket.toString() << ", disk " << _disk
          << (_verifyBody ? ", verifying body" : "")
          << (_moveToIdealDisk ? ", moving to ideal disk" : "")
          << ")";
        return s.str();
    }
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

    RepairBucketReply(const RepairBucketCommand& cmd,
                      const api::BucketInfo& bucketInfo = api::BucketInfo())
        : api::InternalReply(ID, cmd),
          _bucket(cmd.getBucketId()),
          _bucketInfo(bucketInfo),
          _disk(cmd.getDisk()),
          _altered(false)
    {
    }

    document::BucketId getBucketId() const { return _bucket; }
    virtual bool hasSingleBucketId() const { return true; }

    const api::BucketInfo& getBucketInfo() const { return _bucketInfo; }
    uint16_t getDisk() const { return _disk; }

    bool bucketAltered() const { return _altered; }
    void setAltered(bool altered) { _altered = altered; }

    virtual void print(std::ostream& out, bool verbose, const std::string& indent) const
    {
        out << "RepairBucketReply()";

        if (verbose) {
            out << " : ";
            InternalReply::print(out, true, indent);
        }
    }
};

inline std::unique_ptr<api::StorageReply> RepairBucketCommand::makeReply() {
    return std::unique_ptr<api::StorageReply>(
            new RepairBucketReply(*this));
}

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

    BucketDiskMoveCommand(const document::BucketId& bucket,
                          uint16_t srcDisk, uint16_t dstDisk)
        : api::InternalCommand(ID),
          _bucket(bucket),
          _srcDisk(srcDisk),
          _dstDisk(dstDisk)
    {
        setPriority(LOW);
    }

    document::BucketId getBucketId() const { return _bucket; }
    virtual bool hasSingleBucketId() const { return true; }

    uint16_t getSrcDisk() const { return _srcDisk; }
    uint16_t getDstDisk() const { return _dstDisk; }

    void setBucketId(const document::BucketId& id) { _bucket = id; }

    std::unique_ptr<api::StorageReply> makeReply();

    virtual void print(std::ostream& out, bool, const std::string&) const
    {
        out << "BucketDiskMoveCommand(" << _bucket << ", source " << _srcDisk
            << ", target " << _dstDisk << ")";
    }

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
                        uint32_t destinationFileSize = 0)
        : api::InternalReply(ID, cmd),
          _bucket(cmd.getBucketId()),
          _bucketInfo(bucketInfo),
          _fileSizeOnSrc(sourceFileSize),
          _fileSizeOnDst(destinationFileSize),
          _srcDisk(cmd.getSrcDisk()),
          _dstDisk(cmd.getDstDisk())
    {
    }

    document::BucketId getBucketId() const { return _bucket; }
    virtual bool hasSingleBucketId() const { return true; }

    const api::BucketInfo& getBucketInfo() const { return _bucketInfo; }
    void setFileSizeOnSrc(uint64_t fileSize) { _fileSizeOnSrc = fileSize; }
    void setFileSizeOnDst(uint64_t fileSize) { _fileSizeOnDst = fileSize; }
    uint64_t getFileSizeOnSrc() const { return _fileSizeOnSrc; }
    uint64_t getFileSizeOnDst() const { return _fileSizeOnDst; }
    uint16_t getSrcDisk() const { return _srcDisk; }
    uint16_t getDstDisk() const { return _dstDisk; }

    void print(std::ostream& out, bool, const std::string&) const
    {
        out << "BucketDiskMoveReply(" << _bucket << ", source " << _srcDisk
            << ", target " << _dstDisk << ", " << _bucketInfo << ", "
            << getResult() << ")";
    }
};

inline std::unique_ptr<api::StorageReply> BucketDiskMoveCommand::makeReply()
{
    return std::unique_ptr<api::StorageReply>(
            new BucketDiskMoveReply(*this));
}

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

    InternalBucketJoinCommand(const document::BucketId& bucket,
            uint16_t keepOnDisk, uint16_t joinFromDisk)
        : api::InternalCommand(ID),
          _bucket(bucket),
          _keepOnDisk(keepOnDisk),
          _joinFromDisk(joinFromDisk)
    {
        setPriority(HIGH); // To not get too many pending of these, prioritize
                           // them higher than getting more bucket info lists.
    }

    document::BucketId getBucketId() const { return _bucket; }
    virtual bool hasSingleBucketId() const { return true; }

    uint16_t getDiskOfInstanceToKeep() const { return _keepOnDisk; }
    uint16_t getDiskOfInstanceToJoin() const { return _joinFromDisk; }

    std::unique_ptr<api::StorageReply> makeReply();

    virtual void print(std::ostream& out, bool verbose, const std::string& indent) const
    {
        out << "InternalBucketJoinCommand()";

        if (verbose) {
            out << " : ";
            InternalCommand::print(out, true, indent);
        }
    }
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
                            const api::BucketInfo& info = api::BucketInfo())
        : api::InternalReply(ID, cmd),
          _bucket(cmd.getBucketId()),
          _bucketInfo(info)
    {
    }

    document::BucketId getBucketId() const { return _bucket; }
    virtual bool hasSingleBucketId() const { return true; }

    const api::BucketInfo& getBucketInfo() const { return _bucketInfo; }

    virtual void print(std::ostream& out, bool verbose, const std::string& indent) const
    {
        out << "InternalBucketJoinReply()";

        if (verbose) {
            out << " : ";
            InternalReply::print(out, true, indent);
        }
    }
};

inline std::unique_ptr<api::StorageReply>
InternalBucketJoinCommand::makeReply()
{
    return std::unique_ptr<api::StorageReply>(
            new InternalBucketJoinReply(*this));
}

} // storage

