// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/select/node.h>
#include <map>
#include <vespa/messagebus/routing/iroutingpolicy.h>
#include <vespa/messagebus/routing/route.h>
#include <vespa/messagebus/routing/routingcontext.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/util/ptrholder.h>
#include <vespa/config-messagetyperouteselectorpolicy.h>
#include <vespa/config/config.h>
#include <vespa/config/helper/configfetcher.h>
#include <vespa/documentapi/common.h>

namespace documentapi {

/**
 * This policy is responsible for selecting among the given recipient routes
 * according to the configured document selection properties. To factilitate
 * this the "routing" plugin in the vespa model builds a mapping from the route
 * names to a document selector and a feed name of every search cluster. This
 * can very well be extended to include storage at a later time.
 */
class MessageTypePolicy : public mbus::IRoutingPolicy,
                          public config::IFetcherCallback<vespa::config::content::MessagetyperouteselectorpolicyConfig>
{
private:
    typedef vespalib::hash_map<int, mbus::Route> MessageTypeMap;
    typedef vespalib::PtrHolder<MessageTypeMap> MessageTypeHolder;
    typedef vespalib::PtrHolder<mbus::Route> RouteHolder;

    MessageTypeHolder     _map;
    RouteHolder           _defaultRoute;
    config::ConfigFetcher _fetcher;

public:
    /**
     * This policy is constructed with a configuration uri that can be used to
     * subscribe for the document selector config. If the uri is empty, it will
     * default to a proper one.
     *
     * @param configUri The configuration uri to subscribe with.
     */
    MessageTypePolicy(const config::ConfigUri & configUri);
    ~MessageTypePolicy();
    void configure(std::unique_ptr<vespa::config::content::MessagetyperouteselectorpolicyConfig> cfg) override;
    void select(mbus::RoutingContext &context) override;
    void merge(mbus::RoutingContext &context) override;
};

}
