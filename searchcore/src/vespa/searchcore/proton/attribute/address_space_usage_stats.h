// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/address_space.h>
#include <vespa/vespalib/stllike/string.h>

namespace proton {

/*
 * class representing usage of a single address space (enum store or
 * multi value) and the most largest attribute in that respect, relative
 * to the limit.
 */
class AddressSpaceUsageStats
{
    search::AddressSpace _usage;
    vespalib::string _attributeName;
    vespalib::string _subDbName;

public:
    AddressSpaceUsageStats(const search::AddressSpace &usage);
    ~AddressSpaceUsageStats();
    void merge(const search::AddressSpace &usage,
               const vespalib::string &attributeName,
               const vespalib::string &subDbName);

    const search::AddressSpace &getUsage() const { return _usage; }
    const vespalib::string &getAttributeName() const { return _attributeName; }
    const vespalib::string &getSubDbName() const { return _subDbName; }
};

} // namespace proton
