// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "routedirective.h"
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string;

namespace mbus {

RouteDirective::RouteDirective(vespalib::stringref name) :
    _name(name)
{
    // empty
}

RouteDirective::~RouteDirective() = default;

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
    return make_string("route:%s", _name.c_str());
}

string
RouteDirective::toDebugString() const
{
    return make_string("RouteDirective(name = '%s')", _name.c_str());
}

} // mbus
