// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config-attributes.h>

namespace proton {

/**
 * Class to create adjusted attributes config that minimizes the number of
 * proton restarts needed due to config changes.  Grab the portions from
 * live (supposedly future) config that is safe to apply early during
 * initialization and replay.
 */
class AttributesConfigScout
{
public:
    using AttributesConfig = vespa::config::search::AttributesConfig;
    using AttributesConfigBuilder =
        vespa::config::search::AttributesConfigBuilder;

private:
    const AttributesConfig &_live;
    std::map<vespalib::string, uint32_t> _map;
    
    static void
    adjust(AttributesConfig::Attribute &attr,
           const AttributesConfig::Attribute &liveAttr);

    void
    adjust(AttributesConfig::Attribute &attr);

public:
    explicit AttributesConfigScout(const AttributesConfig &live);

    std::shared_ptr<AttributesConfig>
    adjust(const AttributesConfig &config);
};

}
