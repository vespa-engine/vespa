// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributesconfigscout.h"
#include <vespa/searchlib/attribute/configconverter.h>

using search::attribute::ConfigConverter;

namespace proton {

AttributesConfigScout::AttributesConfigScout(const AttributesConfig &live)
    : _live(live),
      _map()
{
    uint32_t i = 0;
    for (const auto &attr : live.attribute) {
        _map[attr.name] = i;
        ++i;
    }
}


void
AttributesConfigScout::adjust(AttributesConfig::Attribute &attr,
                              const AttributesConfig::Attribute &liveAttr)
{
    attr.enablebitvectors = liveAttr.enablebitvectors;
    attr.enableonlybitvector = liveAttr.enableonlybitvector;
    attr.fastsearch = liveAttr.fastsearch;
    attr.huge = liveAttr.huge;
    // Note: Predicate attributes only handle changes for the dense-posting-list-threshold config.
    attr.densepostinglistthreshold = liveAttr.densepostinglistthreshold;
}


void
AttributesConfigScout::adjust(AttributesConfig::Attribute &attr)
{
    search::attribute::Config cfg = ConfigConverter::convert(attr);
    const auto it = _map.find(attr.name);
    if (it != _map.end()) {
        const auto &liveAttr = _live.attribute[it->second];
        search::attribute::Config liveCfg =
            ConfigConverter::convert(liveAttr);
        if (cfg.basicType() == liveCfg.basicType() &&
            cfg.collectionType() == liveCfg.collectionType()) {
            adjust(attr, liveAttr);
        }
    }
}


std::shared_ptr<AttributesConfigScout::AttributesConfig>
AttributesConfigScout::adjust(const AttributesConfig &config)
{
    std::shared_ptr<AttributesConfigBuilder> result =
        std::make_shared<AttributesConfigBuilder>(config);
    for (auto &attr : result->attribute) {
        adjust(attr);
    }
    return result;
}

} // namespace proton
