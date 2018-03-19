// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "removelocation.h"
#include <ostream>

namespace storage::api {

IMPLEMENT_COMMAND(RemoveLocationCommand, RemoveLocationReply)
IMPLEMENT_REPLY(RemoveLocationReply)

RemoveLocationCommand::RemoveLocationCommand(const vespalib::stringref & documentSelection,
                                             const document::Bucket &bucket)
    : BucketInfoCommand(MessageType::REMOVELOCATION, bucket),
      _documentSelection(documentSelection)
{}

RemoveLocationCommand::~RemoveLocationCommand() {}

void
RemoveLocationCommand::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    if (_documentSelection.length()) {
        out << "Remove selection(" << _documentSelection << "): ";
    }
    BucketInfoCommand::print(out, verbose, indent);
}

RemoveLocationReply::RemoveLocationReply(const RemoveLocationCommand& cmd)
    : BucketInfoReply(cmd)
{
}

}
