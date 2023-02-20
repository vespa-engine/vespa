// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "sendproxy.h"

#include <vespa/log/log.h>
LOG_SETUP(".sendproxy");

namespace mbus {

SendProxy::SendProxy(MessageBus &mbus, INetwork &net, Resender *resender) :
    _mbus(mbus),
    _net(net),
    _resender(resender),
    _msg(),
    _logTrace(false),
    _root()
{
    // empty
}

void
SendProxy::handleMessage(Message::UP msg)
{
    Trace &trace = msg->getTrace();
    if (trace.getLevel() == 0) {
        if (LOG_WOULD_LOG(spam)) {
            trace.setLevel(9);
            _logTrace = true;
        } else if (LOG_WOULD_LOG(debug)) {
            trace.setLevel(6);
            _logTrace = true;
        }
    }
    _msg = std::move(msg);
    _root.reset(new RoutingNode(_mbus, _net, _resender, *this, *_msg, this));
    _root->send();
}

void
SendProxy::handleDiscard(Context ctx)
{
    (void)ctx;
    _msg->discard();
    delete this;
}

void
SendProxy::handleReply(Reply::UP reply)
{
    Trace &trace = _msg->getTrace();
    if (_logTrace) {
        if (reply->hasErrors()) {
            LOG(debug, "Trace for reply with error(s):\n%s", reply->getTrace().toString().c_str());
        } else if (LOG_WOULD_LOG(spam)) {
            LOG(spam, "Trace for reply:\n%s", reply->getTrace().toString().c_str());
        }
        trace.clear();
    } else if (trace.getLevel() > 0) {
        trace.addChild(reply->steal_trace());
        trace.normalize();
    }
    reply->swapState(*_msg);
    reply->setMessage(std::move(_msg));

    IReplyHandler &handler = reply->getCallStack().pop(*reply);
    handler.handleReply(std::move(reply));

    delete this;
}

} // namespace mbus
