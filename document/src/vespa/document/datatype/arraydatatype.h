// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::ArrayDataType
 * \ingroup datatype
 *
 * \brief A datatype specifying what can be contained in an array field value.
 */
#pragma once

#include <vespa/document/datatype/collectiondatatype.h>

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

        // CollectionDataType implementation
    virtual std::unique_ptr<FieldValue> createFieldValue() const;
    virtual void print(std::ostream&, bool verbose,
                       const std::string& indent) const;
    virtual bool operator==(const DataType& other) const;
    virtual ArrayDataType* clone() const { return new ArrayDataType(*this); }

    FieldPath::UP onBuildFieldPath(
            const vespalib::stringref & remainFieldName) const;

    DECLARE_IDENTIFIABLE(ArrayDataType);
};

} // document

