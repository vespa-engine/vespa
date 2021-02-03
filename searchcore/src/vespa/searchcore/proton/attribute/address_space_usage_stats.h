// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/address_space.h>
#include <vespa/vespalib/stllike/string.h>

namespace proton {

/**
 * Class representing usage of a single address space (enum store or
 * multi value) and the most largest attribute in that respect, relative
 * to the limit.
 */
class AddressSpaceUsageStats
{
    vespalib::AddressSpace _usage;
    vespalib::string _attributeName;
    vespalib::string _subDbName;

public:
    explicit AddressSpaceUsageStats(const vespalib::AddressSpace &usage);
    ~AddressSpaceUsageStats();
    void merge(const vespalib::AddressSpace &usage,
               const vespalib::string &attributeName,
               const vespalib::string &subDbName);

    const vespalib::AddressSpace &getUsage() const { return _usage; }
    const vespalib::string &getAttributeName() const { return _attributeName; }
    const vespalib::string &getSubDbName() const { return _subDbName; }

    bool operator==(const AddressSpaceUsageStats& rhs) const {
        return (_usage == rhs._usage) &&
                (_attributeName == rhs._attributeName) &&
                (_subDbName == rhs._subDbName);
    }
};

std::ostream& operator<<(std::ostream &out, const AddressSpaceUsageStats& rhs);

} // namespace proton
