// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "policydirective.h"
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string;

namespace mbus {

PolicyDirective::PolicyDirective(vespalib::stringref name, vespalib::stringref param) :
    _name(name),
    _param(param)
{ }

PolicyDirective::~PolicyDirective() {}

string
PolicyDirective::toString() const
{
    if (_param.empty()) {
        return make_string("[%s]", _name.c_str());
    }
    return make_string("[%s:%s]", _name.c_str(), _param.c_str());
}

string
PolicyDirective::toDebugString() const
{
    return make_string("PolicyDirective(name = '%s', param = '%s')", _name.c_str(), _param.c_str());
}

} // mbus
