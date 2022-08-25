// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resultclass.h"
#include "resultconfig.h"
#include <vespa/vespalib/stllike/hashtable.hpp>
#include <cassert>

namespace search::docsummary {

ResultClass::ResultClass(const char *name, uint32_t id, util::StringEnum & fieldEnum)
    : _name(name),
      _classID(id),
      _entries(),
      _nameMap(),
      _fieldEnum(fieldEnum),
      _enumMap(),
      _dynInfo(NULL),
      _omit_summary_features(false)
{ }


ResultClass::~ResultClass() = default;

int
ResultClass::GetIndexFromName(const char* name) const
{
    NameIdMap::const_iterator found(_nameMap.find(name));
    return (found != _nameMap.end()) ? found->second : -1;
}

bool
ResultClass::AddConfigEntry(const char *name, ResType type)
{
    if (_nameMap.find(name) != _nameMap.end())
        return false;

    _nameMap[name] = _entries.size();
    ResConfigEntry e;
    e._type      = type;
    e._bindname  = name;
    e._enumValue = _fieldEnum.Add(name);
    assert(e._enumValue >= 0);
    _entries.push_back(e);
    return true;
}

void
ResultClass::CreateEnumMap()
{
    _enumMap.resize(_fieldEnum.GetNumEntries());

    for (uint32_t i(0), m(_enumMap.size()); i < m; i++) {
        _enumMap[i] = -1;
    }
    for (uint32_t i(0); i < _entries.size(); i++) {
        _enumMap[_entries[i]._enumValue] = i;
    }
}

}
