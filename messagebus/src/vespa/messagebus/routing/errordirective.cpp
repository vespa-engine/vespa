// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/util/vstringfmt.h>
#include "errordirective.h"

namespace mbus {

ErrorDirective::ErrorDirective(const vespalib::stringref &msg) :
    _msg(msg)
{
    // empty
}

string
ErrorDirective::toString() const
{
    return vespalib::make_vespa_string("(%s)", _msg.c_str());
}

string
ErrorDirective::toDebugString() const
{
    return vespalib::make_vespa_string("ErrorDirective(msg = '%s')", _msg.c_str());
}

} // mbus
