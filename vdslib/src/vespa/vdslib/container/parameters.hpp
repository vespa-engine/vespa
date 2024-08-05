// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "parameters.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace vdslib {

template<typename T>
T
Parameters::get(KeyT id, T def) const {
    std::string_view ref;
    if (!lookup(id, ref)) return def;
    vespalib::asciistream ist(ref);
    T t;
    ist >> t;
    return t;
}

}
