// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "message_sender_stub.h"
#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/storageapi/messageapi/storagereply.h>
#include <string>
#include <sstream>
#include <stdexcept>

namespace storage {

MessageSenderStub::MessageSenderStub() = default;
MessageSenderStub::~MessageSenderStub() = default;

std::string
MessageSenderStub::getLastCommand(bool verbose) const
{
    if (commands.empty()) {
        throw std::logic_error("Expected command where there was none");
    }
    return dumpMessage(*commands[commands.size() - 1], true, verbose);
}

std::string
MessageSenderStub::dumpMessage(const api::StorageMessage& msg, bool includeAddress, bool verbose)
{
    std::ostringstream ost;

    if (verbose) {
        ost << msg;
    } else {
        ost << msg.getType().getName();
    }

    if (includeAddress && msg.getAddress()) {
        ost << " => " << msg.getAddress()->getIndex();
    }
    if (verbose && msg.getType().isReply()) {
        ost << " " << dynamic_cast<const api::StorageReply&>(msg).getResult();
    }

    return ost.str();
}

std::string
MessageSenderStub::getCommands(bool includeAddress, bool verbose, uint32_t fromIdx) const
{
    std::ostringstream ost;

    for (uint32_t i = fromIdx; i < commands.size(); i++) {
        if (i != fromIdx) {
            ost << ",";
        }

        ost << dumpMessage(*commands[i], includeAddress, verbose);
    }

    return ost.str();
}

std::string
MessageSenderStub::getLastReply(bool verbose) const
{
    if (replies.empty()) {
        throw std::logic_error("Expected reply where there was none");
    }

    return dumpMessage(*replies.back(),true, verbose);

}

std::string
MessageSenderStub::getReplies(bool includeAddress, bool verbose) const
{
    std::ostringstream ost;
    for (uint32_t i = 0; i < replies.size(); i++) {
        if (i != 0) {
            ost << ",";
        }

        ost << dumpMessage(*replies[i], includeAddress, verbose);
    }

    return ost.str();
}

}
