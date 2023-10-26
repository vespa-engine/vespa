// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "subsetservicepolicy.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/messagebus/routing/verbatimdirective.h>
#include <vespa/messagebus/messagebus.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/stllike/hash_fun.h>
#include <vespa/log/log.h>
LOG_SETUP(".subsetservicepolicy");

namespace documentapi {

SubsetServicePolicy::CacheEntry::CacheEntry() :
    _offset(0),
    _generation(0),
    _recipients()
{
    // empty
}

SubsetServicePolicy::SubsetServicePolicy(const string &param) :
    _subsetSize(5),
    _cache()
{
    if (param.length() > 0) {
        int size = atoi(param.c_str());
        if (size >= 0) {
            _subsetSize = (uint32_t)size;
        } else {
            LOG(warning,
                "Ignoring a request to set the subset size to %d because it makes no sense. "
                "This routing policy will choose any one matching service.", size);
        }
    } else {
        LOG(warning, "No parameter given to SubsetService policy, using default value %d.", _subsetSize);
    }
}

SubsetServicePolicy::~SubsetServicePolicy() = default;

void
SubsetServicePolicy::select(mbus::RoutingContext &context)
{
    mbus::Route route = context.getRoute();
    route.setHop(0, getRecipient(context));
    context.addChild(route);
}

void
SubsetServicePolicy::merge(mbus::RoutingContext &context)
{
    DocumentProtocol::merge(context);
}

string
SubsetServicePolicy::getCacheKey(const mbus::RoutingContext &ctx) const
{
    return ctx.getRoute().getHop(0).toString();
}

mbus::Hop
SubsetServicePolicy::getRecipient(mbus::RoutingContext &ctx)
{
    mbus::Hop hop;
    if (_subsetSize > 0) {
        std::lock_guard guard(_lock);
        CacheEntry &entry = update(ctx);
        if (!entry._recipients.empty()) {
            if (++entry._offset >= entry._recipients.size()) {
                entry._offset = 0;
            }
            hop = entry._recipients[entry._offset];
        }
    }
    if (!hop.hasDirectives()) {
        hop = ctx.getRoute().getHop(0);
        hop.setDirective(ctx.getDirectiveIndex(),std::make_shared<mbus::VerbatimDirective>("*"));
    }
    return hop;
}

SubsetServicePolicy::CacheEntry &
SubsetServicePolicy::update(mbus::RoutingContext &ctx)
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
        uint32_t pos = vespalib::hashValue(ctx.getMessageBus().getConnectionSpec().c_str());
        for (uint32_t i = 0; i < _subsetSize && i < entries.size(); ++i) {
            entry._recipients.push_back(mbus::Hop::parse(entries[(pos + i) % entries.size()].first));
        }
    }
    return entry;
}

}

