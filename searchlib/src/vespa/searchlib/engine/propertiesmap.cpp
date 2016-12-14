// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "propertiesmap.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace search {
namespace engine {

search::fef::Properties PropertiesMap::_emptyProperties;

PropertiesMap::PropertiesMap()
    : _propertiesMap()
{ }

PropertiesMap::~PropertiesMap() { }

search::fef::Properties &
PropertiesMap::lookupCreate(const vespalib::stringref &name)
{
    return _propertiesMap[name];
}

const search::fef::Properties &
PropertiesMap::lookup(const vespalib::stringref &name) const
{
    PropsMap::const_iterator pos = _propertiesMap.find(name);
    if (pos == _propertiesMap.end()) {
        return _emptyProperties;
    }
    return pos->second;
}

} // namespace engine
} // namespace search

VESPALIB_HASH_MAP_INSTANTIATE(vespalib::string, search::fef::Properties);