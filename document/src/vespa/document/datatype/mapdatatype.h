// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::MapDataType
 * \ingroup datatype
 *
 * \brief DataType describing a map
 */
#pragma once

#include "datatype.h"

namespace document {

class MapDataType : public DataType {
    const DataType *_keyType;
    const DataType *_valueType;

public:
    MapDataType() : _keyType(0), _valueType(0) {}
    MapDataType(const DataType &keyType, const DataType &valueType);
    MapDataType(const DataType &keyType, const DataType &valueType, int id);

    const DataType& getKeyType() const { return *_keyType; }
    const DataType& getValueType() const { return *_valueType; }

    std::unique_ptr<FieldValue> createFieldValue() const override;
    void print(std::ostream&, bool verbose, const std::string& indent) const override;
    bool operator==(const DataType& other) const override;
    MapDataType* clone() const override { return new MapDataType(*this); }

    void onBuildFieldPath(FieldPath & path, vespalib::stringref remainFieldName) const override;
    static void buildFieldPathImpl(FieldPath & path, const DataType& dataType,
                                   vespalib::stringref remainFieldName,
                                   const DataType &keyType, const DataType &valueType);

    DECLARE_IDENTIFIABLE(MapDataType);
};

} // document
