// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "policydirective.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace mbus {

PolicyDirective::PolicyDirective(const vespalib::stringref &name, const vespalib::stringref &param) :
    _name(name),
    _param(param)
{ }

PolicyDirective::~PolicyDirective() {}

string
PolicyDirective::toString() const
{
    if (_param.empty()) {
        return vespalib::make_vespa_string("[%s]", _name.c_str());
    }
    return vespalib::make_vespa_string("[%s:%s]", _name.c_str(), _param.c_str());
}

string
PolicyDirective::toDebugString() const
{
    return vespalib::make_vespa_string("PolicyDirective(name = '%s', param = '%s')",
                                 _name.c_str(), _param.c_str());
}

} // mbus
