// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmessages.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>

using document::BucketSpace;

namespace storage {

ReadBucketList::ReadBucketList(BucketSpace bucketSpace, spi::PartitionId partition)
    : api::InternalCommand(ID),
      _bucketSpace(bucketSpace),
      _partition(partition)
{ }

ReadBucketList::~ReadBucketList() = default;

document::Bucket
ReadBucketList::getBucket() const
{
    return document::Bucket(_bucketSpace, document::BucketId());
}

void
ReadBucketList::print(std::ostream& out, bool verbose, const std::string& indent) const {
    out << "ReadBucketList(" << _partition << ")";

    if (verbose) {
        out << " : ";
        InternalCommand::print(out, true, indent);
    }
}

ReadBucketListReply::ReadBucketListReply(const ReadBucketList& cmd)
    : api::InternalReply(ID, cmd),
      _bucketSpace(cmd.getBucketSpace()),
      _partition(cmd.getPartition())
{ }

ReadBucketListReply::~ReadBucketListReply() = default;

document::Bucket
ReadBucketListReply::getBucket() const
{
    return document::Bucket(_bucketSpace, document::BucketId());
}

void
ReadBucketListReply::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "ReadBucketListReply(" << _buckets.size() << " buckets)";
    if (verbose) {
        out << " : ";
        InternalReply::print(out, true, indent);
    }
}

std::unique_ptr<api::StorageReply>
ReadBucketList::makeReply() {
    return std::make_unique<ReadBucketListReply>(*this);
}

ReadBucketInfo::ReadBucketInfo(const document::Bucket &bucket)
    : api::InternalCommand(ID),
      _bucket(bucket)
{ }

ReadBucketInfo::~ReadBucketInfo() = default;

void
ReadBucketInfo::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "ReadBucketInfo(" << _bucket.getBucketId() << ")";

    if (verbose) {
        out << " : ";
        InternalCommand::print(out, true, indent);
    }
}

vespalib::string
ReadBucketInfo::getSummary() const {
    vespalib::string s("ReadBucketInfo(");
    s.append(_bucket.toString());
    s.append(')');
    return s;
}

ReadBucketInfoReply::ReadBucketInfoReply(const ReadBucketInfo& cmd)
    : api::InternalReply(ID, cmd),
     _bucket(cmd.getBucket())
{ }

ReadBucketInfoReply::~ReadBucketInfoReply() = default;
void
ReadBucketInfoReply::print(std::ostream& out, bool verbose, const std::string& indent) const {
    out << "ReadBucketInfoReply()";
    if (verbose) {
        out << " : ";
        InternalReply::print(out, true, indent);
    }
}

std::unique_ptr<api::StorageReply> ReadBucketInfo::makeReply() {
    return std::make_unique<ReadBucketInfoReply>(*this);
}


RepairBucketCommand::RepairBucketCommand(const document::Bucket &bucket, uint16_t disk)
    : api::InternalCommand(ID),
      _bucket(bucket),
      _disk(disk),
      _verifyBody(false),
      _moveToIdealDisk(false)
{
    setPriority(LOW);
}

RepairBucketCommand::~RepairBucketCommand() = default;

void
RepairBucketCommand::print(std::ostream& out, bool verbose, const std::string& indent) const {
    out << getSummary();
    if (verbose) {
        out << " : ";
        InternalCommand::print(out, true, indent);
    }
}
    
void
RepairBucketCommand::setBucketId(const document::BucketId& id)
{
    document::Bucket newBucket(_bucket.getBucketSpace(), id);
    _bucket = newBucket;
}

vespalib::string
RepairBucketCommand::getSummary() const {
    vespalib::asciistream s;
    s << "ReadBucketInfo(" << _bucket.toString() << ", disk " << _disk
      << (_verifyBody ? ", verifying body" : "")
      << (_moveToIdealDisk ? ", moving to ideal disk" : "")
      << ")";
    return s.str();
}

RepairBucketReply::RepairBucketReply(const RepairBucketCommand& cmd, const api::BucketInfo& bucketInfo)
    : api::InternalReply(ID, cmd),
      _bucket(cmd.getBucket()),
      _bucketInfo(bucketInfo),
      _disk(cmd.getDisk()),
      _altered(false)
{ }

RepairBucketReply::~RepairBucketReply() = default;

void
RepairBucketReply::print(std::ostream& out, bool verbose, const std::string& indent) const {
    out << "RepairBucketReply()";

    if (verbose) {
        out << " : ";
        InternalReply::print(out, true, indent);
    }
}

std::unique_ptr<api::StorageReply>
RepairBucketCommand::makeReply() {
    return std::make_unique<RepairBucketReply>(*this);
}

BucketDiskMoveCommand::BucketDiskMoveCommand(const document::Bucket &bucket,
                                             uint16_t srcDisk, uint16_t dstDisk)
    : api::InternalCommand(ID),
      _bucket(bucket),
      _srcDisk(srcDisk),
      _dstDisk(dstDisk)
{
    setPriority(LOW);
}

BucketDiskMoveCommand::~BucketDiskMoveCommand() = default;

void
BucketDiskMoveCommand::setBucketId(const document::BucketId& id)
{
    document::Bucket newBucket(_bucket.getBucketSpace(), id);
    _bucket = newBucket;
}

void
BucketDiskMoveCommand::print(std::ostream& out, bool, const std::string&) const {
    out << "BucketDiskMoveCommand(" << _bucket.getBucketId() << ", source " << _srcDisk
        << ", target " << _dstDisk << ")";
}

BucketDiskMoveReply::BucketDiskMoveReply(const BucketDiskMoveCommand& cmd,
                                        const api::BucketInfo& bucketInfo,
                                        uint32_t sourceFileSize,
                                        uint32_t destinationFileSize)
    : api::InternalReply(ID, cmd),
      _bucket(cmd.getBucket()),
      _bucketInfo(bucketInfo),
      _fileSizeOnSrc(sourceFileSize),
      _fileSizeOnDst(destinationFileSize),
      _srcDisk(cmd.getSrcDisk()),
      _dstDisk(cmd.getDstDisk())
{ }

BucketDiskMoveReply::~BucketDiskMoveReply() = default;

void
BucketDiskMoveReply::print(std::ostream& out, bool, const std::string&) const
{
    out << "BucketDiskMoveReply(" << _bucket.getBucketId() << ", source " << _srcDisk
        << ", target " << _dstDisk << ", " << _bucketInfo << ", " << getResult() << ")";
}

std::unique_ptr<api::StorageReply>
BucketDiskMoveCommand::makeReply()
{
    return std::make_unique<BucketDiskMoveReply>(*this);
}


InternalBucketJoinCommand::InternalBucketJoinCommand(const document::Bucket &bucket,
                                                     uint16_t keepOnDisk, uint16_t joinFromDisk)
    : api::InternalCommand(ID),
      _bucket(bucket),
      _keepOnDisk(keepOnDisk),
      _joinFromDisk(joinFromDisk)
{
    setPriority(HIGH); // To not get too many pending of these, prioritize
                       // them higher than getting more bucket info lists.
}

InternalBucketJoinCommand::~InternalBucketJoinCommand() = default;

void
InternalBucketJoinCommand::print(std::ostream& out, bool verbose, const std::string& indent) const {
    out << "InternalBucketJoinCommand()";

    if (verbose) {
        out << " : ";
        InternalCommand::print(out, true, indent);
    }
}

InternalBucketJoinReply::InternalBucketJoinReply(const InternalBucketJoinCommand& cmd,
                                                 const api::BucketInfo& info)
    : api::InternalReply(ID, cmd),
      _bucket(cmd.getBucket()),
      _bucketInfo(info)
{ }

InternalBucketJoinReply::~InternalBucketJoinReply() = default;

void
InternalBucketJoinReply::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "InternalBucketJoinReply()";

    if (verbose) {
        out << " : ";
        InternalReply::print(out, true, indent);
    }
}

std::unique_ptr<api::StorageReply>
InternalBucketJoinCommand::makeReply()
{
    return std::make_unique<InternalBucketJoinReply>(*this);
}

} // storage

