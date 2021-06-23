// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace slobrok {

struct ServiceMapping {
    vespalib::string name;
    vespalib::string spec;
    ~ServiceMapping();
};

typedef std::vector<ServiceMapping> ServiceMappingList;

}
