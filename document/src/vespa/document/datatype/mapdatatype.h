// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::MapDataType
 * \ingroup datatype
 *
 * \brief DataType describing a map
 */
#pragma once

#include <vespa/document/datatype/datatype.h>

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

    virtual std::unique_ptr<FieldValue> createFieldValue() const;
    virtual void print(std::ostream&, bool verbose,
                       const std::string& indent) const;
    virtual bool operator==(const DataType& other) const;
    virtual MapDataType* clone() const { return new MapDataType(*this); }

    FieldPath::UP onBuildFieldPath(
            const vespalib::stringref &remainFieldName) const;
    static FieldPath::UP buildFieldPathImpl(
            const DataType& dataType,
            const vespalib::stringref &remainFieldName,
            const DataType &keyType,
            const DataType &valueType);

    DECLARE_IDENTIFIABLE(MapDataType);
};

} // document

