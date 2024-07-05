// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "propertiesmap.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace search::engine {

fef::Properties PropertiesMap::_emptyProperties;

PropertiesMap::PropertiesMap()
    : _propertiesMap()
{ }

PropertiesMap::PropertiesMap(uint32_t sz)
    : _propertiesMap(sz)
{ }

PropertiesMap::~PropertiesMap() = default;

fef::Properties &
PropertiesMap::lookupCreate(std::string_view name)
{
    return _propertiesMap[name];
}

const fef::Properties &
PropertiesMap::lookup(std::string_view name) const
{
    auto pos = _propertiesMap.find(name);
    if (pos == _propertiesMap.end()) {
        return _emptyProperties;
    }
    return pos->second;
}

}

VESPALIB_HASH_MAP_INSTANTIATE(vespalib::string, search::fef::Properties);

