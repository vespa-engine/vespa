// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "documentsummary.h"
#include <ostream>

namespace storage {
namespace api {

IMPLEMENT_COMMAND(DocumentSummaryCommand, DocumentSummaryReply)
IMPLEMENT_REPLY(DocumentSummaryReply)

DocumentSummaryCommand::DocumentSummaryCommand()
    : StorageCommand(MessageType::DOCUMENTSUMMARY),
      DocumentSummary()
{ }

void
DocumentSummaryCommand::print(std::ostream& out, bool verbose,
                              const std::string& indent) const
{
    out << "DocumentSummary(" << getSummaryCount() << " summaries)";
    if (verbose) {
        out << " : ";
        StorageCommand::print(out, verbose, indent);
    }
}

DocumentSummaryReply::DocumentSummaryReply(const DocumentSummaryCommand& cmd)
    : StorageReply(cmd)
{ }

void
DocumentSummaryReply::print(std::ostream& out, bool verbose,
                            const std::string& indent) const
{
    out << "DocumentSummaryReply()";
    if (verbose) {
        out << " : ";
        StorageReply::print(out, verbose, indent);
    }
}

} // api
} // storage
