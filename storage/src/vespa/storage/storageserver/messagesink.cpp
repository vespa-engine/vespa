// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "messagesink.h"
#include <vespa/storageapi/message/persistence.h>
#include <ostream>

using std::shared_ptr;

namespace storage {

MessageSink::MessageSink()
    : StorageLink("Message Sink")
{
}

MessageSink::~MessageSink()
{
    closeNextLink();
}

void
MessageSink::print(std::ostream& out, bool verbose,
                   const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "MessageSink";
}

namespace {
#if 0
    std::string getTimeString() {
        char timeBuf[200];
        time_t tm;
        struct tm tms;
        time(&tm);
        gmtime_r(&tm, &tms);
        strftime(timeBuf, sizeof(timeBuf), "%Y-%m-%d:%H:%M:%S %Z", &tms);
        return std::string(timeBuf);
    }
#endif
}

IMPL_MSG_COMMAND_H(MessageSink, Get)
{
    //LOG(event, "[%s] Get %s", getTimeString().c_str(),
    //                          cmd->getDocumentId()->toString());
    shared_ptr<api::StorageReply> rmsg(new api::GetReply(*cmd));
    rmsg->setResult(api::ReturnCode::NOT_IMPLEMENTED);
    sendUp(rmsg);
    return true;
}

IMPL_MSG_COMMAND_H(MessageSink, Put)
{
    //LOG(event, "[%s] Put %s", getTimeString().c_str(),
    //                          cmd->getDocumentId()->toString());
    shared_ptr<api::StorageReply> rmsg(new api::PutReply(*cmd));
    rmsg->setResult(api::ReturnCode::OK);
    sendUp(rmsg);
    return true;
}

IMPL_MSG_COMMAND_H(MessageSink, Remove)
{
    //LOG(event, "[%s] Remove %s", getTimeString().c_str(),
    //                             cmd->getDocumentId()->toString());
    shared_ptr<api::StorageReply> rmsg(new api::RemoveReply(*cmd));
    rmsg->setResult(api::ReturnCode::OK);
    sendUp(rmsg);
    return true;
}

IMPL_MSG_COMMAND_H(MessageSink, Revert)
{
    //LOG(event, "[%s] Revert %s", getTimeString().c_str(),
    //                             cmd->getDocumentId()->toString());
    shared_ptr<api::StorageReply> rmsg(new api::RevertReply(*cmd));
    rmsg->setResult(api::ReturnCode::OK);
    sendUp(rmsg);
    return true;
}

} // storage
