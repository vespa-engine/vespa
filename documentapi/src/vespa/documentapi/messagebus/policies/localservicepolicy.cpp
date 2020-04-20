// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "localservicepolicy.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/messagebus/routing/verbatimdirective.h>
#include <vespa/messagebus/messagebus.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/log/log.h>
LOG_SETUP(".localservicepolicy");

namespace documentapi {

LocalServicePolicy::CacheEntry::CacheEntry() :
    _offset(0),
    _generation(0),
    _recipients()
{
}

LocalServicePolicy::LocalServicePolicy(const string &param) :
    _lock(),
    _address(param),
    _cache()
{
}

LocalServicePolicy::~LocalServicePolicy() = default;

void
LocalServicePolicy::select(mbus::RoutingContext &ctx)
{
    mbus::Route route = ctx.getRoute();
    route.setHop(0, getRecipient(ctx));
    ctx.addChild(route);
}

void
LocalServicePolicy::merge(mbus::RoutingContext &context)
{
    DocumentProtocol::merge(context);
}

string
LocalServicePolicy::getCacheKey(const mbus::RoutingContext &ctx) const
{
    return ctx.getRoute().getHop(0).toString();
}

mbus::Hop
LocalServicePolicy::getRecipient(mbus::RoutingContext &ctx)
{
    vespalib::LockGuard guard(_lock);
    CacheEntry &entry = update(ctx);
    if (entry._recipients.empty()) {
        mbus::Hop hop = ctx.getRoute().getHop(0);
        hop.setDirective(ctx.getDirectiveIndex(), std::make_shared<mbus::VerbatimDirective>("*"));
        return hop;
    }
    if (++entry._offset >= entry._recipients.size()) {
        entry._offset = 0;
    }
    return entry._recipients[entry._offset];
}

LocalServicePolicy::CacheEntry &
LocalServicePolicy::update(mbus::RoutingContext &ctx)
{
    uint32_t upd = ctx.getMirror().updates();
    CacheEntry &entry = _cache.insert(std::map<string, CacheEntry>::value_type(getCacheKey(ctx), CacheEntry())).first->second;
    if (entry._generation != upd) {
        entry._generation = upd;
        entry._recipients.clear();

        string pattern = vespalib::make_string("%s*%s",
                                                    ctx.getHopPrefix().c_str(),
                                                    ctx.getHopSuffix().c_str());
        slobrok::api::IMirrorAPI::SpecList entries = ctx.getMirror().lookup(pattern);

        string self = _address.empty() ? toAddress(ctx.getMessageBus().getConnectionSpec()) : _address;
        for (slobrok::api::IMirrorAPI::SpecList::iterator it = entries.begin();
             it != entries.end(); ++it)
        {
            LOG(debug, "Matching self '%s' to '%s'.", self.c_str(), it->second.c_str());
            if (self == toAddress(it->second)) {
                LOG(debug, "Match, add it");
                entry._recipients.push_back(mbus::Hop::parse(it->first));
            }
        }
    }
    return entry;
}

string
LocalServicePolicy::toAddress(const string &connection)
{
    if (connection.substr(0, 4) == "tcp/") {
        uint32_t pos = connection.find_first_of(':', 4);
        if (pos > 4) {
            return connection.substr(4, pos - 4);
        }
    }
    return "";
}

}
