// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documenttyperepo.h"

#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/document/datatype/annotationreferencedatatype.h>
#include <vespa/document/datatype/arraydatatype.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/mapdatatype.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/document/datatype/urldatatype.h>
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/document/datatype/referencedatatype.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/document/config/config-documenttypes.h>
#include <fstream>
#include <cassert>
#include <set>

#include <vespa/log/log.h>
LOG_SETUP(".documenttyperepo");

using std::unique_ptr;
using std::fstream;
using std::make_pair;
using std::pair;
using std::vector;
using vespalib::IllegalArgumentException;
using vespalib::hash_map;
using vespalib::make_string;
using vespalib::string;
using vespalib::stringref;

namespace document {

namespace internal {

using DocumentTypeMapT = vespalib::hash_map<int32_t, DataTypeRepo *>;

class DocumentTypeMap : public DocumentTypeMapT
{
public:
    using DocumentTypeMapT::DocumentTypeMapT;
};

}

using DocumentTypeMap = internal::DocumentTypeMap;

namespace {
template <typename Container>
void DeleteContent(Container &c) {
    for (auto ptr : c) {
        delete ptr;
    }
}
template <typename Map>
void DeleteMapContent(Map &m) {
    for (auto & entry : m) {
        delete entry.second;
    }
}

// A collection of data types.
class Repo {
    vector<const DataType *> _owned_types;
    hash_map<int32_t, const DataType *> _types;
    hash_map<string, const DataType *> _tensorTypes;
    hash_map<string, const DataType *> _name_map;

public:
    ~Repo() { DeleteContent(_owned_types); }

    void inherit(const Repo &parent);
    bool addDataType(const DataType &type);
    template <typename T> const DataType * addDataType(unique_ptr<T> type);

    const DataType &addTensorType(const string &spec);
    const DataType *lookup(int32_t id) const;
    const DataType *lookup(stringref name) const;
    const DataType &findOrThrow(int32_t id) const;
    const DataType &findOrThrowOrCreate(int32_t id, const string &detailedType);
};

void Repo::inherit(const Repo &parent) {
    _types.insert(parent._types.begin(), parent._types.end());
    _tensorTypes.insert(parent._tensorTypes.begin(), parent._tensorTypes.end());
    _name_map.insert(parent._name_map.begin(), parent._name_map.end());
}

// Returns true if a reference to type is stored.
bool Repo::addDataType(const DataType &type) {
    const DataType *& data_type = _types[type.getId()];
    if (data_type) {
        if (data_type->equals(type) && (data_type->getName() == type.getName())) {
            return false;  // Redefinition of identical type is ok.
        }
        throw IllegalArgumentException(
                make_string("Redefinition of data type %d, \"%s\". Previously defined as \"%s\".",
                            type.getId(), type.getName().c_str(), data_type->getName().c_str()));
    }
    const DataType *& data_type_by_name = _name_map[type.getName()];
    if (data_type_by_name) {
        throw IllegalArgumentException(
                make_string("Redefinition of data type \"%s\", with id %d. Previously defined with id %d.",
                            type.getName().c_str(), type.getId(), data_type_by_name->getId()));
    }
    data_type = &type;
    data_type_by_name = &type;
    LOG(spam, "Added data type to repo: %s [%d]", type.getName().c_str(), type.getId());
    return true;
}

template <typename T>
const DataType* Repo::addDataType(unique_ptr<T> type) {
    int id = type->getId();
    if (addDataType(*type)) {
        _owned_types.push_back(type.release());
    }
    return _types[id];
}


const DataType &
Repo::addTensorType(const string &spec)
{
    auto type = TensorDataType::fromSpec(spec);
    auto insres = _tensorTypes.insert(std::make_pair(spec, type.get()));
    if (insres.second) {
        _owned_types.push_back(type.release());
    }
    return *insres.first->second;
}

template <typename Map>
typename Map::mapped_type FindPtr(const Map &m, typename Map::key_type key) {
    typename Map::const_iterator it = m.find(key);
    if (it != m.end()) {
        return it->second;
    }
    return typename Map::mapped_type();
}

const DataType *Repo::lookup(int32_t id) const {
    return FindPtr(_types, id);
}

const DataType *Repo::lookup(stringref n) const {
    return FindPtr(_name_map, n);
}

const DataType &Repo::findOrThrow(int32_t id) const {
    const DataType *type = lookup(id);
    if (type) {
        return *type;
    }
    throw IllegalArgumentException(make_string("Unknown datatype %d", id));
}

const DataType &
Repo::findOrThrowOrCreate(int32_t id, const string &detailedType)
{
    if (id != DataType::T_TENSOR) {
        return findOrThrow(id);
    }
    return addTensorType(detailedType);
}

class AnnotationTypeRepo {
    vector<const AnnotationType *> _owned_types;
    hash_map<int32_t, AnnotationType *> _annotation_types;

public:
    ~AnnotationTypeRepo() { DeleteContent(_owned_types); }

    void inherit(const AnnotationTypeRepo &parent);
    void addAnnotationType(AnnotationType::UP annotation_type);
    void setAnnotationDataType(int32_t id, const DataType &datatype);

    const AnnotationType *lookup(int32_t id) const;
};

void AnnotationTypeRepo::inherit(const AnnotationTypeRepo &parent) {
    _annotation_types.insert(parent._annotation_types.begin(), parent._annotation_types.end());
}

void AnnotationTypeRepo::addAnnotationType(AnnotationType::UP type) {
    AnnotationType *& a_type = _annotation_types[type->getId()];
    if (a_type) {
        if (*type != *a_type) {
            throw IllegalArgumentException(
                make_string("Redefinition of annotation type %d, \"%s\". Previously defined as \"%s\".",
                            type->getId(), type->getName().c_str(), a_type->getName().c_str()));
        }
    } else {
        a_type = type.get();
        _owned_types.push_back(type.release());
    }
}

void AnnotationTypeRepo::setAnnotationDataType(int32_t id, const DataType &d) {
    AnnotationType *annotation_type = _annotation_types[id];
    assert(annotation_type);
    if (!annotation_type->getDataType()) {
        annotation_type->setDataType(d);
    } else if ( ! annotation_type->getDataType()->equals(d)) {
        throw IllegalArgumentException(
            make_string("Redefinition of annotation type %d, \"%s\" = '%s'. Previously defined as '%s'.",
                        annotation_type->getId(), annotation_type->getName().c_str(),
                        annotation_type->getDataType()->toString().c_str(), d.toString().c_str()));
    }
}

const AnnotationType *AnnotationTypeRepo::lookup(int32_t id) const {
    return FindPtr(_annotation_types, id);
}

}  // namespace

// Combination of a DocumentType and a collection of DataTypes
// associated with it.
struct DataTypeRepo {
    typedef unique_ptr<DataTypeRepo> UP;

    DocumentType *doc_type;
    Repo repo;
    AnnotationTypeRepo annotations;

    DataTypeRepo() : doc_type(nullptr) {}
    ~DataTypeRepo() { delete doc_type; }
};

namespace {


void addAnnotationType(const DocumenttypesConfig::Documenttype::Annotationtype &type, AnnotationTypeRepo &annotations)
{
    auto a = std::make_unique<AnnotationType>(type.id, type.name);
    annotations.addAnnotationType(std::move(a));
}

void addAnnotationTypes(const vector<DocumenttypesConfig::Documenttype::Annotationtype> &types,
                        AnnotationTypeRepo &annotations) {
    for (size_t i = 0; i < types.size(); ++i) {
        addAnnotationType(types[i], annotations);
    }
}

void setAnnotationDataTypes(const vector<DocumenttypesConfig::Documenttype::Annotationtype> &types,
                            AnnotationTypeRepo &annotations, const Repo &repo)
{
    for (size_t i = 0; i < types.size(); ++i) {
        if (types[i].datatype == -1) {
            continue;
        }
        const DataType &datatype = repo.findOrThrow(types[i].datatype);
        annotations.setAnnotationDataType(types[i].id, datatype);
    }
}

typedef DocumenttypesConfig::Documenttype::Datatype Datatype;

void addField(const Datatype::Sstruct::Field &field, Repo &repo, StructDataType &struct_type)
{
    LOG(spam, "Adding field %s to %s",
        field.name.c_str(), struct_type.getName().c_str());
    const DataType &field_type = repo.findOrThrowOrCreate(field.datatype, field.detailedtype);
    struct_type.addField(Field(field.name, field.id, field_type));
}

void addStruct(int32_t id, const Datatype::Sstruct &s, Repo &repo) {
    // TODO(thomasg): Ugly stuff, remove when we fix config.
    std::string name = s.name;
    std::string::size_type pos = name.rfind(".body");
    bool useUglyStructHack = false;
    if (pos != std::string::npos) {
        name = name.substr(0, pos) + ".header";
        // If header already exists, we'll just reuse its struct verbatim so no
        // need to set new ID here.
        useUglyStructHack = true;
    } else if (name.rfind(".header") != std::string::npos) {
        const DataType *existing = repo.lookup(name);
        if (existing) {
            LOG(spam, "Reusing id %u from body struct since its fields have already been inserted", existing->getId());
            id = existing->getId();
        }
        useUglyStructHack = true;
    }

    LOG(debug, "Adding struct type %s (%s) with id %u", s.name.c_str(), name.c_str(), id);

    StructDataType::UP struct_type_ap;
    StructDataType *struct_type;
    const DataType *existing = repo.lookup(name);
    if (useUglyStructHack && existing) {
        LOG(spam, "Type %s already existed", name.c_str());
        const StructDataType& cdt = dynamic_cast<const StructDataType&>(*existing);
        struct_type = const_cast<StructDataType*>(&cdt);
    } else {
        const DataType *existing_retry = repo.lookup(id);
        LOG(spam, "Type %s not found, adding it", name.c_str());
        struct_type_ap = std::make_unique<StructDataType>(name, id);
        struct_type = struct_type_ap.get();
        repo.addDataType(std::move(struct_type_ap));
        if (existing_retry) {
            return;
        }
    }

    for (size_t i = 0; i < s.field.size(); ++i) {
        addField(s.field[i], repo, *struct_type);
    }
}

void addArray(int32_t id, const Datatype::Array &a, Repo &repo) {
    const DataType &nested = repo.findOrThrow(a.element.id);
    repo.addDataType(std::make_unique<ArrayDataType>(nested, id));
}

void addWset(int32_t id, const Datatype::Wset &w, Repo &repo) {
    const DataType &key = repo.findOrThrow(w.key.id);
    repo.addDataType(std::make_unique<WeightedSetDataType>(key, w.createifnonexistent, w.removeifzero, id));
}

void addMap(int32_t id, const Datatype::Map &m, Repo &repo) {
    const DataType &key = repo.findOrThrow(m.key.id);
    const DataType &value = repo.findOrThrow(m.value.id);
    repo.addDataType(std::make_unique<MapDataType>(key, value, id));
}

void addAnnotationRef(int32_t id, const Datatype::Annotationref &a, Repo &r, const AnnotationTypeRepo &annotations) {
    const AnnotationType *type = annotations.lookup(a.annotation.id);
    if (!type) {
        throw IllegalArgumentException(make_string("Unknown AnnotationType %d", a.annotation.id));
    }
    r.addDataType(std::make_unique<AnnotationReferenceDataType>(*type, id));
}

void addDataType(const Datatype &type, Repo &repo, const AnnotationTypeRepo &a_repo) {
    switch (type.type) {
    case Datatype::Type::STRUCT:
        return addStruct(type.id, type.sstruct, repo);
    case Datatype::Type::ARRAY:
        return addArray(type.id, type.array, repo);
    case Datatype::Type::WSET:
        return addWset(type.id, type.wset, repo);
    case Datatype::Type::MAP:
        return addMap(type.id, type.map, repo);
    case Datatype::Type::ANNOTATIONREF:
        return addAnnotationRef(type.id, type.annotationref, repo, a_repo);
    default:
        throw IllegalArgumentException(make_string("Unknown datatype type %d for id %d", static_cast<int>(type.type), type.id));
    }
}

void addDataTypes(const vector<Datatype> &types, Repo &repo, const AnnotationTypeRepo &a_repo) {
    for (size_t i = 0; i < types.size(); ++i) {
        addDataType(types[i], repo, a_repo);
    }
}

void addDocumentTypes(const DocumentTypeMap &type_map, Repo &repo) {
    for (const auto & entry : type_map) {
        repo.addDataType(*entry.second->doc_type);
    }
}

const DocumentType *
addDefaultDocument(DocumentTypeMap &type_map) {
    auto data_types = std::make_unique<DataTypeRepo>();
    vector<const DataType *> default_types = DataType::getDefaultDataTypes();
    for (size_t i = 0; i < default_types.size(); ++i) {
        data_types->repo.addDataType(*default_types[i]);
    }
    data_types->repo.addDataType(UrlDataType::getInstance());
    data_types->repo.addDataType(PositionDataType::getInstance());
    data_types->doc_type = new DocumentType("document", 8);

    vector<const AnnotationType *> annotation_types(AnnotationType::getDefaultAnnotationTypes());
    for(size_t i(0); i < annotation_types.size(); ++i) {
        data_types->annotations.addAnnotationType(std::make_unique<AnnotationType>(*annotation_types[i]));
    }

    uint32_t typeId = data_types->doc_type->getId();
    const DocumentType * docType = data_types->doc_type;
    type_map[typeId] = data_types.release();
    return docType;
}

const DataTypeRepo &lookupRepo(int32_t id, const DocumentTypeMap &type_map) {
    DocumentTypeMap::const_iterator it = type_map.find(id);
    if (it == type_map.end()) {
        throw IllegalArgumentException(make_string("Unable to find document type %d.", id));
    }
    return *it->second;
}

void inheritDataTypes(const vector<DocumenttypesConfig::Documenttype::Inherits> &base_types,
                      const DocumentTypeMap &type_map, Repo &repo) {
    repo.inherit(lookupRepo(DataType::T_DOCUMENT, type_map).repo);
    for (size_t i = 0; i < base_types.size(); ++i) {
        repo.inherit(lookupRepo(base_types[i].id, type_map).repo);
    }
}

void inheritAnnotationTypes(const vector<DocumenttypesConfig::Documenttype::Inherits> &base_types,
                            const DocumentTypeMap &type_map, AnnotationTypeRepo &repo) {
    repo.inherit(lookupRepo(DataType::T_DOCUMENT, type_map).annotations);
    for (size_t i = 0; i < base_types.size(); ++i) {
        repo.inherit(lookupRepo(base_types[i].id, type_map).annotations);
    }
}

void inheritDocumentTypes(const vector<DocumenttypesConfig::Documenttype::Inherits> &base_types,
                          const DocumentTypeMap &type_map, DocumentType &doc_type) {
    for (size_t i = 0; i < base_types.size(); ++i) {
        const DataTypeRepo &parent = lookupRepo(base_types[i].id, type_map);
        doc_type.inherit(*parent.doc_type);
    }
}

DataTypeRepo::UP
makeDataTypeRepo(const DocumentType &doc_type, const DocumentTypeMap &type_map) {
    auto data_types = std::make_unique<DataTypeRepo>();
    data_types->repo.inherit(lookupRepo(DataType::T_DOCUMENT, type_map).repo);
    data_types->annotations.inherit(lookupRepo(DataType::T_DOCUMENT, type_map).annotations);
    data_types->doc_type = new DocumentType(doc_type);
    return data_types;
}

void addFieldSet(const DocumenttypesConfig::Documenttype::FieldsetsMap & fsv, DocumentType &doc_type) {
    for (const auto & entry : fsv) {
        const DocumenttypesConfig::Documenttype::Fieldsets & fs(entry.second);
        DocumentType::FieldSet::Fields fields;
        for (const auto& f : fs.fields) {
            fields.insert(f);
        }
        doc_type.addFieldSet(entry.first, std::move(fields));
    }
}

void addReferenceTypes(const vector<DocumenttypesConfig::Documenttype::Referencetype> &ref_types,
                       Repo& data_type_repo, const DocumentTypeMap& doc_type_map)
{
    for (const auto& ref_type : ref_types) {
        const auto* target_doc_type = lookupRepo(ref_type.targetTypeId, doc_type_map).doc_type;
        data_type_repo.addDataType(std::make_unique<ReferenceDataType>(*target_doc_type, ref_type.id));
    }
}

void add_imported_fields(const DocumenttypesConfig::Documenttype::ImportedfieldVector& imported_fields,
                         DocumentType& doc_type)
{
    for (const auto& imported : imported_fields) {
        doc_type.add_imported_field_name(imported.name);
    }
}

void configureDataTypeRepo(const DocumenttypesConfig::Documenttype &doc_type, DocumentTypeMap &type_map) {
    DataTypeRepo *data_types = type_map[doc_type.id];
    inheritAnnotationTypes(doc_type.inherits, type_map, data_types->annotations);
    addAnnotationTypes(doc_type.annotationtype, data_types->annotations);
    inheritDataTypes(doc_type.inherits, type_map, data_types->repo);
    addReferenceTypes(doc_type.referencetype, data_types->repo, type_map);
    addDataTypes(doc_type.datatype, data_types->repo, data_types->annotations);
    setAnnotationDataTypes(doc_type.annotationtype, data_types->annotations, data_types->repo);
    inheritDocumentTypes(doc_type.inherits, type_map, *data_types->doc_type);
    addFieldSet(doc_type.fieldsets, *data_types->doc_type);
    add_imported_fields(doc_type.importedfield, *data_types->doc_type);
}

void addDataTypeRepo(DataTypeRepo::UP data_types, DocumentTypeMap &doc_types) {
    DataTypeRepo *& p = doc_types[data_types->doc_type->getId()];
    if (p) {
        LOG(warning, "Type repo already exists for id %d.", data_types->doc_type->getId());
        throw IllegalArgumentException("Trying to redefine a document type.");
    }
    p = data_types.release();
}

DataTypeRepo::UP makeSkeletonDataTypeRepo(const DocumenttypesConfig::Documenttype &type) {
    auto data_types = std::make_unique<DataTypeRepo>();
    auto type_ap = std::make_unique<StructDataType>(type.name + ".header", type.headerstruct);
    data_types->doc_type = new DocumentType(type.name, type.id, *type_ap);
    data_types->repo.addDataType(std::move(type_ap));
    return data_types;
}

void createAllDocumentTypes(const DocumenttypesConfig::DocumenttypeVector &t, DocumentTypeMap &type_map) {
    for (size_t i = 0; i < t.size(); ++i) {
        addDataTypeRepo(makeSkeletonDataTypeRepo(t[i]), type_map);
    }
}

void addAllDocumentTypesToRepos(DocumentTypeMap &type_map) {
    for (const auto & entry : type_map) {
        addDocumentTypes(type_map, entry.second->repo);
    }
}

void configureAllRepos(const DocumenttypesConfig::DocumenttypeVector &t, DocumentTypeMap &type_map) {
    for (size_t i = 0; i < t.size(); ++i) {
        configureDataTypeRepo(t[i], type_map);
    }
}

using DataTypesByIdx = hash_map<int, const DataType *>;
using StructTypesByIdx = hash_map<int, StructDataType *>;

const StructDataType * performStructInherit(int idx,
                                            const DocumenttypesConfig::DoctypeVector &t,
                                            const StructTypesByIdx &structs)
{
    for (const auto & docT : t) {
        for (const auto & structT : docT.structtype) {
            if (idx == structT.idx) {
                StructDataType *st = structs[idx];
                for (const auto & inheritD : structT.inherits) {
                    const auto * parent = performStructInherit(inheritD.type, t, structs);
                    if (parent == nullptr) {
                        LOG(error, "Missing parent type [idx %d] for struct %s",
                            inheritD.type, structT.name.c_str());
                        throw IllegalArgumentException("missing parent type");
                    }
                    LOG_ASSERT(st != nullptr);
                    for (const auto & field : parent->getFieldSet()) {
                        st->addInheritedField(*field);
                    }
                }
                return st;
            }
        }
    }
    return nullptr;
}

void configureDocTypes(const DocumenttypesConfig::DoctypeVector &t, DocumentTypeMap &type_map) {
    hash_map<int, StructDataType *> structs_by_idx;
    hash_map<int, AnnotationType *> annotations_by_idx;
    DataTypesByIdx types_by_idx;
    std::set<int> needed_indexes;
    for (const auto & docT : t) {
        for (const auto & structT : docT.structtype) {
            for (const auto & fieldD : structT.field) {
                LOG(debug, "doc %s struct %s field %s needs [idx %d]",
                    docT.name.c_str(), structT.name.c_str(), fieldD.name.c_str(), fieldD.type);
                needed_indexes.insert(fieldD.type);
            }
        }
        for (const auto & arrT : docT.arraytype) {
            LOG(debug, "doc %s array needs [idx %d]", docT.name.c_str(),arrT.elementtype);
            needed_indexes.insert(arrT.elementtype);
        }
        for (const auto & wsetT : docT.wsettype) {
            LOG(debug, "doc %s wset needs [idx %d]", docT.name.c_str(), wsetT.elementtype);
            needed_indexes.insert(wsetT.elementtype);
        }
        for (const auto & mapT : docT.maptype) {
            LOG(debug, "doc %s wset needs [idx %d] and [idx %d]",
                docT.name.c_str(), mapT.keytype, mapT.valuetype);
            needed_indexes.insert(mapT.keytype);
            needed_indexes.insert(mapT.valuetype);
        }
        for (const auto & annT: docT.annotationtype) {
            if (annT.datatype != -1) {
                LOG(debug, "doc %s ann needs datatype [idx %d]", docT.name.c_str(), annT.datatype);
                needed_indexes.insert(annT.datatype);
            }
            for (const auto & inheritD : annT.inherits) {
                LOG(debug, "doc %s ann needs parent [idx %d]", docT.name.c_str(), inheritD.idx);
                needed_indexes.insert(inheritD.idx);
            }
        }
        for (const auto & aRef : docT.annotationref) {
            LOG(debug, "doc %s ann ref needs annotation [idx %d]", docT.name.c_str(), aRef.annotationtype);
            needed_indexes.insert(aRef.annotationtype);
        }
        for (const auto & refT : docT.documentref) {
            LOG(debug, "doc %s doc ref needs target [idx %d]", docT.name.c_str(), refT.targettype);
            needed_indexes.insert(refT.targettype);
        }
    }
    for (const auto & docT : t) {
        DataTypeRepo * dtr = FindPtr(type_map, docT.internalid);
        if (dtr == nullptr) {
            dtr = new DataTypeRepo();
            type_map[docT.internalid] = dtr;
            LOG(debug, "new doct : %s [%d]", docT.name.c_str(), docT.internalid);
        } else {
            LOG(debug, "old doct : %s [%d]", docT.name.c_str(), docT.internalid);
        }
        auto & repo = dtr->repo;
        for (const auto & structT : docT.structtype) {
            if (const auto * dt = repo.lookup(structT.internalid)) {
                LOG(debug, "already has %s [%d], wanted to add %s [%d]",
                    dt->getName().c_str(), dt->getId(),
                    structT.name.c_str(), structT.internalid);
                types_by_idx[structT.idx] = dt;
                needed_indexes.erase(structT.idx);
                continue;
            }
            auto st = std::make_unique<StructDataType>(structT.name, structT.internalid);
            needed_indexes.erase(structT.idx);
            structs_by_idx[structT.idx] = st.get();
            types_by_idx[structT.idx] = repo.addDataType(std::move(st));
            assert(types_by_idx[structT.idx] == structs_by_idx[structT.idx]);
        }
        if (dtr->doc_type == nullptr) {
            const auto * contentStruct = types_by_idx[docT.contentstruct];
            const auto * fields = dynamic_cast<const StructDataType *>(contentStruct);
            if (fields != nullptr) {
                dtr->doc_type = new DocumentType(docT.name, docT.internalid, *fields);
            } else {
                LOG(error, "Missing content struct for '%s' (idx %d not found)",
                    docT.name.c_str(), docT.contentstruct);
                throw IllegalArgumentException("missing content struct");
            }
            for (const auto & inheritD : docT.inherits) {
                const DataType *dt = types_by_idx[inheritD.idx];
                if (dt == nullptr) {
                    LOG(error, "parent datatype [idx %d] missing for document %s",
                        inheritD.idx, docT.name.c_str());
                    throw IllegalArgumentException("Unable to find document for inheritance");
                    continue;
                }
                DataTypeRepo * parentRepo = FindPtr(type_map, dt->getId());
                if (parentRepo == nullptr) {
                    LOG(error, "parent repo [id %d] missing for document %s",
                        dt->getId(), docT.name.c_str());
                    throw IllegalArgumentException("missing parent repo");
                    continue;
                }
                dtr->annotations.inherit(parentRepo->annotations);
            }
        }
        types_by_idx[docT.idx] = dtr->doc_type;
        needed_indexes.erase(docT.idx);
        for (const auto & primT : docT.primitivetype) {
            string name = primT.name;
            const DataType *dt = repo.lookup(name);
            if (dt == nullptr) {
                if (name == "float16") {
                    name = "float";
                }
                name[0] = (name[0] & 0x5F);
                dt = repo.lookup(name);
            }
            if (dt == nullptr) {
                LOG(warning, "Missing primitive type '%s'", primT.name.c_str());
            } else {
                types_by_idx[primT.idx] = dt;
                needed_indexes.erase(primT.idx);
            }
        }
        for (const auto & tensorT : docT.tensortype) {
            const DataType & tt = repo.addTensorType(tensorT.detailedtype);
            types_by_idx[tensorT.idx] = &tt;
            needed_indexes.erase(tensorT.idx);
        }
        for (const auto & annT: docT.annotationtype) {
            auto at = std::make_unique<AnnotationType>(annT.internalid, annT.name);
            annotations_by_idx[annT.idx] = at.get();
            needed_indexes.erase(annT.idx);
            dtr->annotations.addAnnotationType(std::move(at));
        }
    }
    for (const auto & docT : t) {
        DataTypeRepo * dtr = FindPtr(type_map, docT.internalid);
        LOG_ASSERT(dtr != nullptr);
        auto & repo = dtr->repo;
        for (const auto & refT : docT.documentref) {
            if (types_by_idx[refT.idx] != nullptr) {
                continue;
            }
            const auto * target = dynamic_cast<const DocumentType *>(types_by_idx[refT.targettype]);
            if (target == nullptr) {
                LOG(error, "Missing target document type for reference (idx %d)", refT.targettype);
                throw IllegalArgumentException("missing target type");
            } else {
                auto rt = std::make_unique<ReferenceDataType>(*target, refT.internalid);
                needed_indexes.erase(refT.idx);
                types_by_idx[refT.idx] = repo.addDataType(std::move(rt));
            }
        }
        for (const auto & aRef : docT.annotationref) {
            const AnnotationType * target = annotations_by_idx[aRef.annotationtype];
            if (target == nullptr) {
                LOG(error, "Missing annotation type [idx %d] for annotationref",
                    aRef.annotationtype);
                throw IllegalArgumentException("missing annotation type");
            } else {
                auto ar = std::make_unique<AnnotationReferenceDataType>(*target, aRef.internalid);
                needed_indexes.erase(aRef.idx);
                types_by_idx[aRef.idx] = repo.addDataType(std::move(ar));
            }
        }
    }
    while (needed_indexes.size() > 0) {
        size_t missing_cnt = needed_indexes.size();
        bool missing = false;
        for (const auto & docT : t) {
            DataTypeRepo * dtr = FindPtr(type_map, docT.internalid);
            LOG_ASSERT(dtr != nullptr);
            auto & repo = dtr->repo;
            for (const auto & arrT : docT.arraytype) {
                if (types_by_idx[arrT.idx] != nullptr) {
                    continue; // OK already
                }
                const DataType * nested = types_by_idx[arrT.elementtype];
                if (nested == nullptr) {
                    missing = true;
                } else {
                    auto at = std::make_unique<ArrayDataType>(*nested, arrT.internalid);
                    needed_indexes.erase(arrT.idx);
                    types_by_idx[arrT.idx] = repo.addDataType(std::move(at));
                }
            }
            for (const auto & mapT : docT.maptype) {
                if (types_by_idx[mapT.idx] != nullptr) {
                    continue; // OK already
                }
                const DataType * kt = types_by_idx[mapT.keytype];
                const DataType * vt = types_by_idx[mapT.valuetype];
                if (kt == nullptr || vt == nullptr) {
                    missing = true;
                } else {
                    auto mt = std::make_unique<MapDataType>(*kt, *vt, mapT.internalid);
                    needed_indexes.erase(mapT.idx);
                    types_by_idx[mapT.idx] = repo.addDataType(std::move(mt));
                }
            }
            for (const auto & wsetT : docT.wsettype) {
                if (types_by_idx[wsetT.idx] != nullptr) {
                    continue; // OK already
                }
                const DataType * nested = types_by_idx[wsetT.elementtype];
                if (nested == nullptr) {
                    missing = true;
                } else {
                    auto wt = std::make_unique<WeightedSetDataType>(*nested,
                                                                    wsetT.createifnonexistent, wsetT.removeifzero,
                                                                    wsetT.internalid);
                    needed_indexes.erase(wsetT.idx);
                    types_by_idx[wsetT.idx] = repo.addDataType(std::move(wt));
                }
            }
        }
        if (missing) {
            LOG(debug, "retry complex types, %zd missing", needed_indexes.size());
        }
        if (needed_indexes.size() == missing_cnt) {
            for (int idx : needed_indexes) {
                LOG(error, "no progress, datatype [idx %d] still missing", idx);
                throw IllegalArgumentException("no progress");
            }
            break;
        }
    }
    for (const auto & docT : t) {
        for (const auto & structT : docT.structtype) {
            auto st = structs_by_idx[structT.idx];
            if (st == nullptr) continue;
            for (const auto & fieldD : structT.field) {
                const DataType *ft = types_by_idx[fieldD.type];
                if (ft == nullptr) {
                    LOG(error, "Missing type [idx %d] for struct %s field %s",
                        fieldD.type, structT.name.c_str(), fieldD.name.c_str());
                    throw IllegalArgumentException("missing datatype");
                } else {
                    st->addField(Field(fieldD.name, fieldD.internalid, *ft));
                }
            }
        }
    }
    for (const auto & docT : t) {
        for (const auto & structT : docT.structtype) {
            performStructInherit(structT.idx, t, structs_by_idx);
        }
    }
    for (const auto & docT : t) {
        for (const auto & annT: docT.annotationtype) {
            if (annT.datatype != -1) {
                const DataType * dt = types_by_idx[annT.datatype];
                if (dt == nullptr) {
                    LOG(error, "Missing datatype [idx %d] for annotation type %s",
                        annT.datatype, annT.name.c_str());
                    throw IllegalArgumentException("missing datatype");
                } else {
                    AnnotationType * at = annotations_by_idx[annT.idx];
                    at->setDataType(*dt);
                }
            }
            for (const auto & inheritD : annT.inherits) {
                AnnotationType * at = annotations_by_idx[annT.idx];
                LOG_ASSERT(at != nullptr);
                const AnnotationType * parent = annotations_by_idx[inheritD.idx];
                if (parent == nullptr) {
                    LOG(error, "missing parent [idx %d] for annotation %s",
                        inheritD.idx, annT.name.c_str());
                    throw IllegalArgumentException("missing parent");
                }
            }
        }
        DataTypeRepo * dtr = FindPtr(type_map, docT.internalid);
        LOG_ASSERT(dtr != nullptr);
        for (const auto & importD : docT.importedfield) {
            dtr->doc_type->add_imported_field_name(importD.name);
        }
        for (const auto & entry : docT.fieldsets) {
            DocumentType::FieldSet::Fields fields;
            for (const auto& f : entry.second.fields) {
                fields.insert(f);
            }
            dtr->doc_type->addFieldSet(entry.first, fields);
        }
        for (const auto & inheritD : docT.inherits) {
            const DataType *dt = types_by_idx[inheritD.idx];
            const DocumentType * parent = dynamic_cast<const DocumentType *>(dt);
            if (parent == nullptr) {
                LOG(error, "missing parent type [idx %d] for document %s",
                    inheritD.idx, docT.name.c_str());
                throw IllegalArgumentException("missing parent type");
            } else {
                dtr->doc_type->inherit(*parent);
            }
        }
    }
}

}  // namespace

DocumentTypeRepo::DocumentTypeRepo() :
    _doc_types(std::make_unique<internal::DocumentTypeMap>()),
    _default(addDefaultDocument(*_doc_types))
{
}

DocumentTypeRepo::DocumentTypeRepo(const DocumentType & type) :
    _doc_types(std::make_unique<internal::DocumentTypeMap>()),
    _default(addDefaultDocument(*_doc_types))
{
    try {
        addDataTypeRepo(makeDataTypeRepo(type, *_doc_types), *_doc_types);
    } catch (...) {
        DeleteMapContent(*_doc_types);
        throw;
    }
}

DocumentTypeRepo::DocumentTypeRepo(const DocumenttypesConfig &config) :
    _doc_types(std::make_unique<internal::DocumentTypeMap>()),
    _default(addDefaultDocument(*_doc_types))
{
    try {
        if (config.documenttype.empty() && ! config.doctype.empty()) {
            configureDocTypes(config.doctype, *_doc_types);
        } else {
        createAllDocumentTypes(config.documenttype, *_doc_types);
        addAllDocumentTypesToRepos(*_doc_types);
        configureAllRepos(config.documenttype, *_doc_types);
        }
    } catch (...) {
        DeleteMapContent(*_doc_types);
        throw;
    }
}

DocumentTypeRepo::~DocumentTypeRepo() {
    DeleteMapContent(*_doc_types);
}

const DocumentType *
DocumentTypeRepo::getDocumentType(int32_t type_id) const noexcept {
    const DataTypeRepo *repo = FindPtr(*_doc_types, type_id);
    return repo ? repo->doc_type : nullptr;
}

const DocumentType *
DocumentTypeRepo::getDocumentType(stringref name) const noexcept {
    DocumentTypeMap::const_iterator it = _doc_types->find(DocumentType::createId(name));

    if (it != _doc_types->end() && it->second->doc_type->getName() == name) {
        return it->second->doc_type;
    }
    for (it = _doc_types->begin(); it != _doc_types->end(); ++it) {
        if (it->second->doc_type->getName() == name) {
            return it->second->doc_type;
        }
    }
    return nullptr;
}

const DataType *
DocumentTypeRepo::getDataType(const DocumentType &doc_type, int32_t id) const {
    const DataTypeRepo *dt_repo = FindPtr(*_doc_types, doc_type.getId());
    return dt_repo ? dt_repo->repo.lookup(id) : nullptr;
}

const DataType *
DocumentTypeRepo::getDataType(const DocumentType &doc_type, stringref name) const {
    const DataTypeRepo *dt_repo = FindPtr(*_doc_types, doc_type.getId());
    return dt_repo ? dt_repo->repo.lookup(name) : nullptr;
}

const AnnotationType *
DocumentTypeRepo::getAnnotationType(const DocumentType &doc_type, int32_t id) const {
    const DataTypeRepo *dt_repo = FindPtr(*_doc_types, doc_type.getId());
    return dt_repo ? dt_repo->annotations.lookup(id) : nullptr;
}

void
DocumentTypeRepo::forEachDocumentType(std::function<void(const DocumentType &)> handler) const {
    for (const auto & entry : *_doc_types) {
        handler(*entry.second->doc_type);
    }
}

}  // namespace document
