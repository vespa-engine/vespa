// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".fef.indexenvironment");

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include "indexenvironment.h"

namespace search {
namespace fef {
namespace test {

IndexEnvironment::IndexEnvironment() :
    _properties(),
    _fields(),
    _attrMan(),
    _tableMan()
{
}

const FieldInfo *
IndexEnvironment::getField(uint32_t id) const
{
    return id < _fields.size() ? &_fields[id] : NULL;
}

const FieldInfo *
IndexEnvironment::getFieldByName(const string &name) const
{
    for (std::vector<FieldInfo>::const_iterator it = _fields.begin();
         it != _fields.end(); ++it) {
        if (it->name() == name) {
            return &(*it);
        }
    }
    return NULL;
}

} // namespace test
} // namespace fef
} // namespace search
