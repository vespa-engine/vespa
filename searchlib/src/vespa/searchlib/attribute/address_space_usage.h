// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/hash_fun.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/address_space.h>
#include <unordered_map>

namespace search {

/**
 * Represents the address space usage for a set of attribute vector components.
 */
class AddressSpaceUsage
{
private:
    using AddressSpaceMap = std::unordered_map<vespalib::string, vespalib::AddressSpace, vespalib::hash<vespalib::string>>;
    AddressSpaceMap _map;

public:
    AddressSpaceUsage();
    void set(const vespalib::string& component, const vespalib::AddressSpace& usage);
    vespalib::AddressSpace get(const vespalib::string& component) const;
    const AddressSpaceMap& get_all() const { return _map; }
    vespalib::AddressSpace enum_store_usage() const;
    vespalib::AddressSpace multi_value_usage() const;
};

}
