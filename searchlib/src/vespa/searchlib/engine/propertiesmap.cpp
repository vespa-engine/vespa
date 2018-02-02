// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "propertiesmap.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace search::engine {

fef::Properties PropertiesMap::_emptyProperties;

PropertiesMap::PropertiesMap()
    : _propertiesMap()
{ }

PropertiesMap::~PropertiesMap() { }

fef::Properties &
PropertiesMap::lookupCreate(const vespalib::stringref &name)
{
    return _propertiesMap[name];
}

const fef::Properties &
PropertiesMap::lookup(const vespalib::stringref &name) const
{
    PropsMap::const_iterator pos = _propertiesMap.find(name);
    if (pos == _propertiesMap.end()) {
        return _emptyProperties;
    }
    return pos->second;
}

}

VESPALIB_HASH_MAP_INSTANTIATE(vespalib::string, search::fef::Properties);

