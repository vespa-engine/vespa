// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryresult.h"
#include <ostream>

namespace storage {
namespace api {

IMPLEMENT_COMMAND(QueryResultCommand, QueryResultReply)
IMPLEMENT_REPLY(QueryResultReply)

QueryResultCommand::QueryResultCommand()
    : StorageCommand(MessageType::QUERYRESULT),
      _searchResult(),
      _summary()
{ }

void
QueryResultCommand::print(std::ostream& out, bool verbose,
                           const std::string& indent) const
{
    out << "QueryResultCommand(" << _searchResult.getHitCount() << " hits)";
    if (verbose) {
        out << " : ";
        StorageCommand::print(out, verbose, indent);
    }
}

QueryResultReply::QueryResultReply(const QueryResultCommand& cmd)
    : StorageReply(cmd)
{ }

void
QueryResultReply::print(std::ostream& out, bool verbose,
                         const std::string& indent) const
{
    out << "QueryResultReply()";
    if (verbose) {
        out << " : ";
        StorageReply::print(out, verbose, indent);
    }
}

} // api
} // storage
