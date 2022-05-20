// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "datagram.h"
#include <ostream>

using document::BucketSpace;

namespace storage {
namespace api {

IMPLEMENT_COMMAND(MapVisitorCommand, MapVisitorReply)
IMPLEMENT_REPLY(MapVisitorReply)
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

} // api
} // storage
