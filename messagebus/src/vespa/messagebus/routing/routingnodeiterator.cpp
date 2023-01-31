// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "routingnode.h"

namespace mbus {

RoutingNodeIterator::RoutingNodeIterator(std::vector<RoutingNode*> &children) :
    _pos(children.begin()),
    _end(children.end())
{ }

RoutingNodeIterator &
RoutingNodeIterator::next()
{
    ++_pos;
    return *this;
}

RoutingNodeIterator &
RoutingNodeIterator::skip(uint32_t num)
{
    for (uint32_t i = 0; i < num && isValid(); ++i) {
        next();
    }
    return *this;
}

const Route &
RoutingNodeIterator::getRoute() const
{
    return (*_pos)->getRoute();
}

Reply::UP
RoutingNodeIterator::removeReply()
{
    RoutingNode *node = *_pos;
    Reply::UP ret = node->getReply();
    ret->getTrace().setLevel(node->getTrace().getLevel());
    ret->getTrace().swap(node->getTrace());
    return ret;
}

const Reply &
RoutingNodeIterator::getReplyRef() const
{
    return (*_pos)->getReplyRef();
}

} // mbus
