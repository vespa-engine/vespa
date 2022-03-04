// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::ArrayDataType
 * \ingroup datatype
 *
 * \brief A datatype specifying what can be contained in an array field value.
 */
#pragma once

#include "collectiondatatype.h"

namespace document {

class ArrayDataType final : public CollectionDataType {
public:
    explicit ArrayDataType(const DataType &nestedType);
    ArrayDataType(const ArrayDataType &o) = delete;
    ArrayDataType &operator=(const ArrayDataType &rhs) = delete;

    ArrayDataType(const DataType &nestedType, int32_t id);

    std::unique_ptr<FieldValue> createFieldValue() const override;
    void print(std::ostream&, bool verbose, const std::string& indent) const override;
    bool equals(const DataType& other) const noexcept override;
    void onBuildFieldPath(FieldPath & path, vespalib::stringref remainFieldName) const override;

    bool isArray() const noexcept override { return true; }
};

} // document

