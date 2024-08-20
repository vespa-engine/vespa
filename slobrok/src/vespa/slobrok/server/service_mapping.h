// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vector>

namespace slobrok {

struct ServiceMapping {
    std::string name;
    std::string spec;
    ServiceMapping(const std::string & name_, const std::string & spec_) noexcept : name(name_), spec(spec_) { }
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
