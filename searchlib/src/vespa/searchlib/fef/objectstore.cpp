// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "objectstore.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace search::fef {

ObjectStore::ObjectStore() :
    _objectMap()
{
}

ObjectStore::~ObjectStore()
{
    for(auto & it : _objectMap) {
        delete it.second;
        it.second = NULL;
    }
}

void
ObjectStore::add(const vespalib::string & key, Anything::UP value)
{
    ObjectMap::iterator found = _objectMap.find(key);
    if (found != _objectMap.end()) {
        delete found->second;
        found->second = NULL;
    }
    _objectMap[key] = value.release();
}

const Anything *
ObjectStore::get(const vespalib::string & key) const
{
    ObjectMap::const_iterator found = _objectMap.find(key);
    return (found != _objectMap.end()) ? found->second : NULL;
}

}
