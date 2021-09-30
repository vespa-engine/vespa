// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "internal.h"
#include <ostream>


namespace storage::api {

InternalCommand::InternalCommand(uint32_t type)
    : StorageCommand(MessageType::INTERNAL),
      _type(type)
{ }

InternalCommand::~InternalCommand() = default;

InternalReply::InternalReply(uint32_t type, const InternalCommand& cmd)
    : StorageReply(cmd),
      _type(type)
{ }

InternalReply::~InternalReply() = default;

void
InternalCommand::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "InternalCommand(" << _type << ")";
    if (verbose) {
        out << " : ";
        StorageCommand::print(out, verbose, indent);
    }
}

void
InternalReply::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "InternalReply(" << _type << ")";
    if (verbose) {
        out << " : ";
        StorageReply::print(out, verbose, indent);
    }
}


}

