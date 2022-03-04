// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "collectiondatatype.h"

namespace document {

CollectionDataType::CollectionDataType(vespalib::stringref name,
                                       const DataType& nestedType) noexcept
    : DataType(name),
      _nestedType(&nestedType)
{ }

CollectionDataType::CollectionDataType(vespalib::stringref name,
                                       const DataType& nestedType,
                                       int32_t id) noexcept
    : DataType(name, id),
      _nestedType(&nestedType)
{ }

CollectionDataType::~CollectionDataType() = default;

bool
CollectionDataType::equals(const DataType& other) const noexcept
{
    if (!DataType::equals(other)) return false;
    const CollectionDataType * o = other.cast_collection();
    return o && _nestedType->equals(*o->_nestedType);
}

} // document
