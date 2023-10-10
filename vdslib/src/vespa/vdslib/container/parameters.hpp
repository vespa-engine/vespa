// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "parameters.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace vdslib {

template<typename T>
void
Parameters::set(KeyT id, T t) {
    vespalib::asciistream ost;
    ost << t;
    _parameters[id] = ost.str();
}

template<typename T>
T
Parameters::get(KeyT id, T def) const {
    vespalib::stringref ref;
    if (!lookup(id, ref)) return def;
    vespalib::asciistream ist(ref);
    T t;
    ist >> t;
    return t;
}

}
