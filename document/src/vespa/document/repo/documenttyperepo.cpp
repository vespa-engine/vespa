// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documenttyperepo.h"

#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/document/datatype/annotationreferencedatatype.h>
#include <vespa/document/datatype/arraydatatype.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/mapdatatype.h>
#include <vespa/document/datatype/positiondatatype.h>
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

using DocumentTypeMapT = std::map<int32_t, std::unique_ptr<DataTypeRepo>>;

class DocumentTypeMap : public DocumentTypeMapT
{
public:
    using DocumentTypeMapT::DocumentTypeMapT;
    [[nodiscard]] DataTypeRepo * findRepo(int32_t doc_type_id) const {
        auto iter = find(doc_type_id);
        if (iter == end()) {
            return nullptr;
        } else {
            return iter->second.get();
        }
    }
};

}

using DocumentTypeMap = internal::DocumentTypeMap;

namespace {

// A collection of data types.
class Repo {
    vector<std::unique_ptr<const DataType>> _owned_types;
    hash_map<int32_t, const DataType *> _id_map;
    hash_map<string, const DataType *> _tensorTypes;
    hash_map<string, const DataType *> _name_map;

public:
    Repo() noexcept;
    ~Repo();

    void inherit(const Repo &parent) __attribute__((noinline));
    bool addDataType(const DataType &type) __attribute__((noinline));
    template <typename T> const DataType * addDataType(unique_ptr<T> type) __attribute__((noinline));

    const DataType &addTensorType(const string &spec) __attribute__((noinline));
    const DataType *lookup(int32_t id) const __attribute__((noinline));
    const DataType *lookup(stringref name) const __attribute__((noinline));
    const DataType &findOrThrow(int32_t id) const __attribute__((noinline));
    const DataType &findOrThrowOrCreate(int32_t id, const string &detailedType) __attribute__((noinline));
};

Repo::Repo() noexcept = default;
Repo::~Repo() = default;

void
Repo::inherit(const Repo &parent) {
    _id_map.insert(parent._id_map.begin(), parent._id_map.end());
    _tensorTypes.insert(parent._tensorTypes.begin(), parent._tensorTypes.end());
    _name_map.insert(parent._name_map.begin(), parent._name_map.end());
}

// Returns true if a reference to type is stored.
bool
Repo::addDataType(const DataType &type) {
    const DataType *& data_type = _id_map[type.getId()];
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
const DataType*
Repo::addDataType(unique_ptr<T> type) {
    int id = type->getId();
    if (addDataType(*type)) {
        _owned_types.emplace_back(std::move(type));
    }
    return _id_map[id];
}


const DataType &
Repo::addTensorType(const string &spec)
{
    auto type = TensorDataType::fromSpec(spec);
    auto [ iter, inserted ] = _tensorTypes.insert(std::make_pair(spec, type.get()));
    if (inserted) {
        _owned_types.emplace_back(std::move(type));
    }
    return *iter->second;
}

const DataType *Repo::lookup(int32_t id) const {
    auto iter = _id_map.find(id);
    return (iter == _id_map.end()) ? nullptr : iter->second;
}

const DataType *Repo::lookup(stringref n) const {
    auto iter = _name_map.find(n);
    return (iter == _name_map.end()) ? nullptr : iter->second;
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
    vector<std::unique_ptr<const AnnotationType>> _owned_types;
    hash_map<int32_t, AnnotationType *> _annotation_types;

public:
    ~AnnotationTypeRepo() {}

    void inherit(const AnnotationTypeRepo &parent);
    AnnotationType * addAnnotationType(AnnotationType::UP annotation_type);
    void setAnnotationDataType(int32_t id, const DataType &datatype);

    const AnnotationType *lookup(int32_t id) const;
};

void AnnotationTypeRepo::inherit(const AnnotationTypeRepo &parent) {
    _annotation_types.insert(parent._annotation_types.begin(), parent._annotation_types.end());
}

AnnotationType * AnnotationTypeRepo::addAnnotationType(AnnotationType::UP type) {
    AnnotationType *& a_type = _annotation_types[type->getId()];
    if (a_type) {
        if (*type != *a_type) {
            throw IllegalArgumentException(
                make_string("Redefinition of annotation type %d, \"%s\". Previously defined as \"%s\".",
                            type->getId(), type->getName().c_str(), a_type->getName().c_str()));
        }
    } else {
        a_type = type.get();
        _owned_types.emplace_back(std::move(type));
    }
    return a_type;
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
    auto iter = _annotation_types.find(id);
    return (iter == _annotation_types.end()) ? nullptr : iter->second;
}

}  // namespace

// Combination of a DocumentType and a collection of DataTypes
// associated with it.
struct DataTypeRepo {
    using UP = unique_ptr<DataTypeRepo>;

    std::unique_ptr<DocumentType> doc_type;
    Repo repo;
    AnnotationTypeRepo annotations;

    DataTypeRepo() : doc_type() {}
    ~DataTypeRepo() {}

    DocumentType * doc() const { return doc_type.get(); }
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

using Datatype = DocumenttypesConfig::Documenttype::Datatype;

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

void addArray(int32_t id, const Datatype::Array &a, Repo &repo) __attribute__((noinline));
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

void addDataType(const Datatype &type, Repo &repo, const AnnotationTypeRepo &a_repo) __attribute__((noinline));
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
    for (const auto & [ key, data_type_repo ] : type_map) {
        repo.addDataType(*data_type_repo->doc());
    }
}

const DocumentType *
addDefaultDocument(DocumentTypeMap &type_map) {
    const uint32_t typeId = DataType::T_DOCUMENT;

    auto data_types = std::make_unique<DataTypeRepo>();
    vector<const DataType *> default_types = DataType::getDefaultDataTypes();
    for (size_t i = 0; i < default_types.size(); ++i) {
        data_types->repo.addDataType(*default_types[i]);
    }
    data_types->repo.addDataType(PositionDataType::getInstance());
    data_types->doc_type = std::make_unique<DocumentType>("document", typeId);

    vector<const AnnotationType *> annotation_types(AnnotationType::getDefaultAnnotationTypes());
    for(size_t i(0); i < annotation_types.size(); ++i) {
        data_types->annotations.addAnnotationType(std::make_unique<AnnotationType>(*annotation_types[i]));
    }
    const DocumentType * docType = data_types->doc();
    type_map[typeId] = std::move(data_types);
    return docType;
}

const DataTypeRepo &lookupRepo(int32_t id, const DocumentTypeMap &type_map) {
    if (const auto * p = type_map.findRepo(id)) {
        return *p;
    } else {
        throw IllegalArgumentException(make_string("Unable to find document type %d.", id));
    }
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
    data_types->doc_type = std::make_unique<DocumentType>(doc_type);
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
        const auto & repo = lookupRepo(ref_type.targetTypeId, doc_type_map);
        data_type_repo.addDataType(std::make_unique<ReferenceDataType>(*repo.doc_type, ref_type.id));
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
    const auto & data_types = type_map[doc_type.id];
    inheritAnnotationTypes(doc_type.inherits, type_map, data_types->annotations);
    addAnnotationTypes(doc_type.annotationtype, data_types->annotations);
    inheritDataTypes(doc_type.inherits, type_map, data_types->repo);
    addReferenceTypes(doc_type.referencetype, data_types->repo, type_map);
    addDataTypes(doc_type.datatype, data_types->repo, data_types->annotations);
    setAnnotationDataTypes(doc_type.annotationtype, data_types->annotations, data_types->repo);
    inheritDocumentTypes(doc_type.inherits, type_map, *data_types->doc());
    addFieldSet(doc_type.fieldsets, *data_types->doc());
    add_imported_fields(doc_type.importedfield, *data_types->doc());
}

void addDataTypeRepo(DataTypeRepo::UP data_types, DocumentTypeMap &doc_types) {
    auto & p = doc_types[data_types->doc()->getId()];
    if (p) {
        LOG(warning, "Type repo already exists for id %d.", data_types->doc()->getId());
        throw IllegalArgumentException("Trying to redefine a document type.");
    }
    p = std::move(data_types);
}

DataTypeRepo::UP makeSkeletonDataTypeRepo(const DocumenttypesConfig::Documenttype &type) {
    auto data_types = std::make_unique<DataTypeRepo>();
    auto type_ap = std::make_unique<StructDataType>(type.name + ".header", type.headerstruct);
    data_types->doc_type = std::make_unique<DocumentType>(type.name, type.id, *type_ap);
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
using DocTypesByIdx = hash_map<int, DocumentType *>;


class ApplyNewDoctypeConfig {
private:
    using DTC = ::document::config::DocumenttypesConfig;

    using CDocType        = DTC::Doctype;
    using CDocInherit     = DTC::Doctype::Inherits;
    using CDocFieldsets   = DTC::Doctype::Fieldsets;
    using CDocImportField = DTC::Doctype::Importedfield;
    using CPrimitiveT     = DTC::Doctype::Primitivetype;
    using CArrayT         = DTC::Doctype::Arraytype;
    using CMapT           = DTC::Doctype::Maptype;
    using CWsetT          = DTC::Doctype::Wsettype;
    using CTensorT        = DTC::Doctype::Tensortype;
    using CDocRefT        = DTC::Doctype::Documentref;
    using CAnnotationT    = DTC::Doctype::Annotationtype;
    using CAnnRefT        = DTC::Doctype::Annotationref;
    using CStructT        = DTC::Doctype::Structtype;
    using CStructField    = DTC::Doctype::Structtype::Field;
    using CStructInherits = DTC::Doctype::Structtype::Inherits;

    struct DocTypeInProgress {
        const CDocType & cfg;
        DataTypeRepo * data_type_repo = nullptr;
        bool builtin = false;

        DocTypeInProgress(const CDocType & config, DocumentTypeMap &doc_types)
            : cfg(config)
        {
            auto iter = doc_types.find(cfg.internalid);
            if (iter != doc_types.end()) {
                LOG(debug, "old doct : %s [%d]", cfg.name.c_str(), cfg.internalid);
                builtin = true;
            } else {
                LOG(debug, "new doct : %s [%d]", cfg.name.c_str(), cfg.internalid);
                doc_types.emplace(cfg.internalid, std::make_unique<DataTypeRepo>());
            }
            iter = doc_types.find(cfg.internalid);
            LOG_ASSERT(iter != doc_types.end());
            data_type_repo = iter->second.get();
        }

        Repo& repo() { return data_type_repo->repo; }
    };

    struct StructInProgress {
        const CStructT & cfg;
        StructDataType *stype = nullptr;
        const StructDataType *oldtype = nullptr;
        bool finished = false;
        StructInProgress(const CStructT & config) : cfg(config) {}
    };
    using StructsInProgress = std::map<int, StructInProgress>;
    StructsInProgress _structs_in_progress;

    using DocTypesInProgress = std::map<int, DocTypeInProgress>;
    using MadeTypes = std::map<int, const DataType *>;

    const DocumenttypesConfig::DoctypeVector & _input;
    DocumentTypeMap & _output;

    DocTypesInProgress _doc_types_in_progress;
    hash_map<int, AnnotationType *> _annotations_by_idx;
    MadeTypes _made_types;
    std::set<int> _needed_idx_set;

    void apply() __attribute__((noinline));
    void madeType(const DataType *t, int idx) __attribute__((noinline));

    void createSimpleTypes(DocTypeInProgress & dtInP) {
        for (const auto & primT : dtInP.cfg.primitivetype) {
            string name = primT.name;
            const DataType *t = dtInP.repo().lookup(name);
            if (t == nullptr) {
                if (name == "float16") {
                    // is this even sane?
                    name = "float";
                }
                name[0] = (name[0] & 0x5F);
                t = dtInP.repo().lookup(name);
            }
            if (t == nullptr) {
                LOG(error, "Missing primitive type '%s'", primT.name.c_str());
                throw IllegalArgumentException("missing primitive type");
            } else {
                madeType(t, primT.idx);
            }
        }
        for (const auto & tensorT : dtInP.cfg.tensortype) {
            const DataType & tt = dtInP.repo().addTensorType(tensorT.detailedtype);
            madeType(&tt, tensorT.idx);
        }
    }

    void createEmptyStructs(DocTypeInProgress & dtInP) {
        for (const auto & structT : dtInP.cfg.structtype) {
            StructInProgress in_progress(structT);
            if (const auto * oldt = dtInP.repo().lookup(structT.internalid)) {
                auto st = dynamic_cast<const StructDataType *>(oldt);
                if (st) {
                    LOG(debug, "already has %s [%d], wanted to add %s [%d]",
                        st->getName().c_str(), st->getId(),
                        structT.name.c_str(), structT.internalid);
                    in_progress.oldtype = st;
                    in_progress.finished = true;
                    madeType(st, structT.idx);
                } else {
                    throw IllegalArgumentException("struct internalid -> not a struct");
                }
            } else {
                auto up = std::make_unique<StructDataType>(structT.name, structT.internalid);
                in_progress.stype = up.get();
                const DataType *t = dtInP.repo().addDataType(std::move(up));
                LOG_ASSERT(t == in_progress.stype);
                madeType(t, structT.idx);
            }
            auto [iter, succ] = _structs_in_progress.emplace(structT.idx, in_progress);
            LOG_ASSERT(succ);
        }
    }

    const StructDataType * findStruct(int idx) {
        auto iter = _structs_in_progress.find(idx);
        if (iter == _structs_in_progress.end()) return nullptr;
        const auto & in_progress = iter->second;
        if (in_progress.finished) {
            return in_progress.oldtype;
        }
        return in_progress.stype;
    }

    void initializeDocTypeAndInheritAnnotations(DocTypeInProgress & dtInP) {
        if (dtInP.builtin) {
            madeType(dtInP.data_type_repo->doc(), dtInP.cfg.idx);
            return;
        }
        LOG_ASSERT(dtInP.data_type_repo->doc() == nullptr);
        const auto & docT = dtInP.cfg;
        const StructDataType * fields = findStruct(docT.contentstruct);
        if (fields != nullptr) {
            dtInP.data_type_repo->doc_type = std::make_unique<DocumentType>(docT.name, docT.internalid, *fields);
            madeType(dtInP.data_type_repo->doc(), docT.idx);
        } else {
            LOG(error, "Missing content struct for '%s' (idx %d not found)",
                docT.name.c_str(), docT.contentstruct);
            throw IllegalArgumentException("missing content struct");
        }
        // depends on config in inheritance order
        for (const auto & inheritD : docT.inherits) {
            const DataType *dt = _made_types[inheritD.idx];
            if (dt == nullptr) {
                LOG(error, "parent datatype [idx %d] missing for document %s",
                    inheritD.idx, docT.name.c_str());
                throw IllegalArgumentException("Unable to find document for inheritance");
            }
            const DataTypeRepo *parentRepo = _output.findRepo(dt->getId());
            if (parentRepo == nullptr) {
                LOG(error, "parent repo [id %d] missing for document %s",
                    dt->getId(), docT.name.c_str());
                throw IllegalArgumentException("missing parent repo");
            }
            dtInP.data_type_repo->annotations.inherit(parentRepo->annotations);
        }
    }

    void createEmptyAnnotationTypes(DocTypeInProgress & dtInP) {
        auto & annRepo = dtInP.data_type_repo->annotations;
        for (const auto & annT: dtInP.cfg.annotationtype) {
            if (annRepo.lookup(annT.internalid)) {
                throw IllegalArgumentException("duplicate annotation type id");
            }
            auto at = std::make_unique<AnnotationType>(annT.internalid, annT.name);
            _annotations_by_idx[annT.idx] = at.get();
            _needed_idx_set.erase(annT.idx);
            const auto * t = annRepo.addAnnotationType(std::move(at));
            LOG_ASSERT(t == _annotations_by_idx[annT.idx]);
        }
    }

    void createReferenceTypes(DocTypeInProgress & dtInP) {
        for (const auto & aRef : dtInP.cfg.annotationref) {
            const AnnotationType * target = _annotations_by_idx[aRef.annotationtype];
            if (target == nullptr) {
                LOG(error, "Missing annotation type [idx %d] for annotationref",
                    aRef.annotationtype);
                throw IllegalArgumentException("missing annotation type");
            } else {
                auto ar = std::make_unique<AnnotationReferenceDataType>(*target, aRef.internalid);
                madeType(dtInP.repo().addDataType(std::move(ar)), aRef.idx);
            }
        }
        for (const auto & refT : dtInP.cfg.documentref) {
            const auto * target = dynamic_cast<const DocumentType *>(_made_types[refT.targettype]);
            if (target == nullptr) {
                LOG(error, "Missing target document type for reference (idx %d)", refT.targettype);
                throw IllegalArgumentException("missing target type");
            } else {
                auto rt = std::make_unique<ReferenceDataType>(*target, refT.internalid);
                madeType(dtInP.repo().addDataType(std::move(rt)), refT.idx);
            }
        }
    }

    void createComplexTypes() __attribute__((noinline));
    void createComplexTypesForDocType(const CDocType & docT, Repo& repo) __attribute__((noinline));

    void fillStructs() {
        for (auto & [idx, in_progress] : _structs_in_progress) {
            if (in_progress.finished) {
                continue;
            }
            auto st = in_progress.stype;
            LOG_ASSERT(st);
            for (const auto & fieldD : in_progress.cfg.field) {
                const DataType *ft = _made_types[fieldD.type];
                if (ft == nullptr) {
                    LOG(error, "Missing type [idx %d] for struct %s field %s",
                        fieldD.type, in_progress.cfg.name.c_str(), fieldD.name.c_str());
                    throw IllegalArgumentException("missing datatype");
                } else {
                    st->addField(Field(fieldD.name, fieldD.internalid, *ft));
                }
            }
        }
    }

    void fillDocument(DocTypeInProgress & dtInP) {
        if (dtInP.builtin) {
            return;
        }
        const CDocType & docT = dtInP.cfg;
        auto * doc_type = dtInP.data_type_repo->doc();
        LOG_ASSERT(doc_type != nullptr);
        for (const auto & importD : docT.importedfield) {
            doc_type->add_imported_field_name(importD.name);
        }
        for (const auto & inheritD : docT.inherits) {
            const DataType *dt = _made_types[inheritD.idx];
            const DocumentType * parent = dynamic_cast<const DocumentType *>(dt);
            if (parent == nullptr) {
                LOG(error, "missing parent type [idx %d] for document %s",
                    inheritD.idx, docT.name.c_str());
                throw IllegalArgumentException("missing parent type");
            } else {
                doc_type->inherit(*parent);
            }
        }
        for (const auto & entry : docT.fieldsets) {
            DocumentType::FieldSet::Fields fields;
            for (const auto& f : entry.second.fields) {
                fields.insert(f);
            }
            doc_type->addFieldSet(entry.first, fields);
        }
    }

    void fillAnnotationTypes(DocTypeInProgress & dtInP) {
        for (const auto & annT: dtInP.cfg.annotationtype) {
            AnnotationType * at = _annotations_by_idx[annT.idx];
            if (annT.datatype != -1) {
                const DataType * dt = _made_types[annT.datatype];
                if (dt == nullptr) {
                    LOG(error, "Missing datatype [idx %d] for annotation type %s",
                        annT.datatype, annT.name.c_str());
                    throw IllegalArgumentException("missing datatype");
                } else {
                    at->setDataType(*dt);
                }
            }
            for (const auto & inheritD : annT.inherits) {
                LOG_ASSERT(at != nullptr);
                const AnnotationType * parent = _annotations_by_idx[inheritD.idx];
                if (parent == nullptr) {
                    LOG(error, "missing parent [idx %d] for annotation %s",
                        inheritD.idx, annT.name.c_str());
                    throw IllegalArgumentException("missing parent");
                }
            }
        }
    }

    class EnsureIndexes {
        std::set<int> _set;
    public:
        void add(int idx) {
            auto [iter, succ] = _set.insert(idx);
            if (! succ) {
                throw IllegalArgumentException("duplicate type idx");
            }
            LOG(debug, "ensure indexes: add %d", idx);
        }
        void check(int idx) {
            if (! _set.contains(idx)) {
                LOG(error, "ensure indexes: missing %d", idx);
                throw IllegalArgumentException("needed idx missing");
            }
        }
    };

    void findNeeded() {
        EnsureIndexes idx_set;
        for (const auto & docT : _input) {
            LOG(debug, "doc %s", docT.name.c_str());
            idx_set.add(docT.idx);
            for (const auto & structT : docT.structtype) {
                idx_set.add(structT.idx);
                for (const auto & fieldD : structT.field) {
                    LOG(debug, "doc %s struct %s field %s needs [idx %d]",
                        docT.name.c_str(), structT.name.c_str(), fieldD.name.c_str(), fieldD.type);
                    _needed_idx_set.insert(fieldD.type);
                }
            }
            for (const auto & primT : docT.primitivetype) {
                idx_set.add(primT.idx);
            }
            for (const auto & tensorT : docT.tensortype) {
                idx_set.add(tensorT.idx);
            }
            for (const auto & arrT : docT.arraytype) {
                idx_set.add(arrT.idx);
                LOG(debug, "doc %s array needs [idx %d]", docT.name.c_str(),arrT.elementtype);
                _needed_idx_set.insert(arrT.elementtype);
            }
            for (const auto & wsetT : docT.wsettype) {
                idx_set.add(wsetT.idx);
                LOG(debug, "doc %s wset needs [idx %d]", docT.name.c_str(), wsetT.elementtype);
                _needed_idx_set.insert(wsetT.elementtype);
            }
            for (const auto & mapT : docT.maptype) {
                idx_set.add(mapT.idx);
                LOG(debug, "doc %s wset needs [idx %d] and [idx %d]",
                    docT.name.c_str(), mapT.keytype, mapT.valuetype);
                _needed_idx_set.insert(mapT.keytype);
                _needed_idx_set.insert(mapT.valuetype);
            }
            for (const auto & annT: docT.annotationtype) {
                idx_set.add(annT.idx);
                if (annT.datatype != -1) {
                    LOG(debug, "doc %s ann needs datatype [idx %d]", docT.name.c_str(), annT.datatype);
                    _needed_idx_set.insert(annT.datatype);
                }
                for (const auto & inheritD : annT.inherits) {
                    LOG(debug, "doc %s ann needs parent [idx %d]", docT.name.c_str(), inheritD.idx);
                    _needed_idx_set.insert(inheritD.idx);
                }
            }
            for (const auto & aRef : docT.annotationref) {
                idx_set.add(aRef.idx);
                LOG(debug, "doc %s ann ref needs annotation [idx %d]", docT.name.c_str(), aRef.annotationtype);
                _needed_idx_set.insert(aRef.annotationtype);
            }
            for (const auto & refT : docT.documentref) {
                idx_set.add(refT.idx);
                LOG(debug, "doc %s doc ref needs target [idx %d]", docT.name.c_str(), refT.targettype);
                _needed_idx_set.insert(refT.targettype);
            }
        }
        for (int needed : _needed_idx_set) {
            idx_set.check(needed);
        }
    }

    const StructDataType * performStructInherit(int idx) {
        auto iter = _structs_in_progress.find(idx);
        if (iter == _structs_in_progress.end()) {
            throw IllegalArgumentException("inherit from non-struct");
        }
        auto & in_progress = iter->second;
        if (in_progress.finished) {
            return in_progress.oldtype;
        }
        const auto & structT = in_progress.cfg;
        for (const auto & inheritD : structT.inherits) {
            const auto * parent = performStructInherit(inheritD.type);
            if (parent == nullptr) {
                LOG(error, "Missing parent type [idx %d] for struct %s",
                    inheritD.type, structT.name.c_str());
                throw IllegalArgumentException("missing parent type");
            }
            for (const auto & field : parent->getFieldSet()) {
                in_progress.stype->addInheritedField(*field);
            }
        }
        in_progress.finished = true;
        in_progress.oldtype = in_progress.stype;
        return in_progress.oldtype;
    }

public:
    ApplyNewDoctypeConfig(const DocumenttypesConfig::DoctypeVector & input, DocumentTypeMap & output);
    ~ApplyNewDoctypeConfig();
};

ApplyNewDoctypeConfig::ApplyNewDoctypeConfig(const DocumenttypesConfig::DoctypeVector & input, DocumentTypeMap & output)
    : _input(input),
      _output(output)
{
    apply();
}
ApplyNewDoctypeConfig::~ApplyNewDoctypeConfig() = default;

void
ApplyNewDoctypeConfig::madeType(const DataType *t, int idx) {
    _made_types[idx] = t;
    _needed_idx_set.erase(idx);
}

void
ApplyNewDoctypeConfig::apply() {
    findNeeded();
    for (const CDocType & docT : _input) {
        auto [iter,succ] = _doc_types_in_progress.emplace(docT.idx,
                                                          DocTypeInProgress(docT, _output));
        LOG_ASSERT(succ);
        auto & dtInP = iter->second;
        createSimpleTypes(dtInP);
        createEmptyStructs(dtInP);
        initializeDocTypeAndInheritAnnotations(dtInP);
        createEmptyAnnotationTypes(dtInP);
    }
    for (auto & [id, dtInP] : _doc_types_in_progress) {
        createReferenceTypes(dtInP);
    }
    createComplexTypes();
    fillStructs();
    for (auto & [id, dtInP] : _doc_types_in_progress) {
        fillDocument(dtInP);
        fillAnnotationTypes(dtInP);
    }
    for (const auto & docT : _input) {
        for (const auto & structT : docT.structtype) {
            performStructInherit(structT.idx);
        }
    }
}

void
ApplyNewDoctypeConfig::createComplexTypes() {
    while (_needed_idx_set.size() > 0) {
        size_t missing_cnt = _needed_idx_set.size();
        for (const auto & docT : _input) {
            auto iter = _doc_types_in_progress.find(docT.idx);
            LOG_ASSERT(iter != _doc_types_in_progress.end());
            auto & dtInP = iter->second;
            createComplexTypesForDocType(dtInP.cfg, dtInP.repo());
        }
        if (_needed_idx_set.size() == missing_cnt) {
            for (int idx : _needed_idx_set) {
                LOG(error, "no progress, datatype [idx %d] still missing", idx);
            }
            throw IllegalArgumentException("no progress");
        }
        LOG(debug, "retry complex types, %zd missing", _needed_idx_set.size());
    }
}

void
ApplyNewDoctypeConfig::createComplexTypesForDocType(const CDocType & docT, Repo& repo) {
    for (const auto & arrT : docT.arraytype) {
        if (_made_types[arrT.idx] != nullptr) {
            continue; // OK already
        }
        if (const DataType * nested = _made_types[arrT.elementtype]) {
            auto at = std::make_unique<ArrayDataType>(*nested, arrT.internalid);
            madeType(repo.addDataType(std::move(at)), arrT.idx);
        }
    }
    for (const auto & mapT : docT.maptype) {
        if (_made_types[mapT.idx] != nullptr) {
            continue; // OK already
        }
        const DataType * kt = _made_types[mapT.keytype];
        const DataType * vt = _made_types[mapT.valuetype];
        if (kt && vt) {
            auto mt = std::make_unique<MapDataType>(*kt, *vt, mapT.internalid);
            madeType(repo.addDataType(std::move(mt)), mapT.idx);
        }
    }
    for (const auto & wsetT : docT.wsettype) {
        if (_made_types[wsetT.idx] != nullptr) {
            continue; // OK already
        }
        if (const DataType * nested = _made_types[wsetT.elementtype]) {
            auto wt = std::make_unique<WeightedSetDataType>(*nested,
                                                            wsetT.createifnonexistent, wsetT.removeifzero,
                                                            wsetT.internalid);
            madeType(repo.addDataType(std::move(wt)), wsetT.idx);
        }
    }
}

void configureDocTypes(const DocumenttypesConfig::DoctypeVector &t, DocumentTypeMap &type_map) {
    LOG(debug, "applying new doc type config");
    ApplyNewDoctypeConfig(t, type_map);
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
        throw;
    }
}

DocumentTypeRepo::~DocumentTypeRepo() = default;

DataTypeRepo *DocumentTypeRepo::findRepo(int32_t doc_type_id) const {
    return _doc_types->findRepo(doc_type_id);
}

const DocumentType *
DocumentTypeRepo::getDocumentType(int32_t type_id) const noexcept {
    const DataTypeRepo *repo = findRepo(type_id);
    return repo ? repo->doc() : nullptr;
}

const DocumentType *
DocumentTypeRepo::getDocumentType(stringref name) const noexcept {
    const auto * rp = findRepo(DocumentType::createId(name));
    if (rp && rp->doc()->getName() == name) {
        return rp->doc();
    }
    for (const auto & [ id, p ] : *_doc_types) {
        if (p->doc()->getName() == name) {
            return p->doc();
        }
    }
    return nullptr;
}

const DataType *
DocumentTypeRepo::getDataType(const DocumentType &doc_type, int32_t id) const {
    const DataTypeRepo *dt_repo = findRepo(doc_type.getId());
    return dt_repo ? dt_repo->repo.lookup(id) : nullptr;
}

const DataType *
DocumentTypeRepo::getDataType(const DocumentType &doc_type, stringref name) const {
    const DataTypeRepo *dt_repo = findRepo(doc_type.getId());
    return dt_repo ? dt_repo->repo.lookup(name) : nullptr;
}

const AnnotationType *
DocumentTypeRepo::getAnnotationType(const DocumentType &doc_type, int32_t id) const {
    const DataTypeRepo *dt_repo = findRepo(doc_type.getId());
    return dt_repo ? dt_repo->annotations.lookup(id) : nullptr;
}

void
DocumentTypeRepo::forEachDocumentType(std::function<void(const DocumentType &)> handler) const {
    for (const auto & [ it, rp ] : *_doc_types) {
        handler(*rp->doc());
    }
}

}  // namespace document
