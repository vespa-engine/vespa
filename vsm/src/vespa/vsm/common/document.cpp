// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "document.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

using search::DocumentIdT;
using search::TimeT;
using document::FieldValue;

namespace vsm
{

vespalib::asciistream & operator << (vespalib::asciistream & os, const FieldRef & f)
{
  const char *s = f.data();
  os << f.size();
  if (s) {
      os << s; // Better hope it's null terminated!
  }
  os << " : ";
  return os;
}

vespalib::asciistream & operator << (vespalib::asciistream & os, const StringFieldIdTMap & f)
{
    for (StringFieldIdTMapT::const_iterator it=f._map.begin(), mt=f._map.end(); it != mt; it++) {
        os << it->first << " = " << it->second << '\n';
    }
    return os;
}

StringFieldIdTMap::StringFieldIdTMap() :
    _map()
{
}

void StringFieldIdTMap::add(const vespalib::string & s, FieldIdT fieldId)
{
    _map[s] = fieldId;
}

void StringFieldIdTMap::add(const vespalib::string & s)
{
    if (_map.find(s) == _map.end()) {
        FieldIdT fieldId = _map.size();
        _map[s] = fieldId;
    }
}

FieldIdT StringFieldIdTMap::fieldNo(const vespalib::string & fName) const
{
    StringFieldIdTMapT::const_iterator found = _map.find(fName);
    FieldIdT fNo((found != _map.end()) ? found->second : npos);
    return fNo;
}

size_t StringFieldIdTMap::highestFieldNo() const
{
  size_t maxFNo(0);
  for (const auto & field : _map) {
    if (field.second >= maxFNo) {
      maxFNo = field.second + 1;
    }
  }
  return maxFNo;
}

Document::~Document() { }

}

VESPALIB_HASH_MAP_INSTANTIATE(vespalib::string, vsm::FieldIdTList);
VESPALIB_HASH_MAP_INSTANTIATE(vespalib::string, vsm::IndexFieldMapT);
