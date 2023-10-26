// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

struct Method {
    const char *shortName;
    const char *rpcMethod;
    const int args;
};

namespace methods {

const Method find(const vespalib::string &name);
void dump();

};

