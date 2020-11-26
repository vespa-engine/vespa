// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "roundrobinpolicy.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/error.h>

namespace documentapi {

RoundRobinPolicy::CacheEntry::CacheEntry() :
    _offset(0),
    _generation(),
    _recipients()
{}

RoundRobinPolicy::RoundRobinPolicy(const string &) :
    _lock(),
    _cache()
{}

RoundRobinPolicy::~RoundRobinPolicy() = default;

void
RoundRobinPolicy::select(mbus::RoutingContext &ctx)
{
    mbus::Hop hop = getRecipient(ctx);
    if (hop.hasDirectives()) {
        mbus::Route route = ctx.getRoute();
        route.setHop(0, hop);
        ctx.addChild(route);
    } else {
        mbus::EmptyReply::UP reply(new mbus::EmptyReply());
        reply->addError(mbus::Error(mbus::ErrorCode::NO_ADDRESS_FOR_SERVICE,
                                    "None of the configured recipients are currently available."));
        ctx.setReply(std::move(reply));
    }
}

void
RoundRobinPolicy::merge(mbus::RoutingContext &context)
{
    DocumentProtocol::merge(context);
}

string
RoundRobinPolicy::getCacheKey(const mbus::RoutingContext &ctx) const
{
    string ret;
    for (uint32_t i = 0; i < ctx.getNumRecipients(); ++i) {
        ret.append(ctx.getRecipient(i).getHop(0).toString());
        ret.append(" ");
    }
    return ret;
}

mbus::Hop
RoundRobinPolicy::getRecipient(mbus::RoutingContext &ctx)
{
    std::lock_guard guard(_lock);
    CacheEntry &entry = update(ctx);
    if (entry._recipients.empty()) {
        return mbus::Hop();
    }
    if (++entry._offset >= entry._recipients.size()) {
        entry._offset = 0;
    }
    return entry._recipients[entry._offset];
}

RoundRobinPolicy::CacheEntry &
RoundRobinPolicy::update(mbus::RoutingContext &ctx)
{
    uint32_t upd = ctx.getMirror().updates();
    CacheEntry &entry = _cache.insert(std::map<string, CacheEntry>::value_type(getCacheKey(ctx), CacheEntry())).first->second;
    if (entry._generation != upd) {
        entry._generation = upd;
        entry._recipients.clear();
        for (uint32_t i = 0; i < ctx.getNumRecipients(); ++i)
        {
            slobrok::api::IMirrorAPI::SpecList entries = ctx.getMirror().lookup(ctx.getRecipient(i).getHop(0).toString());
            for (slobrok::api::IMirrorAPI::SpecList::iterator it = entries.begin();
                 it != entries.end(); ++it)
            {
                entry._recipients.push_back(mbus::Hop::parse(it->first));
            }
        }
    }
    return entry;
}

}
