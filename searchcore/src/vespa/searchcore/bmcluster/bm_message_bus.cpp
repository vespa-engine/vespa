// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_message_bus.h"
#include "pending_tracker_hash.h"
#include "pending_tracker.h"
#include "storage_reply_error_checker.h"
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/network/rpcnetworkparams.h>
#include <vespa/messagebus/rpcmessagebus.h>
#include <vespa/messagebus/ireplyhandler.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/documentapi/messagebus/messages/documentmessage.h>
#include <vespa/storageapi/mbusprot/storagereply.h>
#include <vespa/vespalib/stllike/asciistream.h>

#include <vespa/log/log.h>
LOG_SETUP(".bm_message_bus");

using documentapi::DocumentProtocol;
using mbus::RPCMessageBus;
using mbus::Reply;
using mbus::SourceSession;
using storage::mbusprot::StorageReply;

namespace search::bmcluster {

namespace {

std::atomic<uint64_t> bm_message_bus_msg_id(0u);

vespalib::string reply_as_string(Reply &reply) {
    vespalib::asciistream os;
    if (reply.getType() == 0) {
        os << "empty reply";
    } else {
        os << "reply=" << reply.toString() << ", protocol=" << reply.getProtocol();
    }
    os << ", ";
    auto message = reply.getMessage();
    if (message) {
        os << "message=" << message->toString();
        os << ", protocol=" << message->getProtocol();
    } else {
        os << "no message";
    }
    reply.setMessage(std::move(message));
    os << ", ";
    if (reply.hasErrors()) {
        os << "errors=[";
        for (uint32_t i = 0; i < reply.getNumErrors(); ++i) {
            auto &error = reply.getError(i);
            if (i > 0) {
                os << ", ";
            }
            os << mbus::ErrorCode::getName(error.getCode()) << ": " << error.getMessage() << " (from " << error.getService() << ")";
        }
        os << "]";
    } else {
        os << "no errors";
    }
    return os.str();
}

}

class BmMessageBus::ReplyHandler : public mbus::IReplyHandler,
                                   public StorageReplyErrorChecker
{
    PendingTrackerHash _pending_hash;
public:
    ReplyHandler();
    ~ReplyHandler() override;
    void handleReply(std::unique_ptr<Reply> reply) override;
    void retain(uint64_t msg_id, PendingTracker &tracker) { _pending_hash.retain(msg_id, tracker); }
    void message_aborted(uint64_t msg_id);
};

BmMessageBus::ReplyHandler::ReplyHandler()
    : mbus::IReplyHandler(),
      StorageReplyErrorChecker(),
      _pending_hash()
{
}

BmMessageBus::ReplyHandler::~ReplyHandler() = default;

void
BmMessageBus::ReplyHandler::handleReply(std::unique_ptr<Reply> reply)
{
    auto msg_id = reply->getContext().value.UINT64;
    auto tracker = _pending_hash.release(msg_id);
    if (tracker != nullptr) {
        bool failed = false;
        if (reply->getType() == 0 || reply->hasErrors()) {
            failed = true; // empty reply or error
        } else {
            auto protocol = reply->getProtocol();
            if (protocol != DocumentProtocol::NAME) {
                failed = true; // unexpected protocol
            }
        }
        if (failed) {
            ++_errors;
            if (_errors <= 10) {
                LOG(error, "Unexpected %s", reply_as_string(*reply).c_str());
            }
        }
        tracker->release();
    } else {
        ++_errors;
        LOG(error, "Untracked %s", reply_as_string(*reply).c_str());
    }
}

void
BmMessageBus::ReplyHandler::message_aborted(uint64_t msg_id)
{
    ++_errors;
    auto tracker = _pending_hash.release(msg_id);
    tracker->release();
}

BmMessageBus::BmMessageBus(const config::ConfigUri& config_uri,
                           std::shared_ptr<const document::DocumentTypeRepo> document_type_repo)
    : _reply_handler(std::make_unique<ReplyHandler>()),
      _message_bus(),
      _session()
{
    mbus::RPCNetworkParams params(config_uri);
    mbus::ProtocolSet protocol_set;
    protocol_set.add(std::make_shared<DocumentProtocol>(document_type_repo));
    params.setIdentity(mbus::Identity("vespa-bm-client"));
    _message_bus = std::make_unique<mbus::RPCMessageBus>(
            protocol_set,
            params,
            config_uri);
    mbus::SourceSessionParams srcParams;
    srcParams.setThrottlePolicy(mbus::IThrottlePolicy::SP());
    srcParams.setReplyHandler(*_reply_handler);
    _session = _message_bus->getMessageBus().createSourceSession(srcParams);
}

BmMessageBus::~BmMessageBus()
{
    _session.reset();
    _message_bus.reset();
    _reply_handler.reset();
}

uint32_t
BmMessageBus::get_error_count() const
{
    return _reply_handler->get_error_count();
}

void
BmMessageBus::send_msg(std::unique_ptr<mbus::Message> msg, const mbus::Route &route, PendingTracker &tracker)
{
    auto msg_id = ++bm_message_bus_msg_id;
    _reply_handler->retain(msg_id, tracker);
    msg->setContext(mbus::Context(msg_id));
    msg->setRetryEnabled(false);
    auto result = _session->send(std::move(msg), route);
    if (!result.isAccepted()) {
        LOG(error, "Message not accepeted, error is '%s'", result.getError().toString().c_str());
        _reply_handler->message_aborted(msg_id);
    }
}

}
