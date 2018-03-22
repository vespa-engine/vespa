// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "routingnode.h"
#include "errordirective.h"
#include "routedirective.h"
#include "routingtable.h"
#include "policydirective.h"
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/tracelevel.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/messagebus/network/inetwork.h>
#include <stack>

using vespalib::make_string;

namespace mbus {

RoutingNode::RoutingNode(MessageBus &mbus, INetwork &net, Resender *resender,
                         IReplyHandler &replyHandler, Message &msg,
                         IDiscardHandler *discardHandler)
    : _mbus(mbus),
      _net(net),
      _resender(resender),
      _parent(nullptr),
      _recipients(),
      _children(),
      _replyHandler(&replyHandler),
      _discardHandler(discardHandler),
      _trace(msg.getTrace().getLevel()),
      _pending(0),
      _msg(msg),
      _reply(),
      _route(msg.getRoute()),
      _policy(),
      _routingContext(),
      _serviceAddress(),
      _isActive(true),
      _shouldRetry(false)
{ }

RoutingNode::RoutingNode(RoutingNode &parent, Route route)
    : _mbus(parent._mbus),
      _net(parent._net),
      _resender(parent._resender),
      _parent(&parent),
      _recipients(parent._recipients),
      _children(),
      _replyHandler(nullptr),
      _discardHandler(nullptr),
      _trace(parent._trace.getLevel()),
      _pending(0),
      _msg(parent._msg),
      _reply(),
      _route(std::move(route)),
      _policy(),
      _routingContext(),
      _serviceAddress(),
      _isActive(true),
      _shouldRetry(false)
{ }

RoutingNode::~RoutingNode()
{
    clearChildren();
}

void
RoutingNode::clearChildren()
{
    for (std::vector<RoutingNode*>::iterator it = _children.begin();
         it != _children.end(); ++it)
    {
        delete *it;
    }
    _children.clear();
}

void
RoutingNode::discard()
{
    assert(_parent == nullptr);
    if (_discardHandler != nullptr) {
        _discardHandler->handleDiscard(Context());
    }
}

void
RoutingNode::send()
{
    if (!resolve(0)) {
        notifyAbort("Route resolution failed.");
    } else if (hasUnconsumedErrors()) {
        notifyAbort("Errors found while resolving route.");
    } else {
        notifyTransmit();
    }
}

void
RoutingNode::prepareForRetry()
{
    _shouldRetry = false;
    _reply.reset();
    if (_routingContext.get() != nullptr && _routingContext->getSelectOnRetry()) {
        clearChildren();
    } else if (!_children.empty()) {
        bool retryingSome = false;
        for (std::vector<RoutingNode*>::iterator it = _children.begin();
             it != _children.end(); ++it)
        {
            RoutingNode *child= *it;
            if (child->_shouldRetry || child->_reply.get() == nullptr) {
                child->prepareForRetry();
                retryingSome = true;
            }
        }
        if (!retryingSome) {
            // Entering here means we have no children that should be resent even
            // though this node reports a transient error. The only thing we can 
            // do is to reselect from this.
            clearChildren();
        }
    }
}

void
RoutingNode::notifyParent()
{
    if (_serviceAddress) {
        _net.freeServiceAddress(*this);
    }
    tryIgnoreResult();
    if (_parent != nullptr) {
        _parent->notifyMerge();
        return;
    }
    if (_shouldRetry && _resender->scheduleRetry(*this)) {
        return;
    }
    notifySender();
}

void
RoutingNode::addChild(Route route)
{
    RoutingNode *child = new RoutingNode(*this, std::move(route));
    if (shouldIgnoreResult()) {
        child->_route.getHop(0).setIgnoreResult(true);
    }
    _children.push_back(child);
}

void
RoutingNode::setError(uint32_t code, const string &msg)
{
    setError(Error(code, msg));
}

void
RoutingNode::setError(const Error &err)
{
    Reply::UP reply(new EmptyReply());
    reply->getTrace().setLevel(_trace.getLevel());
    reply->addError(err);
    setReply(std::move(reply));
}

void
RoutingNode::addError(uint32_t code, const string &msg)
{
    addError(Error(code, msg));
}

void
RoutingNode::addError(const Error &err)
{
    if (_reply.get() != nullptr) {
        _reply->getTrace().swap(_trace);
        _reply->addError(err);
        _reply->getTrace().swap(_trace);
    } else {
        setError(err);
    }
}

void
RoutingNode::setReply(Reply::UP reply)
{
    if (reply.get() != nullptr) {
        _shouldRetry = _resender != nullptr && _resender->shouldRetry(*reply);
        _trace.getRoot().addChild(reply->getTrace().getRoot());
        reply->getTrace().clear();
    }
    _reply = std::move(reply);
}

void
RoutingNode::handleReply(Reply::UP reply)
{
    setReply(std::move(reply));
    notifyParent();
}

void
RoutingNode::notifyAbort(const string &msg)
{
    std::stack<RoutingNode*> mystack;
    mystack.push(this);
    while (!mystack.empty()) {
        RoutingNode *node = mystack.top();
        mystack.pop();
        if (!node->_isActive) {
            // reply not pending
        } else if (node->_reply.get() != nullptr) {
            node->notifyParent();
        } else if (node->_children.empty()) {
            node->setError(ErrorCode::SEND_ABORTED, msg);
            node->notifyParent();
        } else {
            for (std::vector<RoutingNode*>::iterator it = node->_children.begin();
                 it != node->_children.end(); ++it)
            {
                mystack.push(*it);
            }
        }
    }
}

void
RoutingNode::notifyTransmit()
{
    std::vector<RoutingNode*> sendTo;
    std::stack<RoutingNode*> mystack;
    mystack.push(this);
    while (!mystack.empty()) {
        RoutingNode *node = mystack.top();
        mystack.pop();
        if (node->_isActive) {
            if (node->_children.empty()) {
                if (node->hasReply()) {
                    node->notifyParent();
                } else {
                    assert(node->_serviceAddress.get() != nullptr);
                    sendTo.push_back(node);
                }
            } else {
                for (std::vector<RoutingNode*>::iterator it = node->_children.begin();
                     it != node->_children.end(); ++it)
                {
                    mystack.push(*it);
                }
            }
        }
    }
    if (!sendTo.empty()) {
        _net.send(_msg, sendTo);
    }
}

void
RoutingNode::notifySender()
{
    _reply->getTrace().swap(_trace);
    _replyHandler->handleReply(std::move(_reply));
}

void
RoutingNode::notifyMerge()
{
    if (_pending.fetch_sub(1) > 1) {
        return;
    }

    // Merges the trace information from all children into this. This method takes care not to spend cycles
    // manipulating the trace in case tracing is disabled.
    if (_trace.getLevel() > 0) {
        TraceNode tail;
        for (std::vector<RoutingNode*>::iterator it = _children.begin();
             it != _children.end(); ++it)
        {
            TraceNode &root = (*it)->_trace.getRoot();
            tail.addChild(root);
            root.clear();
        }
        tail.setStrict(false);
        _trace.getRoot().addChild(tail);
    }

    // Execute the {@link RoutingPolicy#merge(RoutingContext)} method of the current routing policy. If a
    // policy fails to produce a reply, this attaches an error reply to this node.
    const PolicyDirective &dir = _routingContext->getDirective();
    _trace.trace(TraceLevel::SPLIT_MERGE, make_string("Routing policy '%s' merging replies.", dir.getName().c_str()));
    try {
        _policy->merge(*_routingContext);
    } catch (const std::exception &e) {
        setError(ErrorCode::POLICY_ERROR, make_string("Policy '%s' threw an exception; %s",
                                                      dir.getName().c_str(), e.what()));
    }
    if (_reply.get() == nullptr) {
        setError(ErrorCode::APP_FATAL_ERROR, make_string("Routing policy '%s' failed to merge replies.",
                                                         dir.getName().c_str()));
    }
    
    // Notifies the parent node.
    notifyParent();
}

bool
RoutingNode::hasUnconsumedErrors()
{
    bool hasError = false;

    std::stack<RoutingNode*> mystack;
    mystack.push(this);
    while (!mystack.empty()) {
        RoutingNode *node = mystack.top();
        mystack.pop();
        if (node->_reply.get() != nullptr) {
            for (uint32_t i = 0; i < node->_reply->getNumErrors(); ++i) {
                int errorCode = node->_reply->getError(i).getCode();
                RoutingNode *it = node;
                while (it != nullptr) {
                    if (it->_routingContext.get() != nullptr &&
                        it->_routingContext->isConsumableError(errorCode))
                    {
                        errorCode = ErrorCode::NONE;
                        break;
                    }
                    it = it->_parent;
                }
                if (errorCode != ErrorCode::NONE) {
                    _shouldRetry = _resender != nullptr && _resender->canRetry(errorCode);
                    if (!_shouldRetry) {
                        return true; // no need to continue
                    }
                    hasError = true;
                }
            }
        } else {
            for (std::vector<RoutingNode*>::iterator it = node->_children.begin();
                 it != node->_children.end(); ++it)
            {
                mystack.push(*it);
            }
        }
    }

    return hasError;
}

bool
RoutingNode::resolve(uint32_t depth)
{
    if (!_route.hasHops()) {
        setError(ErrorCode::ILLEGAL_ROUTE, "Route has no hops.");
        return false;
    }
    if (!_children.empty()) {
        return resolveChildren(depth + 1);
    }
    while (lookupHop() || lookupRoute()) {
        if (++depth > 64) {
            break;
        }
    }
    if (depth > 64) {
        setError(ErrorCode::ILLEGAL_ROUTE, "Depth limit exceeded.");
        return false;
    }
    if (findErrorDirective()) {
        return false;
    }
    if (findPolicyDirective()) {
        if (executePolicySelect()) {
            return resolveChildren(depth + 1);
        }
        return _reply.get() != nullptr;
    }
    _net.allocServiceAddress(*this);
    return _serviceAddress.get() != nullptr || _reply.get() != nullptr;
}

bool
RoutingNode::lookupHop()
{
    RoutingTable::SP table = _mbus.getRoutingTable(_msg.getProtocol());
    if (table.get() != nullptr) {
        string name = _route.getHop(0).getServiceName();
        if (table->hasHop(name)) {
            const HopBlueprint *hop = table->getHop(name);
            configureFromBlueprint(*hop);
            _trace.trace(TraceLevel::SPLIT_MERGE,
                         make_string("Recognized '%s' as %s.", name.c_str(), hop->toString().c_str()));
            return true;
        }
    }
    return false;
}

bool
RoutingNode::lookupRoute()
{
    RoutingTable::SP table = _mbus.getRoutingTable(_msg.getProtocol());
    Hop &hop = _route.getHop(0);
    if (hop.getDirective(0)->getType() == IHopDirective::TYPE_ROUTE) {
        RouteDirective &dir = static_cast<RouteDirective&>(*hop.getDirective(0));
        if (!table || !table->hasRoute(dir.getName())) {
            setError(ErrorCode::ILLEGAL_ROUTE, make_string("Route '%s' does not exist.", dir.getName().c_str()));
            return false;
        }
        insertRoute(*table->getRoute(dir.getName()));
        _trace.trace(TraceLevel::SPLIT_MERGE,
                     make_string("Route '%s' retrieved by directive; new route is '%s'.",
                                 dir.getName().c_str(), _route.toString().c_str()));
        return true;
    }
    if (table) {
        string name = hop.getServiceName();
        if (table->hasRoute(name)) {
            insertRoute(*table->getRoute(name));
            _trace.trace(TraceLevel::SPLIT_MERGE,
                         make_string("Recognized '%s' as route '%s'.", name.c_str(), _route.toString().c_str()));
            return true;
        }
    }
    return false;
}

void
RoutingNode::insertRoute(Route route)
{
    if (shouldIgnoreResult()) {
        route.getHop(0).setIgnoreResult(true);        
    }
    for (uint32_t i = 1; i < _route.getNumHops(); ++i) {
        route.addHop(std::move(_route.getHop(i)));
    }
    _route = std::move(route);
}

bool
RoutingNode::findErrorDirective()
{
    Hop &hop = _route.getHop(0);
    for (uint32_t i = 0; i < hop.getNumDirectives(); ++i) {
        IHopDirective::SP dir = hop.getDirective(i);
        if (dir->getType() == IHopDirective::TYPE_ERROR) {
            setError(ErrorCode::ILLEGAL_ROUTE,
                     static_cast<ErrorDirective&>(*dir).getMessage());
            return true;
        }
    }
    return false;
}

bool
RoutingNode::findPolicyDirective()
{
    Hop &hop = _route.getHop(0);
    for (uint32_t i = 0; i < hop.getNumDirectives(); ++i) {
        IHopDirective::SP dir = hop.getDirective(i);
        if (dir->getType() == IHopDirective::TYPE_POLICY) {
            _routingContext.reset(new RoutingContext(*this, i));
            return true;
        }
    }
    return false;
}

bool
RoutingNode::executePolicySelect()
{
    const PolicyDirective &dir = _routingContext->getDirective();
    _policy = _mbus.getRoutingPolicy(_msg.getProtocol(), dir.getName(), dir.getParam());
    if (_policy.get() == nullptr) {
        setError(ErrorCode::UNKNOWN_POLICY, make_string(
                "Protocol '%s' could not create routing policy '%s' with parameter '%s'.",
                _msg.getProtocol().c_str(), dir.getName().c_str(), dir.getParam().c_str()));
        return false;
    }
    _trace.trace(TraceLevel::SPLIT_MERGE, make_string("Running routing policy '%s'.", dir.getName().c_str()));
    try {
        _policy->select(*_routingContext);
    } catch (const std::exception &e) {
        setError(ErrorCode::POLICY_ERROR, make_string("Policy '%s' threw an exception; %s",
                                                      dir.getName().c_str(), e.what()));
        return false;
    }
    if (_children.empty()) {
        if (_reply.get() == nullptr) {
            setError(ErrorCode::NO_SERVICES_FOR_ROUTE,
                     make_string("Policy '%s' selected no recipients for route '%s'.",
                                 dir.getName().c_str(), _route.toString().c_str()));
        } else {
            _trace.trace(TraceLevel::SPLIT_MERGE,
                         make_string("Policy '%s' assigned a reply to this branch.", dir.getName().c_str()));
        }
        return false;
    }
    for (std::vector<RoutingNode*>::iterator it = _children.begin();
         it != _children.end(); ++it)
    {
        RoutingNode *child = *it;
        Hop &hop = child->_route.getHop(0);
        child->_trace.trace(TraceLevel::SPLIT_MERGE,
                            make_string("Component '%s' selected by policy '%s'.",
                                        hop.toString().c_str(), dir.getName().c_str()));
    }
    return true;
}

bool
RoutingNode::resolveChildren(uint32_t childDepth)
{
    int numActiveChildren = 0;
    bool ret = true;
    for (std::vector<RoutingNode*>::iterator it = _children.begin();
         it != _children.end(); ++it)
    {
        RoutingNode *child = *it;
        child->_trace.trace(TraceLevel::SPLIT_MERGE,
                            make_string("Resolving '%s'.", child->_route.toString().c_str()));
        child->_isActive = (child->_reply.get() == nullptr);
        if (child->_isActive) {
            ++numActiveChildren;
            if (!child->resolve(childDepth)) {
                ret = false;
                break;
            }
        } else {
            child->_trace.trace(TraceLevel::SPLIT_MERGE, "Already completed.");
        }
    }
    _pending = numActiveChildren;
    return ret;
}

void
RoutingNode::configureFromBlueprint(const HopBlueprint &hop)
{
    bool ignoreResult = shouldIgnoreResult();
    _route.setHop(0, *hop.create());
    if (ignoreResult) {
        _route.getHop(0).setIgnoreResult(true);
    }
    _recipients.clear();
    for (uint32_t r = 0; r < hop.getNumRecipients(); ++r) {
        Route recipient;
        recipient.addHop(hop.getRecipient(r));
        for (uint32_t h = 1; h < _route.getNumHops(); ++h) {
            recipient.addHop(_route.getHop(h));
        }
        _recipients.push_back(recipient);
    }
}

bool
RoutingNode::tryIgnoreResult() 
{
    if (!shouldIgnoreResult()) {
        return false;
    }
    if (_reply.get() == nullptr || !_reply->hasErrors()) {
        return false;
    }
    setReply(Reply::UP(new EmptyReply()));
    _trace.trace(TraceLevel::SPLIT_MERGE, "Ignoring errors in reply.");
    return true;
}

bool 
RoutingNode::shouldIgnoreResult() 
{
    return _route.getNumHops() > 0 && _route.getHop(0).getIgnoreResult();
}

} // namespace mbus
