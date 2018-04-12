// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/array.hpp>
#include <ostream>
#include <iterator>

namespace storage::api {

IMPLEMENT_COMMAND(CreateBucketCommand, CreateBucketReply)
IMPLEMENT_REPLY(CreateBucketReply)
IMPLEMENT_COMMAND(DeleteBucketCommand, DeleteBucketReply)
IMPLEMENT_REPLY(DeleteBucketReply)
IMPLEMENT_COMMAND(MergeBucketCommand, MergeBucketReply)
IMPLEMENT_REPLY(MergeBucketReply)
IMPLEMENT_COMMAND(GetBucketDiffCommand, GetBucketDiffReply)
IMPLEMENT_REPLY(GetBucketDiffReply)
IMPLEMENT_COMMAND(ApplyBucketDiffCommand, ApplyBucketDiffReply)
IMPLEMENT_REPLY(ApplyBucketDiffReply)
IMPLEMENT_COMMAND(RequestBucketInfoCommand, RequestBucketInfoReply)
IMPLEMENT_REPLY(RequestBucketInfoReply)
IMPLEMENT_COMMAND(NotifyBucketChangeCommand, NotifyBucketChangeReply)
IMPLEMENT_REPLY(NotifyBucketChangeReply)
IMPLEMENT_COMMAND(SetBucketStateCommand, SetBucketStateReply)
IMPLEMENT_REPLY(SetBucketStateReply)

CreateBucketCommand::CreateBucketCommand(const document::Bucket &bucket)
    : MaintenanceCommand(MessageType::CREATEBUCKET, bucket),
      _active(false)
{ }

void
CreateBucketCommand::print(std::ostream& out, bool verbose,
                           const std::string& indent) const
{
    out << "CreateBucketCommand(" << getBucketId();
    if (_active) {
        out << ", active";
    } else {
        out << ", inactive";
    }
    out << ")";
    out << " Reasons to start: " << _reason;
    if (verbose) {
        out << " : ";
        MaintenanceCommand::print(out, verbose, indent);
    }
}

CreateBucketReply::CreateBucketReply(const CreateBucketCommand& cmd)
    : BucketInfoReply(cmd)
{
}

void
CreateBucketReply::print(std::ostream& out, bool verbose,
                         const std::string& indent) const
{
    out << "CreateBucketReply(" << getBucketId() << ")";
    if (verbose) {
        out << " : ";
        BucketInfoReply::print(out, verbose, indent);
    }
}

DeleteBucketCommand::DeleteBucketCommand(const document::Bucket &bucket)
    : MaintenanceCommand(MessageType::DELETEBUCKET, bucket)
{ }

void
DeleteBucketCommand::print(std::ostream& out, bool verbose,
                           const std::string& indent) const
{
    out << "DeleteBucketCommand(" << getBucketId() << ")";
    out << " Reasons to start: " << _reason;
    if (verbose) {
        out << " : ";
        MaintenanceCommand::print(out, verbose, indent);
    }
}

DeleteBucketReply::DeleteBucketReply(const DeleteBucketCommand& cmd)
    : BucketInfoReply(cmd)
{
}

void
DeleteBucketReply::print(std::ostream& out, bool verbose,
                         const std::string& indent) const
{
    out << "DeleteBucketReply(" << getBucketId() << ")";
    if (verbose) {
        out << " : ";
        BucketInfoReply::print(out, verbose, indent);
    }
}

MergeBucketCommand::MergeBucketCommand(
        const document::Bucket &bucket, const std::vector<Node>& nodes,
        Timestamp maxTimestamp, uint32_t clusterStateVersion,
        const std::vector<uint16_t>& chain)
    : MaintenanceCommand(MessageType::MERGEBUCKET, bucket),
      _nodes(nodes),
      _maxTimestamp(maxTimestamp),
      _clusterStateVersion(clusterStateVersion),
      _chain(chain)
{}

MergeBucketCommand::~MergeBucketCommand() {}

void
MergeBucketCommand::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "MergeBucketCommand(" << getBucketId() << ", to time "
        << _maxTimestamp << ", cluster state version: "
        << _clusterStateVersion << ", nodes: [";
    for (uint32_t i=0; i<_nodes.size(); ++i) {
        if (i != 0) out << ", ";
        out << _nodes[i];
    }
    out << "], chain: [";
    for (uint32_t i = 0; i < _chain.size(); ++i) {
        if (i != 0) out << ", ";
        out << _chain[i];
    }
    out << "]";
    out << ", reasons to start: " << _reason;
    out << ")";
    if (verbose) {
        out << " : ";
        BucketCommand::print(out, verbose, indent);
    }
}

std::ostream&
operator<<(std::ostream& out, const MergeBucketCommand::Node& n) {
    return out << n.index << (n.sourceOnly ? " (source only)" : "");
}

MergeBucketReply::MergeBucketReply(const MergeBucketCommand& cmd)
    : BucketReply(cmd),
      _nodes(cmd.getNodes()),
      _maxTimestamp(cmd.getMaxTimestamp()),
      _clusterStateVersion(cmd.getClusterStateVersion()),
      _chain(cmd.getChain())
{
}

void
MergeBucketReply::print(std::ostream& out, bool verbose,
                        const std::string& indent) const
{
    out << "MergeBucketReply(" << getBucketId() << ", to time "
        << _maxTimestamp << ", cluster state version: "
        << _clusterStateVersion << ", nodes: ";
    for (uint32_t i=0; i<_nodes.size(); ++i) {
        if (i != 0) out << ", ";
        out << _nodes[i];
    }
    out << "], chain: [";
    for (uint32_t i = 0; i < _chain.size(); ++i) {
        if (i != 0) out << ", ";
        out << _chain[i];
    }
    out << "])";
    if (verbose) {
        out << " : ";
        BucketReply::print(out, verbose, indent);
    }
}

GetBucketDiffCommand::Entry::Entry()
    : _timestamp(0),
      _gid(),
      _headerSize(0),
      _bodySize(0),
      _flags(0),
      _hasMask(0)
{
}

void GetBucketDiffCommand::Entry::print(std::ostream& out, bool verbose,
                                        const std::string& indent) const
{
    out << "Entry(timestamp: " << _timestamp
        << ", " << _gid.toString() << ", hasMask: 0x" << _hasMask;
    if (verbose) {
        out << ",\n" << indent << "      " << "header size: "
            << std::dec << _headerSize << ", body size: " << _bodySize
            << ", flags 0x" << std::hex << _flags << std::dec;
    }
    out << ")";
}

bool GetBucketDiffCommand::Entry::operator==(const Entry& e) const
{
    return (_timestamp == e._timestamp &&
            _headerSize == e._headerSize &&
            _bodySize == e._bodySize &&
            _gid == e._gid &&
            _flags == e._flags);
}

GetBucketDiffCommand::GetBucketDiffCommand(
        const document::Bucket &bucket, const std::vector<Node>& nodes,
        Timestamp maxTimestamp)
    : BucketCommand(MessageType::GETBUCKETDIFF, bucket),
      _nodes(nodes),
      _maxTimestamp(maxTimestamp)
{}

GetBucketDiffCommand::~GetBucketDiffCommand() {}

void
GetBucketDiffCommand::print(std::ostream& out, bool verbose,
                            const std::string& indent) const
{
    out << "GetBucketDiffCommand(" << getBucketId() << ", to time "
        << _maxTimestamp << ", nodes: ";
    for (uint32_t i=0; i<_nodes.size(); ++i) {
        if (i != 0) out << ", ";
        out << _nodes[i];
    }
    if (_diff.empty()) {
        out << ", no entries";
    } else if (verbose) {
        out << ",";
        for (uint32_t i=0; i<_diff.size(); ++i) {
            out << "\n" << indent << "  ";
            _diff[i].print(out, verbose, indent + "  ");
        }
    } else {
        out << ", " << _diff.size() << " entries";
        out << ", id " << _msgId;
    }
    out << ")";
    if (verbose) {
        out << " : ";
        BucketCommand::print(out, verbose, indent);
    }
}

GetBucketDiffReply::GetBucketDiffReply(const GetBucketDiffCommand& cmd)
    : BucketReply(cmd),
      _nodes(cmd.getNodes()),
      _maxTimestamp(cmd.getMaxTimestamp()),
      _diff(cmd.getDiff())
{}

GetBucketDiffReply::~GetBucketDiffReply() {}

void
GetBucketDiffReply::print(std::ostream& out, bool verbose,
                          const std::string& indent) const
{
    out << "GetBucketDiffReply(" << getBucketId() << ", to time "
        << _maxTimestamp << ", nodes: ";
    for (uint32_t i=0; i<_nodes.size(); ++i) {
        if (i != 0) out << ", ";
        out << _nodes[i];
    }
    if (_diff.empty()) {
        out << ", no entries";
    } else if (verbose) {
        out << ",";
        for (uint32_t i=0; i<_diff.size(); ++i) {
            out << "\n" << indent << "  ";
            _diff[i].print(out, verbose, indent + "  ");
        }
    } else {
        out << ", " << _diff.size() << " entries";
        out << ", id " << _msgId;
    }
    out << ")";
    if (verbose) {
        out << " : ";
        BucketReply::print(out, verbose, indent);
    }
}

ApplyBucketDiffCommand::Entry::Entry()
    : _entry(),
      _docName(),
      _headerBlob(),
      _bodyBlob(),
      _repo()
{}

ApplyBucketDiffCommand::Entry::Entry(const GetBucketDiffCommand::Entry& e)
    : _entry(e),
      _docName(),
      _headerBlob(),
      _bodyBlob(),
      _repo()
{}

ApplyBucketDiffCommand::Entry::~Entry() {}
ApplyBucketDiffCommand::Entry::Entry(const Entry &) = default;
ApplyBucketDiffCommand::Entry & ApplyBucketDiffCommand::Entry::operator = (const Entry &) = default;

bool
ApplyBucketDiffCommand::Entry::filled() const
{
    return ((_headerBlob.size() > 0 ||
             (_entry._headerSize  == 0 && !_docName.empty())) &&
             (_bodyBlob.size() > 0 ||
              _entry._bodySize == 0));
}

void
ApplyBucketDiffCommand::Entry::print(
        std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "ApplyEntry(";
    _entry.print(out, verbose, indent + "           ");
    out << ",\n" << indent << "  name(" << _docName
        << "), headerBlob(" << _headerBlob.size()
        << "), bodyBlob(" << _bodyBlob.size() << ")";
    if (_headerBlob.size() > 0) {
        document::ByteBuffer buf(&_headerBlob[0],
                                 _headerBlob.size());
        if (_repo) {
            document::Document doc(*_repo, buf);
            out << ",\n" << indent << "  " << doc.getId().getGlobalId().toString();
        } else {
            out << ",\n" << indent << "  unknown global id. (repo missing)";
        }
    }
    out << ")";
}

bool
ApplyBucketDiffCommand::Entry::operator==(const Entry& e) const
{
    return (_entry == e._entry &&
            _headerBlob == e._headerBlob &&
            _bodyBlob == e._bodyBlob);
}

ApplyBucketDiffCommand::ApplyBucketDiffCommand(
        const document::Bucket &bucket, const std::vector<Node>& nodes,
        uint32_t maxBufferSize)
    : BucketInfoCommand(MessageType::APPLYBUCKETDIFF, bucket),
      _nodes(nodes),
      _diff(),
      _maxBufferSize(maxBufferSize)
{}

ApplyBucketDiffCommand::~ApplyBucketDiffCommand() {}

void
ApplyBucketDiffCommand::print(std::ostream& out, bool verbose,
                              const std::string& indent) const
{
    uint32_t totalSize = 0;
    uint32_t filled = 0;
    for (std::vector<Entry>::const_iterator it = _diff.begin();
         it != _diff.end(); ++it)
    {
        totalSize += it->_headerBlob.size();
        totalSize += it->_bodyBlob.size();
        if (it->filled()) ++filled;
    }
    out << "ApplyBucketDiffCommand(" << getBucketId() << ", nodes: ";
    for (uint32_t i=0; i<_nodes.size(); ++i) {
        if (i != 0) out << ", ";
        out << _nodes[i];
    }
    out << ", max buffer size " << _maxBufferSize << " bytes"
        << ", " << _diff.size() << " entries of " << totalSize << " bytes, "
        << (100.0 * filled / _diff.size()) << " \% filled)";
    if (_diff.empty()) {
        out << ", no entries";
    } else if (verbose) {
        out << ",";
        for (uint32_t i=0; i<_diff.size(); ++i) {
            out << "\n" << indent << "  ";
            _diff[i].print(out, verbose, indent + "  ");
        }
    } else {
        out << ", " << _diff.size() << " entries";
        out << ", id " << _msgId;
    }
    out << ")";
    if (verbose) {
        out << " : ";
        BucketCommand::print(out, verbose, indent);
    }
}

ApplyBucketDiffReply::ApplyBucketDiffReply(const ApplyBucketDiffCommand& cmd)
    : BucketInfoReply(cmd),
      _nodes(cmd.getNodes()),
      _diff(cmd.getDiff()),
      _maxBufferSize(cmd.getMaxBufferSize())
{}

ApplyBucketDiffReply::~ApplyBucketDiffReply() {}

void
ApplyBucketDiffReply::print(std::ostream& out, bool verbose,
                            const std::string& indent) const
{
    uint32_t totalSize = 0;
    uint32_t filled = 0;
    for (std::vector<Entry>::const_iterator it = _diff.begin();
         it != _diff.end(); ++it)
    {
        totalSize += it->_headerBlob.size();
        totalSize += it->_bodyBlob.size();
        if (it->filled()) ++filled;
    }
    out << "ApplyBucketDiffReply(" << getBucketId() << ", nodes: ";
    for (uint32_t i=0; i<_nodes.size(); ++i) {
        if (i != 0) out << ", ";
        out << _nodes[i];
    }
    out << ", max buffer size " << _maxBufferSize << " bytes"
        << ", " << _diff.size() << " entries of " << totalSize << " bytes, "
        << (100.0 * filled / _diff.size()) << " \% filled)";
    if (_diff.empty()) {
        out << ", no entries";
    } else if (verbose) {
        out << ",";
        for (uint32_t i=0; i<_diff.size(); ++i) {
            out << "\n" << indent << "  ";
            _diff[i].print(out, verbose, indent + "  ");
        }
    } else {
        out << ", " << _diff.size() << " entries";
        out << ", id " << _msgId;
    }
    out << ")";
    if (verbose) {
        out << " : ";
        BucketInfoReply::print(out, verbose, indent);
    }
}

RequestBucketInfoCommand::RequestBucketInfoCommand(
        document::BucketSpace bucketSpace,
        const std::vector<document::BucketId>& buckets)
    : StorageCommand(MessageType::REQUESTBUCKETINFO),
      _bucketSpace(bucketSpace),
      _buckets(buckets),
      _state(),
      _distributor(0xFFFF)
{
}

RequestBucketInfoCommand::RequestBucketInfoCommand(
        document::BucketSpace bucketSpace,
        uint16_t distributor, const lib::ClusterState& state,
        const vespalib::stringref & distributionHash)
    : StorageCommand(MessageType::REQUESTBUCKETINFO),
      _bucketSpace(bucketSpace),
      _buckets(),
      _state(new lib::ClusterState(state)),
      _distributor(distributor),
      _distributionHash(distributionHash)
{
}

RequestBucketInfoCommand::RequestBucketInfoCommand(
        document::BucketSpace bucketSpace,
        uint16_t distributor, const lib::ClusterState& state)
    : StorageCommand(MessageType::REQUESTBUCKETINFO),
      _bucketSpace(bucketSpace),
      _buckets(),
      _state(new lib::ClusterState(state)),
      _distributor(distributor),
      _distributionHash("")
{
}

document::Bucket
RequestBucketInfoCommand::getBucket() const
{
    return document::Bucket(_bucketSpace, document::BucketId());
}

void
RequestBucketInfoCommand::print(std::ostream& out, bool verbose,
                                const std::string& indent) const
{
    out << "RequestBucketInfoCommand(";
    if (_buckets.size() != 0) {
        out << _buckets.size() << " buckets";
    }
    if (hasSystemState()) {
        out << "distributor " << _distributor << " in ";
        _state->print(out, verbose, indent + "  ");
    }
    if (verbose && _buckets.size() > 0) {
        out << "\n" << indent << "  Specified buckets:\n" << indent << "    ";
        std::copy(_buckets.begin(), _buckets.end(),
                  std::ostream_iterator<document::BucketId>(
                      out, ("\n" + indent + "    ").c_str()));
    }
    out << ")";
    if (verbose) {
        out << " : ";
        StorageCommand::print(out, verbose, indent);
    }
}

std::ostream& operator<<(std::ostream& out,
                         const RequestBucketInfoReply::Entry& e)
{
    return out << e._bucketId << " - " << e._info;
}


RequestBucketInfoReply::RequestBucketInfoReply(
        const RequestBucketInfoCommand& cmd)
    : StorageReply(cmd),
      _buckets()
{ }

RequestBucketInfoReply::~RequestBucketInfoReply() { }

void
RequestBucketInfoReply::print(std::ostream& out, bool verbose,
                              const std::string& indent) const
{
    out << "RequestBucketInfoReply(" << _buckets.size();
    if (verbose) {
        out << "\n" << indent << "  ";
        std::copy(_buckets.begin(), _buckets.end(),
                  std::ostream_iterator<Entry>(out,
                      ("\n" + indent + "  ").c_str()));
    }
    out << ")";
    if (verbose) {
        out << " : ";
        StorageReply::print(out, verbose, indent);
    }
}

NotifyBucketChangeCommand::NotifyBucketChangeCommand(
        const document::Bucket &bucket, const BucketInfo& info)
    : BucketCommand(MessageType::NOTIFYBUCKETCHANGE, bucket),
      _info(info)
{
}

void
NotifyBucketChangeCommand::print(std::ostream& out, bool verbose,
                                 const std::string& indent) const
{
    out << "NotifyBucketChangeCommand(" << getBucketId() << ", ";
    _info.print(out, verbose, indent);
    out << ")";
    if (verbose) {
        out << " : ";
        StorageCommand::print(out, verbose, indent);
    }
}

NotifyBucketChangeReply::NotifyBucketChangeReply(
        const NotifyBucketChangeCommand& cmd)
    : BucketReply(cmd)
{
}

void
NotifyBucketChangeReply::print(std::ostream& out, bool verbose,
                              const std::string& indent) const
{
    out << "NotifyBucketChangeReply(" << getBucketId() << ")";
    if (verbose) {
        out << " : ";
        BucketReply::print(out, verbose, indent);
    }
}

SetBucketStateCommand::SetBucketStateCommand(
        const document::Bucket &bucket,
        BUCKET_STATE state)
    : MaintenanceCommand(MessageType::SETBUCKETSTATE, bucket),
      _state(state)
{
}

void
SetBucketStateCommand::print(std::ostream& out,
                             bool verbose,
                             const std::string& indent) const
{
    out << "SetBucketStateCommand(" << getBucketId() << ", ";
    switch (_state) {
    case INACTIVE:
        out << "INACTIVE";
        break;
    case ACTIVE:
        out << "ACTIVE";
        break;
    }
    out << ")";
    if (verbose) {
        out << " : ";
        MaintenanceCommand::print(out, verbose, indent);
    }
}

vespalib::string
SetBucketStateCommand::getSummary() const
{
    vespalib::asciistream stream;
    stream << "SetBucketStateCommand(" << getBucketId().toString() << ", "
           << ((_state == ACTIVE) ? "ACTIVE" : "INACTIVE") << ")";
    return stream.str();
}

SetBucketStateReply::SetBucketStateReply(
        const SetBucketStateCommand& cmd)
    : BucketInfoReply(cmd)
{
}

void
SetBucketStateReply::print(std::ostream& out,
                           bool verbose,
                           const std::string& indent) const
{
    out << "SetBucketStateReply(" << getBucketId() << ")";
    if (verbose) {
        out << " : ";
        BucketInfoReply::print(out, verbose, indent);
    }
}

}

template class vespalib::Array<storage::api::RequestBucketInfoReply::Entry>;
