// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documenttyperepo.h"

#include <vespa/document/datatype/annotationreferencedatatype.h>
#include <vespa/document/datatype/arraydatatype.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/mapdatatype.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/document/datatype/urldatatype.h>
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/document/datatype/referencedatatype.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/document/config/config-documenttypes.h>
#include <fstream>

#include <vespa/log/log.h>
LOG_SETUP(".documenttyperepo");

using std::unique_ptr;
using std::fstream;
using std::make_pair;
using std::pair;
using std::vector;
using vespalib::Closure1;
using vespalib::Identifiable;
using vespalib::IllegalArgumentException;
using vespalib::hash_map;
using vespalib::make_string;
using vespalib::string;
using vespalib::stringref;
using vespalib::compression::CompressionConfig;

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
    hash_map<string, const DataType *> _name_map;

public:
    ~Repo() { DeleteContent(_owned_types); }

    void inherit(const Repo &parent);
    bool addDataType(const DataType &type);
    template <typename T> void addDataType(unique_ptr<T> type);

    const DataType *lookup(int32_t id) const;
    const DataType *lookup(const stringref &name) const;
    const DataType &findOrThrow(int32_t id) const;
};

void Repo::inherit(const Repo &parent) {
    _types.insert(parent._types.begin(), parent._types.end());
    _name_map.insert(parent._name_map.begin(), parent._name_map.end());
}

// Returns true if a reference to type is stored.
bool Repo::addDataType(const DataType &type) {
    const DataType *& data_type = _types[type.getId()];
    if (data_type) {
        if (*data_type == type) {
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
    return true;
}

template <typename T>
void Repo::addDataType(unique_ptr<T> type) {
    if (addDataType(*type)) {
        _owned_types.push_back(type.release());
    }
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

const DataType *Repo::lookup(const stringref &n) const {
    return FindPtr(_name_map, n);
}

const DataType &Repo::findOrThrow(int32_t id) const {
    const DataType *type = lookup(id);
    if (type) {
        return *type;
    }
    throw IllegalArgumentException(make_string("Unknown datatype %d", id));
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
    } else if (*(annotation_type->getDataType()) != d) {
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
    AnnotationType::UP a(new AnnotationType(type.id, type.name));
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

void addField(const Datatype::Sstruct::Field &field, const Repo &repo, StructDataType &struct_type, bool isHeaderField)
{
    LOG(spam, "Adding field %s to %s (header: %s)",
        field.name.c_str(), struct_type.getName().c_str(), isHeaderField ? "yes" : "no");
    const DataType &field_type = repo.findOrThrow(field.datatype);
    struct_type.addField(Field(field.name, field.id, field_type, isHeaderField));
}

bool hasSuffix(const string &s, const string &suffix) {
    string::size_type pos = s.rfind(suffix.c_str());
    return pos != string::npos && pos == s.size() - suffix.size();
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
        const StructDataType& cdt = Identifiable::cast<const StructDataType&>(*existing);
        struct_type = const_cast<StructDataType*>(&cdt);
    } else {
        const DataType *existing_retry = repo.lookup(id);
        LOG(spam, "Type %s not found, adding it", name.c_str());
        struct_type_ap.reset(new StructDataType(name, id));
        struct_type = struct_type_ap.get();
        repo.addDataType(std::move(struct_type_ap));
        if (existing_retry) {
            return;
        }
    }

    CompressionConfig::Type type = CompressionConfig::NONE;
    if (s.compression.type == Datatype::Sstruct::Compression::LZ4) {
        type = CompressionConfig::LZ4;
    }

    struct_type->setCompressionConfig(
            CompressionConfig(type, s.compression.level, s.compression.threshold, s.compression.minsize));

    for (size_t i = 0; i < s.field.size(); ++i) {
        addField(s.field[i], repo, *struct_type, hasSuffix(s.name, ".header"));
    }
}

void addArray(int32_t id, const Datatype::Array &a, Repo &repo) {
    const DataType &nested = repo.findOrThrow(a.element.id);
    repo.addDataType(DataType::UP(new ArrayDataType(nested, id)));
}

void addWset(int32_t id, const Datatype::Wset &w, Repo &repo) {
    const DataType &key = repo.findOrThrow(w.key.id);
    repo.addDataType(DataType::UP(new WeightedSetDataType(key, w.createifnonexistent, w.removeifzero, id)));
}

void addMap(int32_t id, const Datatype::Map &m, Repo &repo) {
    const DataType &key = repo.findOrThrow(m.key.id);
    const DataType &value = repo.findOrThrow(m.value.id);
    repo.addDataType(DataType::UP(new MapDataType(key, value, id)));
}

void addAnnotationRef(int32_t id, const Datatype::Annotationref &a, Repo &r, const AnnotationTypeRepo &annotations) {
    const AnnotationType *type = annotations.lookup(a.annotation.id);
    if (!type) {
        throw IllegalArgumentException(make_string("Unknown AnnotationType %d", a.annotation.id));
    }
    r.addDataType(DataType::UP(new AnnotationReferenceDataType(*type, id)));
}

void addDataType(const Datatype &type, Repo &repo, const AnnotationTypeRepo &a_repo) {
    switch (type.type) {
    case Datatype::STRUCT:
        return addStruct(type.id, type.sstruct, repo);
    case Datatype::ARRAY:
        return addArray(type.id, type.array, repo);
    case Datatype::WSET:
        return addWset(type.id, type.wset, repo);
    case Datatype::MAP:
        return addMap(type.id, type.map, repo);
    case Datatype::ANNOTATIONREF:
        return addAnnotationRef(type.id, type.annotationref, repo, a_repo);
    default:
        throw IllegalArgumentException(make_string("Unknown datatype type %d for id %d", type.type, type.id));
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
    DataTypeRepo::UP data_types(new DataTypeRepo);
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

DataTypeRepo::UP makeDataTypeRepo(const DocumentType &doc_type, const DocumentTypeMap &type_map) {
    DataTypeRepo::UP data_types(new DataTypeRepo);
    data_types->repo.inherit(lookupRepo(DataType::T_DOCUMENT, type_map).repo);
    data_types->annotations.inherit(lookupRepo(DataType::T_DOCUMENT, type_map).annotations);
    data_types->doc_type = doc_type.clone();
    return data_types;
}

void addFieldSet(const DocumenttypesConfig::Documenttype::FieldsetsMap & fsv, DocumentType &doc_type) {
    for (const auto & entry : fsv) {
        const DocumenttypesConfig::Documenttype::Fieldsets & fs(entry.second);
        DocumentType::FieldSet::Fields fields;
        for (size_t j(0); j < fs.fields.size(); j++) {
            fields.insert(fs.fields[j]);
        }
        doc_type.addFieldSet(entry.first, fields);
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
    DataTypeRepo::UP data_types(new DataTypeRepo);
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
        createAllDocumentTypes(config.documenttype, *_doc_types);
        addAllDocumentTypesToRepos(*_doc_types);
        configureAllRepos(config.documenttype, *_doc_types);
    } catch (...) {
        DeleteMapContent(*_doc_types);
        throw;
    }
}

DocumentTypeRepo::~DocumentTypeRepo() {
    DeleteMapContent(*_doc_types);
}

const DocumentType *
DocumentTypeRepo::getDocumentType(int32_t type_id) const {
    const DataTypeRepo *repo = FindPtr(*_doc_types, type_id);
    return repo ? repo->doc_type : nullptr;
}

const DocumentType *
DocumentTypeRepo::getDocumentType(const stringref &name) const {
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
DocumentTypeRepo::getDataType(const DocumentType &doc_type, const stringref &name) const {
    const DataTypeRepo *dt_repo = FindPtr(*_doc_types, doc_type.getId());
    return dt_repo ? dt_repo->repo.lookup(name) : nullptr;
}

const AnnotationType *
DocumentTypeRepo::getAnnotationType(const DocumentType &doc_type, int32_t id) const {
    const DataTypeRepo *dt_repo = FindPtr(*_doc_types, doc_type.getId());
    return dt_repo ? dt_repo->annotations.lookup(id) : nullptr;
}

void
DocumentTypeRepo::forEachDocumentType(Closure1<const DocumentType &> &c) const {
    for (const auto & entry : *_doc_types) {
        c.call(*entry.second->doc_type);
    }
}

}  // namespace document
