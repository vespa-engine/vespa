// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "messagetypepolicy.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

using vespa::config::content::MessagetyperouteselectorpolicyConfig;

namespace documentapi {

MessageTypePolicy::MessageTypePolicy(const config::ConfigUri & configUri) :
    mbus::IRoutingPolicy(),
    config::IFetcherCallback<MessagetyperouteselectorpolicyConfig>(),
    _map(),
    _defaultRoute(),
    _fetcher(configUri.getContext())
{
    _fetcher.subscribe<MessagetyperouteselectorpolicyConfig>(configUri.getConfigId(), this);
    _fetcher.start();
}

void
MessageTypePolicy::configure(std::unique_ptr<MessagetyperouteselectorpolicyConfig> cfg)
{
    std::unique_ptr<MessageTypeMap> map(new MessageTypeMap);
    for (size_t i(0), m(cfg->route.size()); i < m; i++) {
        const MessagetyperouteselectorpolicyConfig::Route & r = cfg->route[i];
        (*map)[r.messagetype] = mbus::Route::parse(r.name);
    }
    _map.set(map.release());
    _defaultRoute.set(new mbus::Route(mbus::Route::parse(cfg->defaultroute)));
    _map.latch();
    _defaultRoute.latch();
}

void
MessageTypePolicy::select(mbus::RoutingContext & context)
{
    int messageType = context.getMessage().getType();
    std::shared_ptr<MessageTypeMap> map = _map.get();
    MessageTypeMap::const_iterator found = map->find(messageType);
    if (found != map->end()) {
         context.addChild(found->second);
    } else {
        context.addChild(*_defaultRoute.get());
    }
}

void
MessageTypePolicy::merge(mbus::RoutingContext &context)
{
    DocumentProtocol::merge(context);
}
}  // namespace documentapi
