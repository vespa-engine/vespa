// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stat.h"
#include <ostream>

namespace storage::api {

IMPLEMENT_COMMAND(StatBucketCommand, StatBucketReply)
IMPLEMENT_REPLY(StatBucketReply)
IMPLEMENT_COMMAND(GetBucketListCommand, GetBucketListReply)
IMPLEMENT_REPLY(GetBucketListReply)

StatBucketCommand::StatBucketCommand(const document::Bucket& bucket,
                                     vespalib::stringref documentSelection)
    : BucketCommand(MessageType::STATBUCKET, bucket),
      _docSelection(documentSelection)
{
}

StatBucketCommand::~StatBucketCommand() = default;

void
StatBucketCommand::print(std::ostream& out, bool verbose,
                         const std::string& indent) const
{
    out << "StatBucketCommand(" << getBucketId()
        << ", selection: " << _docSelection << ")";
    if (verbose) {
        out << " : ";
        BucketCommand::print(out, verbose, indent);
    }
}

StatBucketReply::StatBucketReply(const StatBucketCommand& cmd,
                                 vespalib::stringref results)
    : BucketReply(cmd),
      _results(results)
{
}

void
StatBucketReply::print(std::ostream& out, bool verbose,
                       const std::string& indent) const
{
    out << "StatBucketReply(" << getBucketId();
    if (verbose) {
        out << ", result: " << _results << ") : ";
        BucketReply::print(out, verbose, indent);
    } else {
        vespalib::string::size_type pos = _results.find('\n');
        vespalib::string overview;
        if (pos != vespalib::string::npos) {
            overview = _results.substr(0, pos) + " ...";
        } else {
            overview = _results;
        }
        out << ", result: " << overview << ")";
    }
}

GetBucketListCommand::GetBucketListCommand(const document::Bucket &bucket)
    : BucketCommand(MessageType::GETBUCKETLIST, bucket)
{
}

void
GetBucketListCommand::print(std::ostream& out, bool verbose,
                            const std::string& indent) const
{
    out << "GetBucketList(" << getBucketId() << ")";
    if (verbose) {
        out << " : ";
        BucketCommand::print(out, verbose, indent);
    }
}

GetBucketListReply::GetBucketListReply(const GetBucketListCommand& cmd)
    : BucketReply(cmd),
      _buckets()
{}

GetBucketListReply::~GetBucketListReply() {}

void
GetBucketListReply::print(std::ostream& out, bool verbose,
                          const std::string& indent) const
{
    out << "GetBucketListReply(" << getBucketId() << ", Info on "
        << _buckets.size() << " buckets)";
    if (verbose) {
        out << " : ";
        BucketReply::print(out, verbose, indent);
    }
}

std::ostream&
operator<<(std::ostream& out, const GetBucketListReply::BucketInfo& instance)
{
    out << "BucketInfo(" << instance._bucket << ": "
        << instance._bucketInformation << ")";
    return out;
}

}
