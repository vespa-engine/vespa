// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "errordirective.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace mbus {

ErrorDirective::ErrorDirective(const vespalib::stringref &msg) :
    _msg(msg)
{ }

string
ErrorDirective::toString() const
{
    return vespalib::make_string("(%s)", _msg.c_str());
}

string
ErrorDirective::toDebugString() const
{
    return vespalib::make_string("ErrorDirective(msg = '%s')", _msg.c_str());
}

} // mbus
