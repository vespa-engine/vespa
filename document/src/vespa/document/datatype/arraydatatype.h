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

class ArrayDataType : public CollectionDataType {
protected:
    // Protected to help you avoid calling the copy constructor when
    // you think you're calling the regular constructor with a nested
    // ArrayDataType.
    ArrayDataType(const ArrayDataType &o) : CollectionDataType(o) {}

public:
    ArrayDataType() {}
    explicit ArrayDataType(const DataType &nestedType);
    ArrayDataType(const DataType &nestedType, int32_t id);

    std::unique_ptr<FieldValue> createFieldValue() const override;
    void print(std::ostream&, bool verbose, const std::string& indent) const override;
    bool operator==(const DataType& other) const override;
    ArrayDataType* clone() const override { return new ArrayDataType(*this); }
    ArrayDataType &operator=(const ArrayDataType &rhs) = default;
    void onBuildFieldPath(FieldPath & path, vespalib::stringref remainFieldName) const override;

    DECLARE_IDENTIFIABLE(ArrayDataType);
};

} // document

