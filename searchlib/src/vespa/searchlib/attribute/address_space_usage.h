// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/address_space.h>

namespace search {

/**
 * Represents the address space usage for enum store and multi value mapping.
 */
class AddressSpaceUsage
{
private:
    vespalib::AddressSpace _enumStoreUsage;
    vespalib::AddressSpace _multiValueUsage;

public:
    AddressSpaceUsage();
    AddressSpaceUsage(const vespalib::AddressSpace &enumStoreUsage_,
                      const vespalib::AddressSpace &multiValueUsage_);
    static vespalib::AddressSpace defaultEnumStoreUsage();
    static vespalib::AddressSpace defaultMultiValueUsage();
    const vespalib::AddressSpace &enumStoreUsage() const { return _enumStoreUsage; }
    const vespalib::AddressSpace &multiValueUsage() const { return _multiValueUsage; }

};

} // namespace search
