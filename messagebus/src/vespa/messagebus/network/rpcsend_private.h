// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/messagebus/trace.h>
#include <vespa/messagebus/routing/routingnode.h>

namespace mbus::network::internal {
/**
 * Implements a helper class to hold the necessary context to create a reply from
 * an rpc return value. This object is held as the context of an FRT_RPCRequest.
 */
class SendContext {
public:
    using UP = std::unique_ptr<SendContext>;
    SendContext(const SendContext &) = delete;
    SendContext & operator = (const SendContext &) = delete;
    SendContext(mbus::RoutingNode &recipient, duration timeRemaining)
        : _recipient(recipient),
          _trace(recipient.getTrace().getLevel()),
          _timeout(timeRemaining)
   { }
    mbus::RoutingNode &getRecipient() { return _recipient; }
    mbus::Trace &getTrace() { return _trace; }
    duration getTimeout() { return _timeout; }
private:
    mbus::RoutingNode &_recipient;
    mbus::Trace        _trace;
    duration           _timeout;
};

/**
 * Implements a helper class to hold the necessary context to send a reply as an
 * rpc return value. This object is held in the callstack of the reply.
 */
class ReplyContext {
private:
    FRT_RPCRequest   &_request;
    vespalib::Version _version;

public:
    using UP = std::unique_ptr<ReplyContext>;
    ReplyContext(const ReplyContext &) = delete;
    ReplyContext & operator = (const ReplyContext &) = delete;

    ReplyContext(FRT_RPCRequest &request, const vespalib::Version &version)
            : _request(request), _version(version) { }
    FRT_RPCRequest &getRequest() { return _request; }
    const vespalib::Version &getVersion() { return _version; }
};


}
