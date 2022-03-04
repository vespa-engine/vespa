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

class MapDataType final : public DataType {
    const DataType *_keyType;
    const DataType *_valueType;

public:
    MapDataType(const DataType &keyType, const DataType &valueType) noexcept;
    MapDataType(const DataType &keyType, const DataType &valueType, int id) noexcept;
    MapDataType(const MapDataType &) = delete;
    MapDataType & operator=(const MapDataType &) = delete;
    ~MapDataType() override;

    const DataType& getKeyType() const noexcept { return *_keyType; }
    const DataType& getValueType() const noexcept { return *_valueType; }

    std::unique_ptr<FieldValue> createFieldValue() const override;
    void print(std::ostream&, bool verbose, const std::string& indent) const override;
    bool equals(const DataType& other) const noexcept override;
    const MapDataType * cast_map() const noexcept override { return this; }

    void onBuildFieldPath(FieldPath & path, vespalib::stringref remainFieldName) const override;
    static void buildFieldPathImpl(FieldPath & path, const DataType& dataType,
                                   vespalib::stringref remainFieldName,
                                   const DataType &keyType, const DataType &valueType);
};

} // document
