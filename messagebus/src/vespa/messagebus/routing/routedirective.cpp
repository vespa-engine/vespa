// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "routedirective.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace mbus {

RouteDirective::RouteDirective(const vespalib::stringref & name) :
    _name(name)
{
    // empty
}

bool
RouteDirective::matches(const IHopDirective &dir) const
{
    if (dir.getType() != TYPE_ROUTE) {
        return false;
    }
    return _name == static_cast<const RouteDirective&>(dir).getName();
}

string
RouteDirective::toString() const
{
    return vespalib::make_string("route:%s", _name.c_str());
}

string
RouteDirective::toDebugString() const
{
    return vespalib::make_string("RouteDirective(name = '%s')", _name.c_str());
}

} // mbus
