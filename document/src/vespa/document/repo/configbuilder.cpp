// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configbuilder.h"
#include <vespa/document/datatype/structdatatype.h>


namespace document::config_builder {

int32_t createFieldId(const vespalib::string &name, int32_t type) {
    StructDataType dummy("dummy", type);
    Field f(name, dummy);
    return f.getId();
}

int32_t DatatypeConfig::id_counter = 100;

DatatypeConfig::DatatypeConfig() {
    id = ++id_counter;
}

DatatypeConfig::DatatypeConfig(const DatatypeConfig&) = default;
DatatypeConfig& DatatypeConfig::operator=(const DatatypeConfig&) = default;

void DatatypeConfig::addNestedType(const TypeOrId &t) {
    if (t.has_type) {
        nested_types.insert(nested_types.end(),
                            t.type.nested_types.begin(),
                            t.type.nested_types.end());
        nested_types.push_back(t.type);
    }
}

Struct &
Struct::addTensorField(const vespalib::string &name, const vespalib::string &spec) {
    sstruct.field.resize(sstruct.field.size() + 1);
    auto &field = sstruct.field.back();
    field.name = name;
    field.id = createFieldId(name, DataType::T_TENSOR);
    field.datatype = DataType::T_TENSOR;
    field.detailedtype = spec;
    return *this;
}

namespace {

void addType(const DatatypeConfig &type,
             DocumenttypesConfig::Documenttype &doc_type) {
    doc_type.datatype.insert(doc_type.datatype.end(),
                             type.nested_types.begin(),
                             type.nested_types.end());
    doc_type.datatype.push_back(type);
}

}

DocTypeRep &
DocTypeRep::annotationType(int32_t id, const vespalib::string &name, const DatatypeConfig &type) {
    addType(type, doc_type);
    return annotationType(id, name, type.id);
}


DocTypeRep
DocumenttypesConfigBuilderHelper::document(int32_t id, const vespalib::string &name,
                                           const DatatypeConfig &header,
                                           const DatatypeConfig &body) {
    assert(header.type == DatatypeConfig::Type::STRUCT);
    assert(body.type == DatatypeConfig::Type::STRUCT);
    _config.documenttype.resize(_config.documenttype.size() + 1);
    _config.documenttype.back().id = id;
    _config.documenttype.back().name = name;
    _config.documenttype.back().headerstruct = header.id;
    _config.documenttype.back().bodystruct = body.id;
    addType(header, _config.documenttype.back());
    addType(body, _config.documenttype.back());
    return DocTypeRep(_config.documenttype.back());
}

}
