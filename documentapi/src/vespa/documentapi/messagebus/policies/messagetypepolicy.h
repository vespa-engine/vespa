// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/messagebus/routing/iroutingpolicy.h>
#include <vespa/vespalib/util/ptrholder.h>
#include <vespa/config-messagetyperouteselectorpolicy.h>
#include <vespa/config/helper/ifetchercallback.h>
#include <vespa/documentapi/common.h>

namespace config {
    class ConfigUri;
    class ConfigFetcher;
}
namespace mbus {
    class RoutingContext;
    class Route;
}
namespace documentapi {

namespace policy { class MessageTypeMap; }
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
    typedef vespalib::PtrHolder<policy::MessageTypeMap> MessageTypeHolder;
    typedef vespalib::PtrHolder<mbus::Route> RouteHolder;

    MessageTypeHolder     _map;
    RouteHolder           _defaultRoute;
    std::unique_ptr<config::ConfigFetcher> _fetcher;

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
