// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/address_space.h>

namespace search {

/**
 * Represents the address space usage for enum store and multi value mapping.
 */
class AddressSpaceUsage
{
private:
    AddressSpace _enumStoreUsage;
    AddressSpace _multiValueUsage;

public:
    AddressSpaceUsage();
    AddressSpaceUsage(const AddressSpace &enumStoreUsage_,
                      const AddressSpace &multiValueUsage_);
    static AddressSpace defaultEnumStoreUsage();
    static AddressSpace defaultMultiValueUsage();
    const AddressSpace &enumStoreUsage() const { return _enumStoreUsage; }
    const AddressSpace &multiValueUsage() const { return _multiValueUsage; }

};

} // namespace search
