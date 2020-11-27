// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace config      { class ConfigUri; }
namespace document    { class DocumentTypeRepo; }

namespace mbus {

class Message;
class RPCMessageBus;
class Route;
class SourceSession;

}

namespace feedbm {

class PendingTracker;

/*
 * Message bus for feed benchmark program.
 */
class BmMessageBus
{
    class ReplyHandler;
    std::unique_ptr<ReplyHandler>        _reply_handler;
    std::unique_ptr<mbus::RPCMessageBus> _message_bus;
    std::unique_ptr<mbus::SourceSession> _session;
public:
    BmMessageBus(const config::ConfigUri& config_uri,
                 std::shared_ptr<const document::DocumentTypeRepo> document_type_repo);
    ~BmMessageBus();
    uint32_t get_error_count() const;
    void send_msg(std::unique_ptr<mbus::Message> msg, const mbus::Route &route, PendingTracker &tracker);
};

}
