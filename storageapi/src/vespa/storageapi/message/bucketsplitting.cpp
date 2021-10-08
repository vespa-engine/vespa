// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketsplitting.h"
#include <ostream>
#include <limits>

namespace storage::api {

IMPLEMENT_COMMAND(SplitBucketCommand, SplitBucketReply)
IMPLEMENT_REPLY(SplitBucketReply)
IMPLEMENT_COMMAND(JoinBucketsCommand, JoinBucketsReply)
IMPLEMENT_REPLY(JoinBucketsReply)

SplitBucketCommand::SplitBucketCommand(const document::Bucket &bucket)
    : MaintenanceCommand(MessageType::SPLITBUCKET, bucket),
      _minSplitBits(0),
      _maxSplitBits(58),
      _minByteSize(std::numeric_limits<uint32_t>::max()),
      _minDocCount(std::numeric_limits<uint32_t>::max())
{
    // By default, set very large sizes, to ensure we trigger 'already big
    // enough' behaviour, only splitting one step by default. The distributor
    // should always overwrite one of these values to get correct behaviour.
}

void
SplitBucketCommand::print(std::ostream& out, bool verbose,
                          const std::string& indent) const
{
    out << "SplitBucketCommand(" << getBucketId();
    if (_minDocCount != std::numeric_limits<uint32_t>::max()
        || _minByteSize != std::numeric_limits<uint32_t>::max())
    {
        out << "Max doc count: " << _minDocCount
            << ", Max total doc size: " << _minByteSize;
    } else if (_maxSplitBits != 58) {
        out << "Max split bits to use: " << _maxSplitBits;
    }
    out << ")";
    out << " Reasons to start: " << _reason;
    if (verbose) {
        out << " : ";
        BucketCommand::print(out, verbose, indent);
    }
}

SplitBucketReply::SplitBucketReply(const SplitBucketCommand& cmd)
    : BucketReply(cmd)
{
}

void
SplitBucketReply::print(std::ostream& out, bool verbose,
                          const std::string& indent) const
{
    out << "SplitBucketReply(" << getBucketId();
    if (_result.empty()) {
        out << " - No target files created.";
    } else {
        out << " ->";
        for (uint32_t i=0; i<_result.size(); ++i) {
            out << "\n" << indent << "  " << _result[i].first << ": "
                << _result[i].second;
        }
    }
    out << ")";
    if (verbose) {
        out << " : ";
        BucketReply::print(out, verbose, indent);
    }
}

JoinBucketsCommand::JoinBucketsCommand(const document::Bucket &target)
    : MaintenanceCommand(MessageType::JOINBUCKETS, target),
      _minJoinBits(0)
{
}

void
JoinBucketsCommand::print(std::ostream& out, bool verbose,
                          const std::string& indent) const
{
    out << "JoinBucketsCommand(" << getBucketId();
    if (_sources.empty()) {
        out << " - No files to join.";
    } else {
        out << " <-";
        for (uint32_t i=0; i<_sources.size(); ++i) {
            out << " " << _sources[i];
        }
    }
    out << ")";
    out << " Reasons to start: " << _reason;
    if (verbose) {
        out << " : ";
        BucketCommand::print(out, verbose, indent);
    }
}


JoinBucketsReply::JoinBucketsReply(const JoinBucketsCommand& cmd)
    : BucketInfoReply(cmd),
      _sources(cmd.getSourceBuckets())
{
}

JoinBucketsReply::JoinBucketsReply(const JoinBucketsCommand& cmd, const BucketInfo& bucketInfo)
    : BucketInfoReply(cmd),
      _sources(cmd.getSourceBuckets())
{
    setBucketInfo(bucketInfo);
}

void
JoinBucketsReply::print(std::ostream& out, bool verbose,
                        const std::string& indent) const
{
    out << "JoinBucketsReply(" << getBucketId();
    if (_sources.empty()) {
        out << " - No files to join.";
    } else {
        out << " <-";
        for (uint32_t i=0; i<_sources.size(); ++i) {
            out << " " << _sources[i];
        }
    }
    out << ")";
    if (verbose) {
        out << " : ";
        BucketReply::print(out, verbose, indent);
    }
}

}
