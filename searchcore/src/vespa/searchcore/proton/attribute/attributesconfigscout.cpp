// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributesconfigscout.h"
#include "attribute_type_matcher.h"
#include <vespa/searchlib/attribute/configconverter.h>
#include <vespa/searchcommon/attribute/config.h>

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
    attr.enableonlybitvector = liveAttr.enableonlybitvector;
    attr.fastsearch = liveAttr.fastsearch;
    attr.paged = liveAttr.paged;
    // Note: Predicate attributes only handle changes for the dense-posting-list-threshold config.
    attr.densepostinglistthreshold = liveAttr.densepostinglistthreshold;
    attr.distancemetric = liveAttr.distancemetric;
    attr.index = liveAttr.index;
}


void
AttributesConfigScout::adjust(AttributesConfig::Attribute &attr)
{
    search::attribute::Config cfg = ConfigConverter::convert(attr);
    const auto it = _map.find(attr.name);
    if (it != _map.end()) {
        const auto &liveAttr = _live.attribute[it->second];
        search::attribute::Config liveCfg = ConfigConverter::convert(liveAttr);
        AttributeTypeMatcher matching_types;
        if (matching_types(cfg, liveCfg)) {
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
