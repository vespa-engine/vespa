// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".engine.propertiesmap");
#include "propertiesmap.h"

namespace search {
namespace engine {

search::fef::Properties PropertiesMap::_emptyProperties;

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
