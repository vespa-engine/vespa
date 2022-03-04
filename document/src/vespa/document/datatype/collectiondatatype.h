// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::CollectionDataType
 * \ingroup datatype
 *
 * \brief Data type used for collections of data with similar types.
 *
 * This contains common functionality for array and weighted set datatypes.
 */
#pragma once

#include "datatype.h"

namespace document {

class CollectionDataType : public DataType {
    const DataType *_nestedType;

protected:
    CollectionDataType(vespalib::stringref name, const DataType &nestedType) noexcept;
    CollectionDataType(vespalib::stringref name, const DataType &nestedType, int32_t id) noexcept;

public:
    CollectionDataType(const CollectionDataType&) = delete;
    CollectionDataType& operator=(const CollectionDataType&) = delete;
    ~CollectionDataType() override;

    bool equals(const DataType&) const noexcept override;
    const DataType &getNestedType() const noexcept { return *_nestedType; }
    const CollectionDataType * cast_collection() const noexcept override { return this; }
};

} // document


