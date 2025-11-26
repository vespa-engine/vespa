// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/field.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/document/datatype/datatype.h>
#include <cassert>
#include <map>
#include <string>
#include <vector>

namespace document::new_config_builder {

// Forward declarations
class NewConfigBuilder;
class NewDocTypeRep;
class NewStruct;

/**
 * TypeRef - Represents an idx-based reference to a type in the new config format.
 * Unlike the old format which used ID-based references, this uses idx values (10000+).
 */
struct TypeRef {
    int32_t idx;  // Index in the config (10000+)

    explicit TypeRef(int32_t i) : idx(i) {}
    operator int32_t() const { return idx; }
};

/**
 * NewStruct - Builder for struct types in the new format.
 * Supports field addition, tensor fields, and struct inheritance.
 */
class NewStruct {
    friend class NewConfigBuilder;
    friend class NewDocTypeRep;

private:
    NewConfigBuilder& _builder;
    std::string _name;
    int32_t _internalid;
    int32_t _idx;
    int32_t _doctype_idx;
    std::vector<std::pair<std::string, TypeRef>> _fields;
    std::vector<std::pair<std::string, std::string>> _tensor_fields;
    std::vector<TypeRef> _inherits;
    bool _registered;

    NewStruct(NewConfigBuilder& builder, std::string name, int32_t doctype_idx);

    int32_t hashId(const std::string& name) const;

public:
    ~NewStruct();
    NewStruct(const NewStruct&) = delete;
    NewStruct(NewStruct&&) = default;
    NewStruct& operator=(const NewStruct&) = delete;
    NewStruct& operator=(NewStruct&&) = default;

    NewStruct& addField(const std::string& name, TypeRef type);
    NewStruct& addTensorField(const std::string& name, const std::string& spec);
    NewStruct& inherit(TypeRef parent_struct);
    NewStruct& setId(int32_t internalid);

    TypeRef ref();  // Finalize and get TypeRef for this struct
};

/**
 * NewArray - Builder for array types.
 */
class NewArray {
    friend class NewConfigBuilder;
    friend class NewDocTypeRep;

private:
    NewConfigBuilder& _builder;
    TypeRef _element_type;
    int32_t _idx;
    int32_t _doctype_idx;
    bool _registered;

    NewArray(NewConfigBuilder& builder, TypeRef element_type, int32_t doctype_idx);

public:
    TypeRef ref();
};

/**
 * NewWset - Builder for weighted set types.
 */
class NewWset {
    friend class NewConfigBuilder;
    friend class NewDocTypeRep;

private:
    NewConfigBuilder& _builder;
    TypeRef _element_type;
    int32_t _idx;
    int32_t _doctype_idx;
    bool _registered;
    bool _removeifzero;
    bool _createifnonexistent;

    NewWset(NewConfigBuilder& builder, TypeRef element_type, int32_t doctype_idx);

public:
    NewWset& removeIfZero();
    NewWset& createIfNonExistent();
    TypeRef ref();
};

/**
 * NewMap - Builder for map types.
 */
class NewMap {
    friend class NewConfigBuilder;
    friend class NewDocTypeRep;

private:
    NewConfigBuilder& _builder;
    TypeRef _key_type;
    TypeRef _value_type;
    int32_t _idx;
    int32_t _doctype_idx;
    bool _registered;

    NewMap(NewConfigBuilder& builder, TypeRef key_type, TypeRef value_type, int32_t doctype_idx);

public:
    TypeRef ref();
};

/**
 * NewAnnotationRef - Builder for annotation reference types.
 */
class NewAnnotationRef {
    friend class NewConfigBuilder;
    friend class NewDocTypeRep;

private:
    NewConfigBuilder& _builder;
    int32_t _annotation_idx;
    int32_t _idx;
    bool _registered;

    NewAnnotationRef(NewConfigBuilder& builder, int32_t annotation_idx);

public:
    TypeRef ref();
};

/**
 * NewDocTypeRep - Fluent API for configuring a document type.
 * Manages the contentstruct, inheritance, annotations, and other document-level features.
 */
class NewDocTypeRep {
    friend class NewConfigBuilder;

private:
    NewConfigBuilder& _builder;
    int32_t _idx;
    std::string _name;
    int32_t _internalid;
    int32_t _contentstruct_idx;
    std::vector<int32_t> _inherits;
    std::vector<std::string> _imported_fields;
    std::map<std::string, std::vector<std::string>> _field_sets;

    // Annotation and reference data
    struct AnnotationTypeData {
        int32_t idx;
        std::string name;
        int32_t internalid;
        int32_t datatype_idx;  // -1 if no datatype
    };
    std::vector<AnnotationTypeData> _annotations;

    NewDocTypeRep(NewConfigBuilder& builder, int32_t idx, std::string name, int32_t internalid);

    int32_t hashId(const std::string& name) const;

public:
    // Add fields to contentstruct
    NewDocTypeRep& addField(const std::string& name, TypeRef type);
    NewDocTypeRep& addTensorField(const std::string& name, const std::string& spec);

    // Type factory methods (create types owned by this doctype)
    NewStruct createStruct(const std::string& name);
    NewArray createArray(TypeRef element_type);
    NewWset createWset(TypeRef element_type);
    NewMap createMap(TypeRef key_type, TypeRef value_type);
    TypeRef registerStruct(NewStruct&& s);
    TypeRef registerArray(NewArray&& a);
    TypeRef registerWset(NewWset&& w);
    TypeRef registerMap(NewMap&& m);

    // Inheritance
    NewDocTypeRep& inherit(int32_t parent_idx);
    NewDocTypeRep& inherit(const std::string& parent_name);

    // Annotations
    NewDocTypeRep& annotationType(int32_t id, const std::string& name);
    NewDocTypeRep& annotationType(int32_t id, const std::string& name, TypeRef datatype);
    TypeRef createAnnotationType(int32_t id, const std::string& name);
    TypeRef createAnnotationType(int32_t id, const std::string& name, TypeRef datatype);
    TypeRef createAnnotationReference(TypeRef annotation_type_idx);

    // Document references
    TypeRef referenceType(int32_t target_doctype_idx);

    // Imported fields
    NewDocTypeRep& imported_field(const std::string& field_name);

    // Field sets
    NewDocTypeRep& fieldSet(const std::string& name, const std::vector<std::string>& fields);

    // Get idx of this document type
    int32_t idx() const { return _idx; }
    TypeRef ref() const { return TypeRef(_idx); }
};

/**
 * NewConfigBuilder - Main builder class for creating modern doctype[] configurations.
 * Automatically sets up a base "document" type with all primitive types.
 */
class NewConfigBuilder {
    friend class NewStruct;
    friend class NewArray;
    friend class NewWset;
    friend class NewMap;
    friend class NewAnnotationRef;
    friend class NewDocTypeRep;

private:
    ::document::config::DocumenttypesConfigBuilder _config;
    std::map<std::string, int32_t> _doctype_map;  // name -> idx
    std::map<int32_t, int32_t> _primitive_idx_map;  // DataType::Type -> idx
    std::map<int32_t, int32_t> _idx_to_internalid_map;  // idx -> internalid
    std::map<int32_t, NewDocTypeRep*> _doctype_builders;  // idx -> NewDocTypeRep
    int32_t _next_idx;
    int32_t _base_document_idx;
    int32_t _position_type_idx;

    void setupBaseDocument();
    void addPrimitiveToBase(const std::string& name, int32_t type_id);

    // Internal registration methods
    void registerStruct(NewStruct& s, int32_t doctype_idx);
    void registerArray(NewArray& a, int32_t doctype_idx);
    void registerWset(NewWset& w, int32_t doctype_idx);
    void registerMap(NewMap& m, int32_t doctype_idx);
    void registerAnnotationRef(NewAnnotationRef& ar, int32_t doctype_idx);
    void finalizeDocType(NewDocTypeRep& doc);

public:
    NewConfigBuilder();
    ~NewConfigBuilder();

    // Get the built configuration
    const DocumenttypesConfig& config();

    // Create a new document type (automatically inherits from base "document")
    NewDocTypeRep& document(const std::string& name);
    NewDocTypeRep& document(const std::string& name, int32_t internalid);

    // Get reference to primitive type (returns idx from base document)
    TypeRef primitiveType(int32_t type_id);  // DataType::T_INT, etc.

    // convenience methods:
    TypeRef boolTypeRef()      { return primitiveType(DataType::T_BOOL); }
    TypeRef byteTypeRef()      { return primitiveType(DataType::T_BYTE); }
    TypeRef doubleTypeRef()    { return primitiveType(DataType::T_DOUBLE); }
    TypeRef floatTypeRef()     { return primitiveType(DataType::T_FLOAT); }
    TypeRef intTypeRef()       { return primitiveType(DataType::T_INT); }
    TypeRef longTypeRef()      { return primitiveType(DataType::T_LONG); }
    TypeRef predicateTypeRef() { return primitiveType(DataType::T_PREDICATE); }
    TypeRef rawTypeRef()       { return primitiveType(DataType::T_RAW); }
    TypeRef shortTypeRef()     { return primitiveType(DataType::T_SHORT); }
    TypeRef stringTypeRef()    { return primitiveType(DataType::T_STRING); }
    TypeRef tagTypeRef()       { return primitiveType(DataType::T_TAG); }
    TypeRef uriTypeRef()       { return primitiveType(DataType::T_URI); }

    // Get reference to built-in position type (returns idx from base document)
    TypeRef positionType();

    // Lookup internalid from TypeRef idx (searches all types, returns 0 if not found)
    int32_t getInternalId(TypeRef type_ref) const;

    // Get type name from TypeRef idx (returns empty string if not found)
    std::string getTypeName(TypeRef type_ref) const;

    // Add a field to an existing struct by idx
    void registerStructField(TypeRef struct_idx, const std::string& fieldname, TypeRef field_idx);

    // Access to base "document" type
    int32_t baseDocumentIdx() const { return _base_document_idx; }

    // Factory methods for types (automatically registered with given doctype)
    NewStruct createStruct(const std::string& name, int32_t doctype_idx);
    NewArray createArray(TypeRef element_type, int32_t doctype_idx);
    NewWset createWset(TypeRef element_type, int32_t doctype_idx);
    NewMap createMap(TypeRef key_type, TypeRef value_type, int32_t doctype_idx);
};

}  // namespace document::new_config_builder
