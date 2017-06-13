// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "removelocation.h"
#include <ostream>

namespace storage {
namespace api {

IMPLEMENT_COMMAND(RemoveLocationCommand, RemoveLocationReply)
IMPLEMENT_REPLY(RemoveLocationReply)

RemoveLocationCommand::RemoveLocationCommand(const vespalib::stringref & documentSelection,
                                             const document::BucketId& id)
    : BucketInfoCommand(MessageType::REMOVELOCATION, id),
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

} // api
} // storage
