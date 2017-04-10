// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/messagebus/policies/asyncinitializationpolicy.h>
#include <vespa/config-slobroks.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/slobrok/imirrorapi.h>
#include <vespa/documentapi/common.h>
#include <vespa/config/subscription/sourcespec.h>

class FRT_Supervisor;

namespace documentapi {

/**
   Super class for routing policies that allow the user to specify external slobrok lists,
   either by supplying external config servers or the slobrok list directly.
*/
class ExternSlobrokPolicy : public AsyncInitializationPolicy
{
protected:
    bool   _firstTry;
    config::ServerSpec::HostSpecList          _configSources;
    vespalib::Lock                            _lock;
    std::unique_ptr<FRT_Supervisor>           _orb;
    std::unique_ptr<slobrok::api::IMirrorAPI> _mirror;
    std::vector<std::string>                  _slobroks;
    string                                    _slobrokConfigId;

public:
    ExternSlobrokPolicy(const std::map<string, string>& params);
    virtual ~ExternSlobrokPolicy();

    /**
     * @return a pointer to the slobrok mirror owned by this policy, if any.
     * If the policy uses the default mirror API, NULL is returned.
     */
    const slobrok::api::IMirrorAPI* getMirror() const { return _mirror.get(); }

    slobrok::api::IMirrorAPI::SpecList lookup(mbus::RoutingContext &context, const string& pattern);

    /**
     * Initializes the policy
     */
    virtual string init() override;
};

}

