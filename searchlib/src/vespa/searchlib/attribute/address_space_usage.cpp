// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "address_space_usage.h"
#include "enumstorebase.h"

namespace search {

AddressSpaceUsage::AddressSpaceUsage()
        : _enumStoreUsage(defaultEnumStoreUsage()),
          _multiValueUsage(defaultMultiValueUsage()) {
}

AddressSpaceUsage::AddressSpaceUsage(const AddressSpace &enumStoreUsage_,
                                     const AddressSpace &multiValueUsage_)
        : _enumStoreUsage(enumStoreUsage_),
          _multiValueUsage(multiValueUsage_) {
}

AddressSpace
AddressSpaceUsage::defaultEnumStoreUsage()
{
    return AddressSpace(0, 0, EnumStoreBase::DataStoreType::RefType::offsetSize());
}

AddressSpace
AddressSpaceUsage::defaultMultiValueUsage()
{
    return AddressSpace(0, 0, (1ull << 32));
}

} // namespace search
