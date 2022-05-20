// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "object2slime.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/data/slime/cursor.h>

namespace vespalib {

Object2Slime::Object2Slime(slime::Cursor & cursor)
    : _cursor(cursor),
      _stack()
{
}

Object2Slime::~Object2Slime() = default;

//-----------------------------------------------------------------------------

void
Object2Slime::openStruct(const vespalib::string &name, const vespalib::string &type)
{
    if (name.empty()) {
        _cursor.get().setString("[type]", type);
    } else {
        _stack.push_back(_cursor);
        _cursor = _cursor.get().setObject(name);
        _cursor.get().setString("[type]", type);
    }
}

void
Object2Slime::closeStruct()
{
    if ( ! _stack.empty()) {
        _cursor = _stack.back();
        _stack.pop_back();
    }
}

void
Object2Slime::visitBool(const vespalib::string &name, bool value)
{
    _cursor.get().setBool(name, value);
}

void
Object2Slime::visitInt(const vespalib::string &name, int64_t value)
{
    _cursor.get().setLong(name, value);
}

void
Object2Slime::visitFloat(const vespalib::string &name, double value)
{
    _cursor.get().setDouble(name, value);
}

void
Object2Slime::visitString(const vespalib::string &name, const vespalib::string &value)
{
    _cursor.get().setString(name, value);
}

void
Object2Slime::visitNull(const vespalib::string &name)
{
    _cursor.get().setNix(name);
}

void
Object2Slime::visitNotImplemented()
{
    _cursor.get().setNix("not_implemented");
}

}
