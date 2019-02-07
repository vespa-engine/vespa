// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/field.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/datatype/structdatatype.h>
#include <vespa/vespalib/stllike/string.h>
#include <cassert>

namespace document::config_builder {

struct TypeOrId;

struct DatatypeConfig : DocumenttypesConfig::Documenttype::Datatype {
    static int32_t id_counter;
    std::vector<DatatypeConfig> nested_types;

    DatatypeConfig();

    DatatypeConfig(const DatatypeConfig&);
    DatatypeConfig& operator=(const DatatypeConfig&);

    DatatypeConfig &setId(int32_t i) { id = i; return *this; }
    void addNestedType(const TypeOrId &t);
};

int32_t createFieldId(const vespalib::string &name, int32_t type);

struct TypeOrId {
    int32_t id;
    bool has_type;
    DatatypeConfig type;

    TypeOrId(int32_t i) : id(i), has_type(false), type() {}
    TypeOrId(const DatatypeConfig &t) : id(t.id), has_type(true), type(t) {}
};

struct Struct : DatatypeConfig {
    Struct(const vespalib::string &name) {
        type = STRUCT;
        sstruct.name = name;
    }
    Struct &setCompression(Sstruct::Compression::Type t, int32_t level,
                           int32_t threshold, int32_t min_size) {
        sstruct.compression.type = t;
        sstruct.compression.level = level;
        sstruct.compression.threshold = threshold;
        sstruct.compression.minsize = min_size;
        return *this;
    }
    Struct &addField(const vespalib::string &name, TypeOrId data_type) {
        addNestedType(data_type);
        sstruct.field.resize(sstruct.field.size() + 1);
        sstruct.field.back().name = name;
        sstruct.field.back().id = createFieldId(name, data_type.id);
        sstruct.field.back().datatype = data_type.id;
        return *this;
    }
    Struct &setId(int32_t i) { DatatypeConfig::setId(i); return *this; }
};

struct Array : DatatypeConfig {
    Array(TypeOrId nested_type) {
        addNestedType(nested_type);
        type = ARRAY;
        array.element.id = nested_type.id;
    }
};

struct Wset : DatatypeConfig {
    Wset(TypeOrId nested_type) {
        addNestedType(nested_type);
        type = WSET;
        wset.key.id = nested_type.id;
    }
    Wset &removeIfZero() { wset.removeifzero = true; return *this; }
    Wset &createIfNonExistent() {
        wset.createifnonexistent = true;
        return *this;
    }
};

struct Map : DatatypeConfig {
    Map(TypeOrId key_type, TypeOrId value_type) {
        addNestedType(key_type);
        addNestedType(value_type);
        type = MAP;
        map.key.id = key_type.id;
        map.value.id = value_type.id;
    }
};

struct AnnotationRef : DatatypeConfig {
    AnnotationRef(int32_t annotation_type_id) {
        type = ANNOTATIONREF;
        annotationref.annotation.id = annotation_type_id;
    }
};

struct DocTypeRep {
    DocumenttypesConfig::Documenttype &doc_type;

    DocTypeRep(DocumenttypesConfig::Documenttype &type)
        : doc_type(type) {}
    DocTypeRep &inherit(int32_t id) {
        doc_type.inherits.resize(doc_type.inherits.size() + 1);
        doc_type.inherits.back().id = id;
        return *this;
    }
    DocTypeRep &annotationType(int32_t id, const vespalib::string &name,
                               int32_t datatype) {
        doc_type.annotationtype.resize(doc_type.annotationtype.size() + 1);
        doc_type.annotationtype.back().id = id;
        doc_type.annotationtype.back().name = name;
        doc_type.annotationtype.back().datatype = datatype;
        return *this;
    }
    DocTypeRep &annotationType(int32_t id, const vespalib::string &name,
                               const DatatypeConfig &type);

    DocTypeRep& referenceType(int32_t id, int32_t target_type_id) {
        doc_type.referencetype.resize(doc_type.referencetype.size() + 1);
        doc_type.referencetype.back().id = id;
        doc_type.referencetype.back().targetTypeId = target_type_id;
        return *this;
    }
};

class DocumenttypesConfigBuilderHelper {
    ::document::DocumenttypesConfigBuilder _config;

public:
    DocumenttypesConfigBuilderHelper() {}
    DocumenttypesConfigBuilderHelper(const DocumenttypesConfig &c)
        : _config(c) {}

    DocTypeRep document(int32_t id, const vespalib::string &name,
                        const DatatypeConfig &header,
                        const DatatypeConfig &body);

    ::document::DocumenttypesConfigBuilder &config() { return _config; }
};

}

