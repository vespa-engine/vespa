// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fieldinfo.h"

namespace search::fef {

FieldInfo::FieldInfo(FieldType type_in, CollectionType collection_in, const string& name_in, uint32_t id_in)
    : _type(type_in),
      _data_type(DataType::DOUBLE),
      _collection(collection_in),
      _name(name_in),
      _id(id_in),
      _threshold(false),
      _element_gap(),
      _hasAttribute(type_in == FieldType::ATTRIBUTE) {
}

const FieldInfo& FieldInfo::no_field() {
    static const FieldInfo instance = [] {
        FieldInfo field(FieldType::NONE, CollectionType::SINGLE, "", 0);
        // RAW is opaque to ranking, keeping the "no field" out of the
        // data type filters used when dumping and setting up rank features.
        field.set_data_type(DataType::RAW);
        return field;
    }();
    return instance;
}

} // namespace search::fef
