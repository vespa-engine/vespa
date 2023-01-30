// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace slobrok {

struct ServiceMapping {
    vespalib::string name;
    vespalib::string spec;
    ServiceMapping(const vespalib::string & name_, const vespalib::string & spec_) noexcept : name(name_), spec(spec_) { }
    ServiceMapping(const ServiceMapping& rhs) noexcept;
    ~ServiceMapping();
    ServiceMapping& operator=(const ServiceMapping& rhs);

    bool operator== (const ServiceMapping &other) const noexcept {
        return name == other.name && spec == other.spec;
    }

    bool operator< (const ServiceMapping &other) const noexcept {
        if (name < other.name) return true;
        if (other.name < name) return false;
        return spec < other.spec;
    }
};

using ServiceMappingList = std::vector<ServiceMapping>;

}
