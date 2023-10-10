// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/address_space.h>
#include <vespa/vespalib/stllike/string.h>

namespace proton {

/**
 * Class representing max address space usage (relative to the limit)
 * among components in attributes vectors in all sub databases.
 */
class AddressSpaceUsageStats
{
    vespalib::AddressSpace _usage;
    vespalib::string _attributeName;
    vespalib::string _component_name;
    vespalib::string _subDbName;

public:
    explicit AddressSpaceUsageStats(const vespalib::AddressSpace &usage);
    ~AddressSpaceUsageStats();
    void merge(const vespalib::AddressSpace &usage,
               const vespalib::string &attributeName,
               const vespalib::string &component_name,
               const vespalib::string &subDbName);

    const vespalib::AddressSpace &getUsage() const { return _usage; }
    const vespalib::string &getAttributeName() const { return _attributeName; }
    const vespalib::string &get_component_name() const { return _component_name; }
    const vespalib::string &getSubDbName() const { return _subDbName; }

    bool operator==(const AddressSpaceUsageStats& rhs) const {
        return (_usage == rhs._usage) &&
                (_attributeName == rhs._attributeName) &&
                (_component_name == rhs._component_name) &&
                (_subDbName == rhs._subDbName);
    }
};

std::ostream& operator<<(std::ostream &out, const AddressSpaceUsageStats& rhs);

} // namespace proton
