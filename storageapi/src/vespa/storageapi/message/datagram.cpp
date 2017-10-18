// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "datagram.h"

using document::BucketSpace;

namespace storage {
namespace api {

IMPLEMENT_COMMAND(MapVisitorCommand, MapVisitorReply)
IMPLEMENT_REPLY(MapVisitorReply)
IMPLEMENT_COMMAND(DocumentListCommand, DocumentListReply)
IMPLEMENT_REPLY(DocumentListReply)
IMPLEMENT_COMMAND(EmptyBucketsCommand, EmptyBucketsReply)
IMPLEMENT_REPLY(EmptyBucketsReply)

MapVisitorCommand::MapVisitorCommand()
    : StorageCommand(MessageType::MAPVISITOR)
{
}

void
MapVisitorCommand::print(std::ostream& out, bool verbose,
                         const std::string& indent) const
{
    out << "MapVisitor(" << _statistics.size() << " entries";
    if (verbose) {
        for (vdslib::Parameters::ParametersMap::const_iterator it
                = _statistics.begin(); it != _statistics.end(); ++it)
        {
            out << ",\n" << indent << "  " << it->first << ": "
                << vespalib::stringref(it->second.c_str(), it->second.length());
        }
        out << ") : ";
        StorageCommand::print(out, verbose, indent);
    } else {
        out << ")";
    }
}

MapVisitorReply::MapVisitorReply(const MapVisitorCommand& cmd)
    : StorageReply(cmd)
{
}

void
MapVisitorReply::print(std::ostream& out, bool verbose,
                       const std::string& indent) const
{
    out << "MapVisitorReply()";
    if (verbose) {
        out << " : ";
        StorageReply::print(out, verbose, indent);
    }
}

DocumentListCommand::DocumentListCommand(const document::BucketId& bid)
    : StorageCommand(MessageType::DOCUMENTLIST),
      _bucket(BucketSpace::placeHolder(), bid),
      _documents()
{
}

void
DocumentListCommand::print(std::ostream& out, bool verbose,
                           const std::string& indent) const
{
    out << "DocumentList(" << _bucket.getBucketId();
    if (_documents.empty()) {
        out << ", empty";
    } else if (verbose) {
        out << ",";
        for (uint32_t i=0; i<_documents.size(); ++i) {
            out << "\n" << indent << "  ";
            out << ":" << _documents[i];
        }
    } else {
        out << ", " << _documents.size() << " documents";
    }
    out << ")";
    if (verbose) {
        out << " : ";
        StorageCommand::print(out, verbose, indent);
    }
}

DocumentListReply::DocumentListReply(const DocumentListCommand& cmd)
    : StorageReply(cmd)
{
}

void
DocumentListReply::print(std::ostream& out, bool verbose,
                         const std::string& indent) const
{
    out << "DocumentListReply()";
    if (verbose) {
        out << " : ";
        StorageReply::print(out, verbose, indent);
    }
}

EmptyBucketsCommand::EmptyBucketsCommand(
        const std::vector<document::BucketId>& buckets)
    : StorageCommand(MessageType::EMPTYBUCKETS),
      _buckets(buckets)
{
}

void
EmptyBucketsCommand::print(std::ostream& out, bool verbose,
                           const std::string& indent) const
{
    out << "EmptyBuckets(";
    if (verbose) {
        for (uint32_t i=0; i<_buckets.size(); ++i) {
            out << "\n" << indent << "  ";
            out << _buckets[i];
        }
    } else {
        out << _buckets.size() << " buckets";
    }
    out << ")";
    if (verbose) {
        out << " : ";
        StorageCommand::print(out, verbose, indent);
    }
}

EmptyBucketsReply::EmptyBucketsReply(const EmptyBucketsCommand& cmd)
    : StorageReply(cmd)
{
}

void
EmptyBucketsReply::print(std::ostream& out, bool verbose,
                         const std::string& indent) const
{
    out << "EmptyBucketsReply()";
    if (verbose) {
        out << " : ";
        StorageReply::print(out, verbose, indent);
    }
}

std::ostream& operator<<(std::ostream& out, const DocumentListCommand::Entry& e)
{
    out << e._doc->getId();
    if (e._removeEntry) out << " - removed";
    out << ", last modified at " << e._lastModified;
    return out;
}

} // api
} // storage
