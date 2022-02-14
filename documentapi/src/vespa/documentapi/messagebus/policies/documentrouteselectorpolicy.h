// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/messagebus/policies/config-documentrouteselectorpolicy.h>
#include <vespa/document/select/node.h>
#include <map>
#include <vespa/messagebus/routing/iroutingpolicy.h>
#include <vespa/documentapi/common.h>
#include <vespa/config/helper/ifetchercallback.h>
#include <mutex>

namespace document { class DocumentTypeRepo; }

namespace mbus {
    class Route;
    class RoutingContext;
}

namespace config {
    class ConfigUri;
    class ConfigFetcher;
}

namespace documentapi {

/**
 * This policy is responsible for selecting among the given recipient routes according to the configured document
 * selection properties. To factilitate this the "routing" plugin in the vespa model builds a mapping from the route
 * names to a document selector and a feed name of every search cluster. This can very well be extended to include
 * storage at a later time.
 */
class DocumentRouteSelectorPolicy : public mbus::IRoutingPolicy,
                                    public config::IFetcherCallback<messagebus::protocol::DocumentrouteselectorpolicyConfig>
{
private:
    typedef std::shared_ptr<document::select::Node> SelectorPtr;
    typedef std::map<string, SelectorPtr> ConfigMap;

    const document::DocumentTypeRepo      &_repo;
    mutable std::mutex                     _lock;
    ConfigMap                              _config;
    string                                 _error;
    std::unique_ptr<config::ConfigFetcher> _fetcher;

    /**
     * This method runs the selector associated with the given location on the content of the message. If the selector
     * validates the location, this method returns true.
     *
     * @param context   The routing context that contains the necessary data.
     * @param routeName The candidate route whose selector to run.
     * @return Whether or not to send to the given recipient.
     */
    bool select(mbus::RoutingContext &context, const vespalib::string &routeName);

public:
    /**
     * This policy is constructed with a configuration uri that is used to subscribe for the document selector
     * config. If the string is either null or empty it will default to the proper one.
     *
     * @param configUri The configuration uri to subscribe with.
     */
    DocumentRouteSelectorPolicy(const document::DocumentTypeRepo &repo,
                                const config::ConfigUri &configUri);
    ~DocumentRouteSelectorPolicy() override;

    /**
     * This is a safety mechanism to allow the constructor to fail and signal that it can not be used.
     *
     * @return The error string, or null if no error.
     */
    const string &getError() const;
    void configure(std::unique_ptr<messagebus::protocol::DocumentrouteselectorpolicyConfig> cfg) override;
    void select(mbus::RoutingContext &context) override;
    void merge(mbus::RoutingContext &context) override;
};

}

