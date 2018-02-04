// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexenvironment.h"

using namespace search::fef;

namespace storage {

IndexEnvironment::IndexEnvironment(const ITableManager & tableManager) :
    _tableManager(&tableManager),
    _properties(),
    _fields(),
    _fieldNames(),
    _motivation(RANK),
    _rankAttributes(),
    _dumpAttributes()
{
}

IndexEnvironment::~IndexEnvironment() = default;

bool
IndexEnvironment::addField(const vespalib::string & name, bool isAttribute)
{
    if (getFieldByName(name) != nullptr) {
        return false;
    }
    FieldInfo info(isAttribute ? FieldType::ATTRIBUTE : FieldType::INDEX,
                   FieldInfo::CollectionType::SINGLE, name, _fields.size());
    info.addAttribute(); // we are able to produce needed attributes at query time
    _fields.push_back(info);
    _fieldNames[info.name()] = info.id();
    return true;
}

void
IndexEnvironment::hintAttributeAccess(const string & name) const {
    if (name.empty()) {
        return;
    }
    if (_motivation == RANK) {
        _rankAttributes.insert(name);
    } else {
        _dumpAttributes.insert(name);
    }
}

} // namespace storage

