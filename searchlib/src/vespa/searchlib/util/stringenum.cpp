// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stringenum.h"
#include <vespa/vespalib/stllike/hashtable.hpp>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".seachlib.util.stringenum");

namespace search::util {

StringEnum::StringEnum()
    : _numEntries(0),
      _mapping(),
      _reverseMap()
{
}

StringEnum::~StringEnum() = default;

void
StringEnum::CreateReverseMapping() const
{
    _reverseMap.resize(_numEntries);

    for (Map::const_iterator it = _mapping.begin();
         it != _mapping.end();
         it++)
    {
        assert(it->second >= 0);
        assert(it->second < (int)_numEntries);
        _reverseMap[it->second] = it->first.c_str();
    }
}

void
StringEnum::Clear()
{
    _reverseMap.clear();
    _mapping.clear();
    _numEntries = 0;
}

int
StringEnum::Add(const char *str)
{
    Map::const_iterator found(_mapping.find(str));
    if (found != _mapping.end()) {
        return found->second;
    } else {
        int value = _numEntries++;
        _mapping[str] = value;
        return value;
    }
}

int
StringEnum::Lookup(const char *str) const
{
    Map::const_iterator found(_mapping.find(str));
    return (found != _mapping.end()) ? found->second : -1;
}

const char *
StringEnum::Lookup(uint32_t value) const
{
    if (value >= _numEntries)
        return nullptr;

    if (_numEntries > _reverseMap.size())
        CreateReverseMapping();

    return _reverseMap[value];
}

}
