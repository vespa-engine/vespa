// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resultclass.h"
#include "resultconfig.h"
#include <vespa/vespalib/stllike/hashtable.hpp>
#include <cassert>
#include <zlib.h>

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


bool
ResEntry::_extract_field(search::RawBuf *target) const
{
    bool rc = true;
    target->reset();

    if (ResultConfig::IsVariableSize(_type)) {
        if (_is_compressed()) { // COMPRESSED

            uint32_t len = _get_length();
            uint32_t realLen = 0;

            if (len >= sizeof(uint32_t))
                realLen = _get_real_length();
            else
                rc = false;

            if (realLen > 0) {
                uLongf rlen = realLen;
                char *fillPos = target->GetWritableFillPos(realLen + 1 < 32000 ?
                                                           32000 : realLen + 1);
                if ((uncompress((Bytef *)fillPos, &rlen,
                                (const Bytef *)(_get_compressed()),
                                len - sizeof(realLen)) == Z_OK) &&
                    rlen == realLen) {
                    fillPos[realLen] = '\0';
                    target->Fill(realLen);
                } else {
                    rc = false;
                }
            }
        } else {                     // UNCOMPRESSED
            uint32_t len = _len;
            if (len + 1 < 32000)
                target->preAlloc(32000);
            else
                target->preAlloc(len + 1);
            char *fillPos = target->GetWritableFillPos(len + 1 < 32000 ?
                                                       32000 : len + 1);
            memcpy(fillPos, _pt, len);
            fillPos[len] = '\0';
            target->Fill(len);
        }
    }
    return rc;
}

}
