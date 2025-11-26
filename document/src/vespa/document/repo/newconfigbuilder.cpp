// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "newconfigbuilder.h"
#include <vespa/document/datatype/arraydatatype.h>
#include <vespa/document/datatype/mapdatatype.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/document/datatype/referencedatatype.h>
#include <vespa/document/datatype/structdatatype.h>
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace document::new_config_builder {

using BDocType        = ::document::config::DocumenttypesConfigBuilder::Doctype;
using BPrimitiveT     = ::document::config::DocumenttypesConfigBuilder::Doctype::Primitivetype;
using BArrayT         = ::document::config::DocumenttypesConfigBuilder::Doctype::Arraytype;
using BMapT           = ::document::config::DocumenttypesConfigBuilder::Doctype::Maptype;
using BWsetT          = ::document::config::DocumenttypesConfigBuilder::Doctype::Wsettype;
using BTensorT        = ::document::config::DocumenttypesConfigBuilder::Doctype::Tensortype;
using BAnnotationT    = ::document::config::DocumenttypesConfigBuilder::Doctype::Annotationtype;
using BAnnRefT        = ::document::config::DocumenttypesConfigBuilder::Doctype::Annotationref;
using BStructT        = ::document::config::DocumenttypesConfigBuilder::Doctype::Structtype;
using BStructField    = ::document::config::DocumenttypesConfigBuilder::Doctype::Structtype::Field;
using BStructInherits = ::document::config::DocumenttypesConfigBuilder::Doctype::Structtype::Inherits;
using BDocInherit     = ::document::config::DocumenttypesConfigBuilder::Doctype::Inherits;
using BDocImportField = ::document::config::DocumenttypesConfigBuilder::Doctype::Importedfield;

namespace {

int32_t hashId(const std::string& name) {
    StructDataType tmp(name);
    int32_t id = tmp.getId();
    fprintf(stderr, "DEBUG hashId: '%s' -> %d\n", name.c_str(), id);
    return id;
}

int32_t createFieldId(const std::string& name, int32_t type) {
    StructDataType dummy("dummy", type);
    Field f(name, dummy);
    int32_t field_id = f.getId();
    fprintf(stderr, "DEBUG createFieldId: field='%s', type_id=%d -> field_id=%d\n",
            name.c_str(), type, field_id);
    return field_id;
}

}  // anonymous namespace

// ==================== NewStruct ====================

NewStruct::NewStruct(NewConfigBuilder& builder, std::string name, int32_t doctype_idx)
    : _builder(builder),
      _name(std::move(name)),
      _internalid(hashId(_name)),
      _idx(-1),
      _doctype_idx(doctype_idx),
      _registered(false)
{
}

NewStruct::~NewStruct() = default;

int32_t NewStruct::hashId(const std::string& name) const {
    return ::document::new_config_builder::hashId(name);
}

NewStruct& NewStruct::addField(const std::string& name, TypeRef type) {
    assert(!_registered && "Cannot modify struct after it's been registered");
    _fields.emplace_back(name, type);
    return *this;
}

NewStruct& NewStruct::addTensorField(const std::string& name, const std::string& spec) {
    assert(!_registered && "Cannot modify struct after it's been registered");
    _tensor_fields.emplace_back(name, spec);
    return *this;
}

NewStruct& NewStruct::inherit(TypeRef parent_struct) {
    assert(!_registered && "Cannot modify struct after it's been registered");
    _inherits.push_back(parent_struct);
    return *this;
}

NewStruct& NewStruct::setId(int32_t internalid) {
    assert(!_registered && "Cannot modify struct after it's been registered");
    _internalid = internalid;
    return *this;
}

TypeRef NewStruct::ref() {
    if (!_registered) {
        // Auto-register when ref() is called
        _builder.registerStruct(*this, _doctype_idx);
    }
    assert(_registered && "Struct should have been auto-registered");
    return TypeRef(_idx);
}

// ==================== NewArray ====================

NewArray::NewArray(NewConfigBuilder& builder, TypeRef element_type, int32_t doctype_idx)
    : _builder(builder),
      _element_type(element_type),
      _idx(-1),
      _doctype_idx(doctype_idx),
      _registered(false)
{
    // Auto-register immediately
    _builder.registerArray(*this, _doctype_idx);
}

TypeRef NewArray::ref() {
    assert(_registered && "Array should have been auto-registered");
    return TypeRef(_idx);
}

// ==================== NewWset ====================

NewWset::NewWset(NewConfigBuilder& builder, TypeRef element_type, int32_t doctype_idx)
    : _builder(builder),
      _element_type(element_type),
      _idx(-1),
      _doctype_idx(doctype_idx),
      _registered(false),
      _removeifzero(false),
      _createifnonexistent(false)
{
}

NewWset& NewWset::removeIfZero() {
    assert(!_registered && "Cannot modify wset after it's been registered");
    _removeifzero = true;
    return *this;
}

NewWset& NewWset::createIfNonExistent() {
    assert(!_registered && "Cannot modify wset after it's been registered");
    _createifnonexistent = true;
    return *this;
}

TypeRef NewWset::ref() {
    if (!_registered) {
        // Auto-register when ref() is called
        _builder.registerWset(*this, _doctype_idx);
    }
    assert(_registered && "Wset should have been auto-registered");
    return TypeRef(_idx);
}

// ==================== NewMap ====================

NewMap::NewMap(NewConfigBuilder& builder, TypeRef key_type, TypeRef value_type, int32_t doctype_idx)
    : _builder(builder),
      _key_type(key_type),
      _value_type(value_type),
      _idx(-1),
      _doctype_idx(doctype_idx),
      _registered(false)
{
    // Auto-register immediately
    _builder.registerMap(*this, _doctype_idx);
}

TypeRef NewMap::ref() {
    assert(_registered && "Map should have been auto-registered");
    return TypeRef(_idx);
}

// ==================== NewAnnotationRef ====================

NewAnnotationRef::NewAnnotationRef(NewConfigBuilder& builder, int32_t annotation_idx)
    : _builder(builder),
      _annotation_idx(annotation_idx),
      _idx(-1),
      _registered(false)
{
}

TypeRef NewAnnotationRef::ref() {
    if (!_registered) {
        assert(false && "AnnotationRef must be registered before getting ref");
    }
    return TypeRef(_idx);
}

// ==================== NewDocTypeRep ====================

NewDocTypeRep::NewDocTypeRep(NewConfigBuilder& builder, int32_t idx, std::string name, int32_t internalid)
    : _builder(builder),
      _idx(idx),
      _name(std::move(name)),
      _internalid(internalid),
      _contentstruct_idx(-1)
{
}

int32_t NewDocTypeRep::hashId(const std::string& name) const {
    return ::document::new_config_builder::hashId(name);
}

NewDocTypeRep& NewDocTypeRep::addField(const std::string& name, TypeRef type) {
    // Find the doctype and contentstruct
    BDocType* doc = nullptr;
    for (auto& d : _builder._config.doctype) {
        if (d.idx == _idx) {
            doc = &d;
            break;
        }
    }
    assert(doc && "Document type not found");

    // Find contentstruct
    BStructT* contentstruct = nullptr;
    for (auto& st : doc->structtype) {
        if (st.idx == doc->contentstruct) {
            contentstruct = &st;
            break;
        }
    }
    assert(contentstruct && "Content struct not found");

    // Add field to contentstruct
    auto& f = contentstruct->field.emplace_back();
    f.name = name;
    f.type = type.idx;
    // Field ID is computed from field name and field TYPE's internalid
    f.internalid = createFieldId(name, _builder.getInternalId(type));

    return *this;
}

NewDocTypeRep& NewDocTypeRep::addTensorField(const std::string& name, const std::string& spec) {
    // Find the doctype
    BDocType* doc = nullptr;
    for (auto& d : _builder._config.doctype) {
        if (d.idx == _idx) {
            doc = &d;
            break;
        }
    }
    assert(doc && "Document type not found");

    // Create tensor type
    auto& tt = doc->tensortype.emplace_back();
    tt.idx = _builder._next_idx++;
    tt.detailedtype = spec;
    _builder._idx_to_internalid_map[tt.idx] = DataType::T_TENSOR;

    // Add field to contentstruct via addField
    return addField(name, TypeRef(tt.idx));
}

NewDocTypeRep& NewDocTypeRep::inherit(int32_t parent_idx) {
    _inherits.push_back(parent_idx);
    return *this;
}

NewDocTypeRep& NewDocTypeRep::inherit(const std::string& parent_name) {
    auto it = _builder._doctype_map.find(parent_name);
    assert(it != _builder._doctype_map.end() && "Parent document type not found");
    return inherit(it->second);
}

NewDocTypeRep& NewDocTypeRep::annotationType(int32_t id, const std::string& name) {
    AnnotationTypeData ann;
    ann.idx = _builder._next_idx++;
    ann.name = name;
    ann.internalid = id;
    ann.datatype_idx = -1;
    _annotations.push_back(ann);
    return *this;
}

NewDocTypeRep& NewDocTypeRep::annotationType(int32_t id, const std::string& name, TypeRef datatype) {
    AnnotationTypeData ann;
    ann.idx = _builder._next_idx++;
    ann.name = name;
    ann.internalid = id;
    ann.datatype_idx = datatype.idx;
    _annotations.push_back(ann);
    return *this;
}

TypeRef NewDocTypeRep::createAnnotationType(int32_t id, const std::string& name) {
    AnnotationTypeData ann;
    ann.idx = _builder._next_idx++;
    ann.name = name;
    ann.internalid = id;
    ann.datatype_idx = -1;
    _annotations.push_back(ann);
    return TypeRef(ann.idx);
}

TypeRef NewDocTypeRep::createAnnotationType(int32_t id, const std::string& name, TypeRef datatype) {
    AnnotationTypeData ann;
    ann.idx = _builder._next_idx++;
    ann.name = name;
    ann.internalid = id;
    ann.datatype_idx = datatype.idx;
    _annotations.push_back(ann);
    return TypeRef(ann.idx);
}

TypeRef NewDocTypeRep::createAnnotationReference(TypeRef annotation_type_idx) {
    // First, ensure the annotation type is in the config
    // Find the doctype
    BDocType* doc_config = nullptr;
    for (auto& d : _builder._config.doctype) {
        if (d.idx == _idx) {
            doc_config = &d;
            break;
        }
    }
    assert(doc_config && "Document type not found");

    // Add the annotation to the config if not already there
    bool found = false;
    for (const auto& ann : doc_config->annotationtype) {
        if (ann.idx == annotation_type_idx.idx) {
            found = true;
            break;
        }
    }

    if (!found) {
        // Find it in our local _annotations list
        for (const auto& ann : _annotations) {
            if (ann.idx == annotation_type_idx.idx) {
                auto& a = doc_config->annotationtype.emplace_back();
                a.idx = ann.idx;
                a.name = ann.name;
                a.internalid = ann.internalid;
                if (ann.datatype_idx >= 0) {
                    a.datatype = ann.datatype_idx;
                }
                break;
            }
        }
    }

    // Create an annotation reference using the NewAnnotationRef helper
    NewAnnotationRef ar(_builder, annotation_type_idx.idx);
    _builder.registerAnnotationRef(ar, _idx);
    return ar.ref();
}

TypeRef NewDocTypeRep::referenceType(int32_t target_doctype_idx) {
    // Find the doctype
    BDocType* doc = nullptr;
    for (auto& d : _builder._config.doctype) {
        if (d.idx == _idx) {
            doc = &d;
            break;
        }
    }
    assert(doc && "Document type not found");

    // Find the target document type name
    std::string target_doctype_name;
    for (const auto& d : _builder._config.doctype) {
        if (d.idx == target_doctype_idx) {
            target_doctype_name = d.name;
            break;
        }
    }
    assert(!target_doctype_name.empty() && "Target document type not found");

    // Create document reference type
    auto& dref = doc->documentref.emplace_back();
    dref.idx = _builder._next_idx++;
    dref.targettype = target_doctype_idx;

    dref.internalid = ReferenceDataType::makeInternalId(target_doctype_name);
    _builder._idx_to_internalid_map[dref.idx] = dref.internalid;

    return TypeRef(dref.idx);
}

NewDocTypeRep& NewDocTypeRep::imported_field(const std::string& field_name) {
    _imported_fields.push_back(field_name);
    return *this;
}

NewDocTypeRep& NewDocTypeRep::fieldSet(const std::string& name, const std::vector<std::string>& fields) {
    _field_sets[name] = fields;
    return *this;
}

NewStruct NewDocTypeRep::createStruct(const std::string& name) {
    return NewStruct(_builder, name, _idx);
}

NewArray NewDocTypeRep::createArray(TypeRef element_type) {
    return NewArray(_builder, element_type, _idx);
}

NewWset NewDocTypeRep::createWset(TypeRef element_type) {
    return NewWset(_builder, element_type, _idx);
}

NewMap NewDocTypeRep::createMap(TypeRef key_type, TypeRef value_type) {
    return NewMap(_builder, key_type, value_type, _idx);
}

TypeRef NewDocTypeRep::registerStruct(NewStruct&& s) {
    _builder.registerStruct(s, _idx);
    return TypeRef(s._idx);
}

TypeRef NewDocTypeRep::registerArray(NewArray&& a) {
    _builder.registerArray(a, _idx);
    return TypeRef(a._idx);
}

TypeRef NewDocTypeRep::registerWset(NewWset&& w) {
    _builder.registerWset(w, _idx);
    return TypeRef(w._idx);
}

TypeRef NewDocTypeRep::registerMap(NewMap&& m) {
    _builder.registerMap(m, _idx);
    return TypeRef(m._idx);
}

// ==================== NewConfigBuilder ====================

NewConfigBuilder::NewConfigBuilder()
    : _next_idx(10000),
      _base_document_idx(-1),
      _position_type_idx(-1)
{
    setupBaseDocument();
}

NewConfigBuilder::~NewConfigBuilder() {
    // Clean up NewDocTypeRep instances
    for (auto& pair : _doctype_builders) {
        delete pair.second;
    }
}

void NewConfigBuilder::setupBaseDocument() {
    // Create the base "document" type with all primitives
    _config.doctype.reserve(100);
    auto& root = _config.doctype.emplace_back();
    root.idx = _next_idx++;
    root.name = "document";
    root.internalid = DataType::T_DOCUMENT;
    _base_document_idx = root.idx;
    _doctype_map["document"] = root.idx;
    _idx_to_internalid_map[root.idx] = DataType::T_DOCUMENT;

    // Create a dummy contentstruct for the base document
    root.structtype.reserve(100);
    auto& st = root.structtype.emplace_back();
    st.idx = _next_idx++;
    st.name = "document.header";
    st.internalid = hashId("document.header");
    root.contentstruct = st.idx;
    _idx_to_internalid_map[st.idx] = st.internalid;

    // Add all primitive types
    addPrimitiveToBase("int", DataType::T_INT);
    addPrimitiveToBase("float", DataType::T_FLOAT);
    addPrimitiveToBase("string", DataType::T_STRING);
    addPrimitiveToBase("raw", DataType::T_RAW);
    addPrimitiveToBase("long", DataType::T_LONG);
    addPrimitiveToBase("double", DataType::T_DOUBLE);
    addPrimitiveToBase("bool", DataType::T_BOOL);
    addPrimitiveToBase("uri", DataType::T_URI);
    addPrimitiveToBase("byte", DataType::T_BYTE);
    addPrimitiveToBase("tag", DataType::T_TAG);
    addPrimitiveToBase("short", DataType::T_SHORT);
    addPrimitiveToBase("predicate", DataType::T_PREDICATE);

    // Add built-in position struct (added at end to not disrupt existing idx values)
    const auto& position_type = PositionDataType::getInstance();
    auto& position_struct = root.structtype.emplace_back();
    position_struct.idx = _next_idx++;
    position_struct.name = "position";
    position_struct.internalid = position_type.getId();
    _idx_to_internalid_map[position_struct.idx] = position_struct.internalid;
    _position_type_idx = position_struct.idx;  // Save for positionType() method

    // Add x field to position
    auto& x_field = position_struct.field.emplace_back();
    x_field.name = "x";
    x_field.type = _primitive_idx_map[DataType::T_INT];
    x_field.internalid = position_type.getField("x").getId();

    // Add y field to position
    auto& y_field = position_struct.field.emplace_back();
    y_field.name = "y";
    y_field.type = _primitive_idx_map[DataType::T_INT];
    y_field.internalid = position_type.getField("y").getId();

    // Add built-in tag wset type WeightedSet<String> (added at end to not disrupt existing idx values)
    auto& tag_wset = root.wsettype.emplace_back();
    tag_wset.idx = _next_idx++;
    tag_wset.elementtype = _primitive_idx_map[DataType::T_STRING];
    tag_wset.createifnonexistent = true;
    tag_wset.removeifzero = true;
    tag_wset.internalid = DataType::T_TAG;
    _idx_to_internalid_map[tag_wset.idx] = tag_wset.internalid;
}

void NewConfigBuilder::addPrimitiveToBase(const std::string& name, int32_t type_id) {
    BPrimitiveT pt;
    pt.idx = _next_idx++;
    pt.name = name;
    _config.doctype[0].primitivetype.push_back(pt);
    _primitive_idx_map[type_id] = pt.idx;
    _idx_to_internalid_map[pt.idx] = type_id;  // Primitives: internalid = DataType::T_* value
}

const DocumenttypesConfig& NewConfigBuilder::config() {
    // Finalize all document types before returning config
    for (auto& pair : _doctype_builders) {
        finalizeDocType(*pair.second);
    }
    return _config;
}

NewDocTypeRep& NewConfigBuilder::document(const std::string& name) {
    return document(name, hashId(name));
}

NewDocTypeRep& NewConfigBuilder::document(const std::string& name, int32_t internalid) {
    // Check if document type already exists
    auto it = _doctype_map.find(name);
    if (it != _doctype_map.end()) {
        return *_doctype_builders[it->second];
    }

    // Check for ID collision with existing documents
    for (const auto& existing : _config.doctype) {
        if (existing.internalid == internalid && existing.name != name) {
            throw vespalib::IllegalArgumentException(
                vespalib::make_string("Document type ID collision: ID %d is already used by document type '%s', cannot assign to '%s'",
                                     internalid, existing.name.c_str(), name.c_str()));
        }
    }

    // Create new document type
    auto& doc = _config.doctype.emplace_back();
    doc.idx = _next_idx++;
    doc.name = name;
    doc.internalid = internalid;
    _doctype_map[name] = doc.idx;
    _idx_to_internalid_map[doc.idx] = internalid;

    // Create contentstruct
    doc.structtype.reserve(100);
    auto& st = doc.structtype.emplace_back();
    st.idx = _next_idx++;
    st.name = name + ".header";
    st.internalid = hashId(st.name);
    doc.contentstruct = st.idx;
    _idx_to_internalid_map[st.idx] = st.internalid;

    // Automatically inherit from base document
    doc.inherits.emplace_back().idx = _base_document_idx;

    // Create and store NewDocTypeRep
    auto* rep = new NewDocTypeRep(*this, doc.idx, name, internalid);
    _doctype_builders[doc.idx] = rep;

    return *rep;
}

TypeRef NewConfigBuilder::primitiveType(int32_t type_id) {
    // Special case: T_DOCUMENT refers to the base document type
    if (type_id == DataType::T_DOCUMENT) {
        return TypeRef(_base_document_idx);
    }
    auto it = _primitive_idx_map.find(type_id);
    assert(it != _primitive_idx_map.end() && "Unknown primitive type");
    return TypeRef(it->second);
}

TypeRef NewConfigBuilder::positionType() {
    assert(_position_type_idx != -1 && "Position type not initialized");
    return TypeRef(_position_type_idx);
}

int32_t NewConfigBuilder::getInternalId(TypeRef type_ref) const {
    auto it = _idx_to_internalid_map.find(type_ref.idx);
    int32_t iid = (it != _idx_to_internalid_map.end()) ? it->second : 0;
    fprintf(stderr, "internalid[%d] = %d\n", type_ref.idx, iid);
    return iid;
}

std::string NewConfigBuilder::getTypeName(TypeRef type_ref) const {
    // Search through all doctypes for the type name
    for (const auto& doctype : _config.doctype) {
        // Check if it's the doctype itself
        if (doctype.idx == type_ref.idx) {
            fprintf(stderr, "DEBUG getTypeName: idx=%d is doctype '%s'\n", type_ref.idx, doctype.name.c_str());
            return doctype.name;
        }

        // Check primitives
        for (const auto& pt : doctype.primitivetype) {
            if (pt.idx == type_ref.idx) {
                // Capitalize first letter to match DataType names (Int, String, etc.)
                std::string name = pt.name;
                if (!name.empty()) {
                    name[0] = toupper(name[0]);
                }
                fprintf(stderr, "DEBUG getTypeName: idx=%d is primitive '%s'\n", type_ref.idx, name.c_str());
                return name;
            }
        }

        // Check structs
        for (const auto& st : doctype.structtype) {
            if (st.idx == type_ref.idx) {
                fprintf(stderr, "DEBUG getTypeName: idx=%d is struct '%s'\n", type_ref.idx, st.name.c_str());
                return st.name;
            }
        }

        // Check arrays - recursively build name
        for (const auto& at : doctype.arraytype) {
            if (at.idx == type_ref.idx) {
                std::string elem_name = getTypeName(TypeRef(at.elementtype));
                std::string full_name = "Array<" + elem_name + ">";
                fprintf(stderr, "DEBUG getTypeName: idx=%d is array of '%s' -> '%s'\n",
                        type_ref.idx, elem_name.c_str(), full_name.c_str());
                return full_name;
            }
        }

        // Check maps - recursively build name
        for (const auto& mt : doctype.maptype) {
            if (mt.idx == type_ref.idx) {
                std::string key_name = getTypeName(TypeRef(mt.keytype));
                std::string val_name = getTypeName(TypeRef(mt.valuetype));
                std::string full_name = "Map<" + key_name + "," + val_name + ">";
                fprintf(stderr, "DEBUG getTypeName: idx=%d is map '%s'\n", type_ref.idx, full_name.c_str());
                return full_name;
            }
        }

        // Check wsets - recursively build name
        for (const auto& wt : doctype.wsettype) {
            if (wt.idx == type_ref.idx) {
                std::string elem_name = getTypeName(TypeRef(wt.elementtype));
                std::string full_name = "WeightedSet<" + elem_name + ">";
                fprintf(stderr, "DEBUG getTypeName: idx=%d is wset of '%s' -> '%s'\n",
                        type_ref.idx, elem_name.c_str(), full_name.c_str());
                return full_name;
            }
        }
    }

    fprintf(stderr, "ERROR getTypeName: idx=%d NOT FOUND!\n", type_ref.idx);
    assert(false && "Type not found in getTypeName");
    return "";  // Not found
}

void NewConfigBuilder::registerStructField(TypeRef struct_idx, const std::string& fieldname, TypeRef field_idx) {
    // Find the struct in all doctypes
    BStructT* target_struct = nullptr;
    for (auto& doctype : _config.doctype) {
        for (auto& st : doctype.structtype) {
            if (st.idx == struct_idx.idx) {
                target_struct = &st;
                break;
            }
        }
        if (target_struct) break;
    }

    assert(target_struct && "Struct not found");

    // Add field to struct
    auto& f = target_struct->field.emplace_back();
    f.name = fieldname;
    f.type = field_idx.idx;

    // Compute field ID using field type's internalid
    int32_t field_type_internalid = getInternalId(field_idx);
    f.internalid = createFieldId(fieldname, field_type_internalid);

    fprintf(stderr, "DEBUG: registerStructField(struct_idx=%d, field='%s', field_type_idx=%d) -> field_id=%d\n",
            struct_idx.idx, fieldname.c_str(), field_idx.idx, f.internalid);
}

NewStruct NewConfigBuilder::createStruct(const std::string& name, int32_t doctype_idx) {
    return NewStruct(*this, name, doctype_idx);
}

NewArray NewConfigBuilder::createArray(TypeRef element_type, int32_t doctype_idx) {
    return NewArray(*this, element_type, doctype_idx);
}

NewWset NewConfigBuilder::createWset(TypeRef element_type, int32_t doctype_idx) {
    return NewWset(*this, element_type, doctype_idx);
}

NewMap NewConfigBuilder::createMap(TypeRef key_type, TypeRef value_type, int32_t doctype_idx) {
    return NewMap(*this, key_type, value_type, doctype_idx);
}

void NewConfigBuilder::registerStruct(NewStruct& s, int32_t doctype_idx) {
    // Find the doctype
    BDocType* doc = nullptr;
    for (auto& d : _config.doctype) {
        if (d.idx == doctype_idx) {
            doc = &d;
            break;
        }
    }
    assert(doc && "Document type not found");

    // Check if we already have a struct with this name, fail on collisions
    for (const auto& st : doc->structtype) {
        if (st.name == s._name) {
            fprintf(stderr, "ERROR: Struct '%s' already exists in doctype idx=%d\n",
                    s._name.c_str(), doctype_idx);
            assert(false && "Struct name collision detected");
        }
    }

    // Allocate idx
    s._idx = _next_idx++;
    s._registered = true;
    _idx_to_internalid_map[s._idx] = s._internalid;

    // Add struct to doctype
    auto& st = doc->structtype.emplace_back();
    st.idx = s._idx;
    st.name = s._name;
    st.internalid = s._internalid;

    // Add struct inheritance
    for (auto parent_ref : s._inherits) {
        st.inherits.emplace_back().type = parent_ref.idx;
    }

    // Add fields
    for (const auto& field_pair : s._fields) {
        auto& f = st.field.emplace_back();
        f.name = field_pair.first;
        f.type = field_pair.second.idx;
        // Field ID is computed from field name and field TYPE's internalid
        f.internalid = createFieldId(field_pair.first, getInternalId(field_pair.second));
    }

    // Add tensor fields
    for (const auto& tensor_pair : s._tensor_fields) {
        auto& f = st.field.emplace_back();
        f.name = tensor_pair.first;
        f.internalid = createFieldId(tensor_pair.first, DataType::T_TENSOR);

        // Create tensor type
        auto& tt = doc->tensortype.emplace_back();
        tt.idx = _next_idx++;
        tt.detailedtype = tensor_pair.second;

        f.type = tt.idx;
    }
}

void NewConfigBuilder::registerArray(NewArray& a, int32_t doctype_idx) {
    // Find the doctype
    BDocType* doc = nullptr;
    for (auto& d : _config.doctype) {
        if (d.idx == doctype_idx) {
            doc = &d;
            break;
        }
    }
    assert(doc && "Document type not found");

    // Check if we already have an array with this elementtype to avoid allocating again
    for (const auto& arr : doc->arraytype) {
        if (arr.elementtype == a._element_type.idx) {
            // Reuse existing array type
            a._idx = arr.idx;
            a._registered = true;
            fprintf(stderr, "DEBUG: registerArray - reusing existing array idx=%d for elem_idx=%d\n",
                    arr.idx, a._element_type.idx);
            return;
        }
    }

    // Allocate idx
    a._idx = _next_idx++;
    a._registered = true;

    // Add array to doctype
    auto& arr = doc->arraytype.emplace_back();
    arr.idx = a._idx;
    arr.elementtype = a._element_type.idx;

    // Compute internalid as hash based on element type's NAME
    std::string element_type_name = getTypeName(a._element_type);
    StructDataType tmp_struct(element_type_name);
    ArrayDataType tmp_array(tmp_struct);
    arr.internalid = tmp_array.getId();

    _idx_to_internalid_map[a._idx] = arr.internalid;
    fprintf(stderr, "DEBUG: registerArray(elem_type=%s, elem_idx=%d) -> idx=%d, internalid=%d\n",
            element_type_name.c_str(), a._element_type.idx, arr.idx, arr.internalid);
}

void NewConfigBuilder::registerWset(NewWset& w, int32_t doctype_idx) {
    // Find the doctype
    BDocType* doc = nullptr;
    for (auto& d : _config.doctype) {
        if (d.idx == doctype_idx) {
            doc = &d;
            break;
        }
    }
    assert(doc && "Document type not found");

    // Check if we already have a wset with this elementtype, same createifnonexistent and same removeifzero
    for (const auto& wset : doc->wsettype) {
        if (wset.elementtype == w._element_type.idx &&
            wset.createifnonexistent == w._createifnonexistent &&
            wset.removeifzero == w._removeifzero) {
            // Reuse existing wset type
            w._idx = wset.idx;
            w._registered = true;
            fprintf(stderr, "DEBUG: registerWset - reusing existing wset idx=%d for elem_idx=%d\n",
                    wset.idx, w._element_type.idx);
            return;
        }
    }

    // Allocate idx
    w._idx = _next_idx++;
    w._registered = true;

    // Add wset to doctype
    auto& wset = doc->wsettype.emplace_back();
    wset.idx = w._idx;
    wset.elementtype = w._element_type.idx;

    // Compute internalid as hash based on element type's NAME
    std::string element_type_name = getTypeName(w._element_type);
    StructDataType tmp_struct(element_type_name);
    WeightedSetDataType tmp_wset(tmp_struct, w._createifnonexistent, w._removeifzero);
    wset.internalid = tmp_wset.getId();

    wset.removeifzero = w._removeifzero;
    wset.createifnonexistent = w._createifnonexistent;
    _idx_to_internalid_map[w._idx] = wset.internalid;
    fprintf(stderr, "DEBUG: registerWset(elem_type=%s, elem_idx=%d) -> idx=%d, internalid=%d\n",
            element_type_name.c_str(), w._element_type.idx, wset.idx, wset.internalid);
}

void NewConfigBuilder::registerMap(NewMap& m, int32_t doctype_idx) {
    // Find the doctype
    BDocType* doc = nullptr;
    for (auto& d : _config.doctype) {
        if (d.idx == doctype_idx) {
            doc = &d;
            break;
        }
    }
    assert(doc && "Document type not found");

    // Check if we already have a map with these key and value types to avoid allocating again
    for (const auto& map : doc->maptype) {
        if (map.keytype == m._key_type.idx && map.valuetype == m._value_type.idx) {
            // Reuse existing map type
            m._idx = map.idx;
            m._registered = true;
            fprintf(stderr, "DEBUG: registerMap - reusing existing map idx=%d for key_idx=%d, val_idx=%d\n",
                    map.idx, m._key_type.idx, m._value_type.idx);
            return;
        }
    }

    // Allocate idx
    m._idx = _next_idx++;
    m._registered = true;

    // Add map to doctype
    auto& map = doc->maptype.emplace_back();
    map.idx = m._idx;
    map.keytype = m._key_type.idx;
    map.valuetype = m._value_type.idx;

    // Compute internalid as hash based on key and value types' NAMES
    std::string key_type_name = getTypeName(m._key_type);
    std::string value_type_name = getTypeName(m._value_type);
    StructDataType tmp_key_struct(key_type_name);
    StructDataType tmp_value_struct(value_type_name);
    MapDataType tmp_map(tmp_key_struct, tmp_value_struct);
    map.internalid = tmp_map.getId();

    _idx_to_internalid_map[m._idx] = map.internalid;
    fprintf(stderr, "DEBUG: registerMap(key_type=%s, val_type=%s) -> idx=%d, internalid=%d\n",
            key_type_name.c_str(), value_type_name.c_str(), map.idx, map.internalid);
}

void NewConfigBuilder::registerAnnotationRef(NewAnnotationRef& ar, int32_t doctype_idx) {
    // Find the doctype
    BDocType* doc = nullptr;
    for (auto& d : _config.doctype) {
        if (d.idx == doctype_idx) {
            doc = &d;
            break;
        }
    }
    assert(doc && "Document type not found");

    // Find the annotation type name
    std::string annotation_name;
    for (const auto& ann : doc->annotationtype) {
        if (ann.idx == ar._annotation_idx) {
            annotation_name = ann.name;
            break;
        }
    }
    assert(!annotation_name.empty() && "Annotation type not found");

    // Allocate idx
    ar._idx = _next_idx++;
    ar._registered = true;

    // Add annotation ref to doctype
    auto& aref = doc->annotationref.emplace_back();
    aref.idx = ar._idx;
    aref.annotationtype = ar._annotation_idx;

    std::string aref_type_name = "annotationreference<" + annotation_name + ">";
    aref.internalid = hashId(aref_type_name);
    _idx_to_internalid_map[ar._idx] = aref.internalid;
}

void NewConfigBuilder::finalizeDocType(NewDocTypeRep& doc) {
    // Find the doctype config
    BDocType* doc_config = nullptr;
    for (auto& d : _config.doctype) {
        if (d.idx == doc._idx) {
            doc_config = &d;
            break;
        }
    }
    assert(doc_config && "Document type not found");

    // Add additional inheritance
    for (auto parent_idx : doc._inherits) {
        // Check if not already present
        bool found = false;
        for (const auto& inh : doc_config->inherits) {
            if (inh.idx == parent_idx) {
                found = true;
                break;
            }
        }
        if (!found) {
            doc_config->inherits.emplace_back().idx = parent_idx;
        }
    }

    // Add annotations (skip those already added)
    for (const auto& ann : doc._annotations) {
        bool already_added = false;
        for (const auto& existing : doc_config->annotationtype) {
            if (existing.idx == ann.idx) {
                already_added = true;
                break;
            }
        }
        if (!already_added) {
            auto& a = doc_config->annotationtype.emplace_back();
            a.idx = ann.idx;
            a.name = ann.name;
            a.internalid = ann.internalid;
            if (ann.datatype_idx >= 0) {
                a.datatype = ann.datatype_idx;
            }
        }
    }

    // Add imported fields
    for (const auto& field_name : doc._imported_fields) {
        doc_config->importedfield.emplace_back().name = field_name;
    }

    // Add field sets
    for (const auto& fs_pair : doc._field_sets) {
        auto& fields = doc_config->fieldsets[fs_pair.first].fields;
        fields.clear();
        for (const auto& field_name : fs_pair.second) {
            fields.push_back(field_name);
        }
    }
}

}  // namespace document::new_config_builder
