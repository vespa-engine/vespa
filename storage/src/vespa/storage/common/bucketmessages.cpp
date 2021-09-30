// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmessages.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>

using document::BucketSpace;

namespace storage {

ReadBucketList::ReadBucketList(BucketSpace bucketSpace)
    : api::InternalCommand(ID),
      _bucketSpace(bucketSpace)
{ }

ReadBucketList::~ReadBucketList() = default;

document::Bucket
ReadBucketList::getBucket() const
{
    return document::Bucket(_bucketSpace, document::BucketId());
}

void
ReadBucketList::print(std::ostream& out, bool verbose, const std::string& indent) const {
    out << "ReadBucketList()";

    if (verbose) {
        out << " : ";
        InternalCommand::print(out, true, indent);
    }
}

ReadBucketListReply::ReadBucketListReply(const ReadBucketList& cmd)
    : api::InternalReply(ID, cmd),
      _bucketSpace(cmd.getBucketSpace())
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

} // storage

