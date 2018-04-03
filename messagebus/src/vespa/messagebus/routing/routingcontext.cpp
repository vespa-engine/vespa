// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "route.h"
#include "routingnode.h"
#include "policydirective.h"
#include <vespa/messagebus/network/inetwork.h>

namespace mbus {

RoutingContext::RoutingContext(RoutingNode &node, uint32_t directive) :
    _node(node),
    _directive(directive),
    _consumableErrors(),
    _selectOnRetry(true),
    _context()
{ }

bool
RoutingContext::hasRecipients() const
{
    return !_node.getRecipients().empty();
}

uint32_t
RoutingContext::getNumRecipients() const
{
    return _node.getRecipients().size();
}

const Route &
RoutingContext::getRecipient(uint32_t idx) const
{
    return _node.getRecipients()[idx];
}

const std::vector<Route> &
RoutingContext::getAllRecipients() const
{
    return _node.getRecipients();
}

void
RoutingContext::getMatchedRecipients(std::vector<Route> &ret) const
{
    std::set<string> done;
    const std::vector<Route> &recipients = _node.getRecipients();
    const Hop &hop = getHop();
    for (std::vector<Route>::const_iterator it = recipients.begin();
         it != recipients.end(); ++it)
    {
        if (it->hasHops() && hop.matches(it->getHop(0))) {
            IHopDirective::SP dir = it->getHop(0).getDirective(_directive);
            string key = dir->toString();
            if (done.find(key) == done.end()) {
                Route add = *it;
                add.setHop(0, hop);
                add.getHop(0).setDirective(_directive, std::move(dir));
                ret.push_back(std::move(add));
                done.insert(key);
            }
        }
    }
}

bool
RoutingContext::getSelectOnRetry() const
{
    return _selectOnRetry;
}

RoutingContext &
RoutingContext::setSelectOnRetry(bool selectOnRetry)
{
    _selectOnRetry = selectOnRetry;
    return *this;
}

const Route &
RoutingContext::getRoute() const
{
    return _node.getRoute();
}

const Hop &
RoutingContext::getHop() const
{
    return _node.getRoute().getHop(0);
}

uint32_t
RoutingContext::getDirectiveIndex() const
{
    return _directive;
}

const PolicyDirective &
RoutingContext::getDirective() const
{
    return static_cast<const PolicyDirective&>(*getHop().getDirective(_directive));
}

string
RoutingContext::getHopPrefix() const
{
    return getHop().getPrefix(_directive);
}

string
RoutingContext::getHopSuffix() const
{
    return getHop().getSuffix(_directive);
}

Context &
RoutingContext::getContext()
{
    return _context;
}

const Context &
RoutingContext::getContext() const
{
    return _context;
}

RoutingContext &
RoutingContext::setContext(const Context &ctx)
{
    _context = ctx;
    return *this;
}

const Message &
RoutingContext::getMessage() const
{
    return _node.getMessage();
}

void
RoutingContext::trace(uint32_t level, const string &note)
{
    _node.getTrace().trace(level, note);
}

bool
RoutingContext::hasReply() const
{
    return _node.hasReply();
}

const Reply &
RoutingContext::getReply() const
{
    return _node.getReplyRef();
}

RoutingContext &
RoutingContext::setReply(Reply::UP reply)
{
    _node.setReply(std::move(reply));
    return *this;
}

RoutingContext &
RoutingContext::setError(uint32_t code, const string &msg)
{
    _node.setError(code, msg);
    return *this;
}

RoutingContext &
RoutingContext::setError(const Error &err)
{
    _node.setError(err);
    return *this;
}

MessageBus &
RoutingContext::getMessageBus()
{
    return _node.getMessageBus();
}

bool
RoutingContext::hasChildren() const
{
    return !_node.getChildren().empty();
}

uint32_t
RoutingContext::getNumChildren() const
{
    return _node.getChildren().size();
}

RoutingNodeIterator
RoutingContext::getChildIterator()
{
    return RoutingNodeIterator(_node.getChildren());
}

void
RoutingContext::addChild(Route route)
{
    _node.addChild(std::move(route));
}

void
RoutingContext::addChildren(std::vector<Route> routes)
{
    for (auto & route : routes) {
        addChild(std::move(route));
    }
}

const slobrok::api::IMirrorAPI &
RoutingContext::getMirror() const
{
    return _node.getNetwork().getMirror();
}

void
RoutingContext::addConsumableError(uint32_t errorCode)
{
    _consumableErrors.insert(errorCode);
}

bool
RoutingContext::isConsumableError(uint32_t errorCode)
{
    return _consumableErrors.find(errorCode) != _consumableErrors.end();
}

} // namespace mbus
