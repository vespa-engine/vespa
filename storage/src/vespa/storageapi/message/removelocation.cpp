// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "removelocation.h"
#include <ostream>

namespace storage::api {

IMPLEMENT_COMMAND(RemoveLocationCommand, RemoveLocationReply)
IMPLEMENT_REPLY(RemoveLocationReply)

RemoveLocationCommand::RemoveLocationCommand(vespalib::stringref documentSelection,
                                             const document::Bucket &bucket)
    : BucketInfoCommand(MessageType::REMOVELOCATION, bucket),
      _documentSelection(documentSelection),
      _explicit_remove_set(),
      _only_enumerate_docs(false)
{}

RemoveLocationCommand::~RemoveLocationCommand() = default;

void
RemoveLocationCommand::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    if (!_documentSelection.empty()) {
        out << "Remove selection(" << _documentSelection << "): ";
    }
    BucketInfoCommand::print(out, verbose, indent);
}

RemoveLocationReply::RemoveLocationReply(const RemoveLocationCommand& cmd, uint32_t docs_removed)
    : BucketInfoReply(cmd),
      _documents_removed(docs_removed)
{
}

}
