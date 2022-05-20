// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visitor.h"
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/vespalib/util/array.hpp>
#include <climits>
#include <ostream>

namespace storage::api {

IMPLEMENT_COMMAND(CreateVisitorCommand, CreateVisitorReply)
IMPLEMENT_REPLY(CreateVisitorReply)
IMPLEMENT_COMMAND(DestroyVisitorCommand, DestroyVisitorReply)
IMPLEMENT_REPLY(DestroyVisitorReply)
IMPLEMENT_COMMAND(VisitorInfoCommand, VisitorInfoReply)
IMPLEMENT_REPLY(VisitorInfoReply)

CreateVisitorCommand::CreateVisitorCommand(document::BucketSpace bucketSpace,
                                           vespalib::stringref libraryName,
                                           vespalib::stringref instanceId,
                                           vespalib::stringref docSelection)
    : StorageCommand(MessageType::VISITOR_CREATE),
      _bucketSpace(bucketSpace),
      _libName(libraryName),
      _params(),
      _controlDestination(),
      _dataDestination(),
      _docSelection(docSelection),
      _buckets(),
      _fromTime(0),
      _toTime(api::MAX_TIMESTAMP),
      _visitorCmdId(getMsgId()),
      _instanceId(instanceId),
      _visitorId(0),
      _visitRemoves(false),
      _fieldSet(document::AllFields::NAME),
      _visitInconsistentBuckets(false),
      _queueTimeout(2000ms),
      _maxPendingReplyCount(2),
      _version(50),
      _maxBucketsPerVisitor(1)
{
}

CreateVisitorCommand::CreateVisitorCommand(const CreateVisitorCommand& o)
    : StorageCommand(o),
      _bucketSpace(o._bucketSpace),
      _libName(o._libName),
      _params(o._params),
      _controlDestination(o._controlDestination),
      _dataDestination(o._dataDestination),
      _docSelection(o._docSelection),
      _buckets(o._buckets),
      _fromTime(o._fromTime),
      _toTime(o._toTime),
      _visitorCmdId(getMsgId()),
      _instanceId(o._instanceId),
      _visitorId(o._visitorId),
      _visitRemoves(o._visitRemoves),
      _fieldSet(o._fieldSet),
      _visitInconsistentBuckets(o._visitInconsistentBuckets),
      _queueTimeout(o._queueTimeout),
      _maxPendingReplyCount(o._maxPendingReplyCount),
      _version(o._version),
      _maxBucketsPerVisitor(o._maxBucketsPerVisitor)
{
}

CreateVisitorCommand::~CreateVisitorCommand() = default;

document::Bucket
CreateVisitorCommand::getBucket() const
{
    return document::Bucket(_bucketSpace, document::BucketId());
}

document::BucketId
CreateVisitorCommand::super_bucket_id() const
{
    if (_buckets.empty()) {
        // TODO STRIPE: Is this actually an error situation? Should be fixed elsewhere.
        return document::BucketId();
    }
    return _buckets[0];
}

void
CreateVisitorCommand::print(std::ostream& out, bool verbose,
                            const std::string& indent) const
{
    out << "CreateVisitorCommand(" << _libName << ", " << _docSelection;
    if (verbose) {
        out << ") {";
        out << "\n" << indent << "  Library name: '" << _libName << "'";
        out << "\n" << indent << "  Instance Id: '" << _instanceId << "'";
        out << "\n" << indent << "  Control Destination: '" << _controlDestination << "'";
        out << "\n" << indent << "  Data Destination: '" << _dataDestination << "'";
        out << "\n" << indent << "  Doc Selection: '" << _docSelection << "'";
        out << "\n" << indent << "  Max pending: '" << _maxPendingReplyCount << "'";
        out << "\n" << indent << "  Timeout: " << vespalib::count_ms(getTimeout()) << " ms";
        out << "\n" << indent << "  Queue timeout: " << vespalib::count_ms(_queueTimeout) << " ms";
        out << "\n" << indent << "  VisitorDispatcher version: '" << _version << "'";
        if (visitRemoves()) {
            out << "\n" << indent << "  Visiting remove entries too";
        }

        out << "\n" << indent << "  Returning fields: " << _fieldSet;

        if (visitInconsistentBuckets()) {
            out << "\n" << indent << "  Visiting inconsistent buckets";
        }
        out << "\n" << indent << "  From " << _fromTime << " to " << _toTime;
        for (std::vector<document::BucketId>::const_iterator it
                = _buckets.begin(); it != _buckets.end(); ++it)
        {
            out << "\n" << indent << "  " << (*it);
        }
        out << "\n" << indent << "  ";
        _params.print(out, verbose, indent + "  ");
        out << "\n" << indent << "  Max buckets: '" << _maxBucketsPerVisitor << "'";
        out << "\n" << indent << "} : ";
        StorageCommand::print(out, verbose, indent);
    } else if (_buckets.size() == 2) {
        out << ", top " << _buckets[0] << ", progress " << _buckets[1] << ")";
    } else {
        out << ", " << _buckets.size() << " buckets)";
    }

}

CreateVisitorReply::CreateVisitorReply(const CreateVisitorCommand& cmd)
    : StorageReply(cmd),
      _super_bucket_id(cmd.super_bucket_id()),
      _lastBucket(document::BucketId(INT_MAX))
{
}

void
CreateVisitorReply::print(std::ostream& out, bool verbose,
                          const std::string& indent) const
{
    out << "CreateVisitorReply(last=" << _lastBucket << ")";
    if (verbose) {
        out << " : ";
        StorageReply::print(out, verbose, indent);
    }
}

DestroyVisitorCommand::DestroyVisitorCommand(vespalib::stringref instanceId)
    : StorageCommand(MessageType::VISITOR_DESTROY),
      _instanceId(instanceId)
{
}

void
DestroyVisitorCommand::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "DestroyVisitorCommand(" << _instanceId << ")";
    if (verbose) {
        out << " : ";
        StorageCommand::print(out, verbose, indent);
    }
}

DestroyVisitorReply::DestroyVisitorReply(const DestroyVisitorCommand& cmd)
    : StorageReply(cmd)
{
}

void
DestroyVisitorReply::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "DestroyVisitorReply()";
    if (verbose) {
        out << " : ";
        StorageReply::print(out, verbose, indent);
    }
}

VisitorInfoCommand::VisitorInfoCommand()
    : StorageCommand(MessageType::VISITOR_INFO),
      _completed(false),
      _bucketsCompleted(),
      _error(ReturnCode::OK)
{
}

VisitorInfoCommand::~VisitorInfoCommand() = default;

void
VisitorInfoCommand::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "VisitorInfoCommand(";
    if (_completed) { out << "completed"; }
    if (_error.failed()) {
        out << _error;
    }
    if (verbose) {
        out << ") : ";
        StorageCommand::print(out, verbose, indent);
    } else {
        if (!_bucketsCompleted.empty()) {
            out << _bucketsCompleted.size() << " buckets completed";
        }
        out << ")";
    }
}

VisitorInfoReply::VisitorInfoReply(const VisitorInfoCommand& cmd)
    : StorageReply(cmd),
      _completed(cmd.visitorCompleted())
{
}

void
VisitorInfoReply::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "VisitorInfoReply(";
    if (_completed) { out << "completed"; }
    if (verbose) {
        out << ") : ";
        StorageReply::print(out, verbose, indent);
    } else {
        out << ")";
    }
}

std::ostream&
operator<<(std::ostream& out, const VisitorInfoCommand::BucketTimestampPair& pair) {
    return out << pair.bucketId << " - " << pair.timestamp;
}

}
