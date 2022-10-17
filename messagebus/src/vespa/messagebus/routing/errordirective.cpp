// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "errordirective.h"
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string;

namespace mbus {

ErrorDirective::ErrorDirective(vespalib::stringref msg) :
    _msg(msg)
{ }

ErrorDirective::~ErrorDirective() = default;

string
ErrorDirective::toString() const
{
    return make_string("(%s)", _msg.c_str());
}

string
ErrorDirective::toDebugString() const
{
    return make_string("ErrorDirective(msg = '%s')", _msg.c_str());
}

} // mbus
