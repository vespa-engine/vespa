// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/asciistream.h>

namespace vespalib {

template <typename T>
T lexical_cast(const stringref s)
{
    T v;
    asciistream is(s);
    is >> v;
    return v;
}

}

