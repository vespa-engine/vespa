// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stringmap.h"
#include <vespa/vespalib/stllike/hashtable.hpp>

void Fast_StringMap::Insert(const char* key, const char* value)
{
    _backing[key] = value;
}


const char *
Fast_StringMap::Lookup(const char *key, const char *defval) const
{
    Map::const_iterator found(_backing.find(key));
    return (found != _backing.end()) ? found->second.c_str() : defval;
}
