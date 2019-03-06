// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "object2slime.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/data/slime/cursor.h>

namespace vespalib {

Object2Slime::Object2Slime(slime::Cursor & cursor)
    : _cursor(&cursor),
      _stack()
{
}

Object2Slime::~Object2Slime() = default;

//-----------------------------------------------------------------------------

void
Object2Slime::openStruct(const vespalib::string &name, const vespalib::string &type)
{
    _stack.push_back(_cursor);

    if (name.empty()) {
        _cursor = & _cursor->setObject(type);
    } else {
        _cursor = & _cursor->setObject(name);
        _cursor->setString("[type]", type);
    }
}

void
Object2Slime::closeStruct()
{
   _cursor = _stack.back();
   _stack.pop_back();
}

void
Object2Slime::visitBool(const vespalib::string &name, bool value)
{
    _cursor->setBool(name, value);
}

void
Object2Slime::visitInt(const vespalib::string &name, int64_t value)
{
    _cursor->setLong(name, value);
}

void
Object2Slime::visitFloat(const vespalib::string &name, double value)
{
    _cursor->setDouble(name, value);
}

void
Object2Slime::visitString(const vespalib::string &name, const vespalib::string &value)
{
    _cursor->setString(name, value);
}

void
Object2Slime::visitNull(const vespalib::string &name)
{
    _cursor->setNix(name);
}

void
Object2Slime::visitNotImplemented()
{
    _cursor->setNix("not_implemented");
}

} // namespace vespalib
