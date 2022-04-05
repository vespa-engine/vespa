// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for documenttyperepo.

#include <vespa/document/base/testdocrepo.h>
#include <vespa/config/print/asciiconfigwriter.h>
#include <vespa/document/datatype/annotationreferencedatatype.h>
#include <vespa/document/datatype/arraydatatype.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/mapdatatype.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vespalib/objects/identifiable.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/exceptions.h>
#include <set>

#include <vespa/log/log.h>
LOG_SETUP("doctype_config_test");

using config::AsciiConfigWriter;
using std::set;
using std::vector;
using vespalib::Identifiable;
using vespalib::IllegalArgumentException;
using vespalib::string;

using namespace document::config_builder;
using namespace document;

namespace {

const string type_name = "test";
const int32_t doc_type_id = 787121340;
const string header_name = type_name + ".header";
const int32_t header_id = 30;
const string type_name_2 = "test_2";
const string header_name_2 = type_name_2 + ".header";
const string field_name = "field_name";
const string derived_name = "derived";

using ::document::config::DocumenttypesConfigBuilder;

using BDocType        = DocumenttypesConfigBuilder::Doctype;
using BDocInherit     = DocumenttypesConfigBuilder::Doctype::Inherits;
using BDocFieldsets   = DocumenttypesConfigBuilder::Doctype::Fieldsets;
using BDocImportField = DocumenttypesConfigBuilder::Doctype::Importedfield;
using BPrimitiveT     = DocumenttypesConfigBuilder::Doctype::Primitivetype;
using BArrayT         = DocumenttypesConfigBuilder::Doctype::Arraytype;
using BMapT           = DocumenttypesConfigBuilder::Doctype::Maptype;
using BWsetT          = DocumenttypesConfigBuilder::Doctype::Wsettype;
using BTensorT        = DocumenttypesConfigBuilder::Doctype::Tensortype;
using BDocRefT        = DocumenttypesConfigBuilder::Doctype::Documentref;
using BAnnotationT    = DocumenttypesConfigBuilder::Doctype::Annotationtype;
using BAnnRefT        = DocumenttypesConfigBuilder::Doctype::Annotationref;
using BStructT        = DocumenttypesConfigBuilder::Doctype::Structtype;
using BStructField    = DocumenttypesConfigBuilder::Doctype::Structtype::Field;
using BStructInherits = DocumenttypesConfigBuilder::Doctype::Structtype::Inherits;

class BuilderHelper {
private:
    int _idx = 10000;
    DocumenttypesConfigBuilder _config;
    static int hashId(const string& name) {
        StructDataType tmp(name);
        return tmp.getId();
    }
    vector<int> _idxOfBuiltins;
    void addPrimitive(BDocType &doc, const string& name, DataType::Type t) {
        BPrimitiveT pt;
        pt.idx = ++_idx;
        pt.name = name;
        doc.primitivetype.push_back(pt);
        assert(t < _idxOfBuiltins.size());
        _idxOfBuiltins[t] = pt.idx;
        LOG(debug, "idx of builtin (%d) = %d", (int)t, pt.idx);
    }
public:
    ~BuilderHelper();
    BDocType & document(const string& name) {
        _config.doctype.reserve(100);
        auto & d = _config.doctype.emplace_back();
        d.idx = ++_idx;
        d.name = name;
        d.internalid = hashId(name);
        auto & st = addStruct(d, name + ".header");
        d.contentstruct = st.idx;
        if (_config.doctype.size() > 1) {
            d.inherits.emplace_back().idx = _config.doctype[0].idx;
        }
        return d;
    }
    BStructField & addField(BDocType &doc, const string& name) {
        return addField(doc.structtype[0], name);
    }
    BStructT & addStruct(BDocType &doc, const string& name) {
        doc.structtype.reserve(100);
        auto & st = doc.structtype.emplace_back();
        st.idx = ++_idx;
        st.name = name;
        st.internalid = hashId(name);
        return st;
    }
    BStructField & addField(BStructT &st, const string& name) {
        st.field.reserve(100);
        auto & f = st.field.emplace_back();
        f.name = name;
        f.internalid = hashId(name);
        return f;
    }
    BArrayT & addArray(BDocType &doc, int nestedIdx) {
        doc.arraytype.reserve(100);
        auto & a = doc.arraytype.emplace_back();
        a.idx = ++_idx;
        a.elementtype = nestedIdx;
        a.internalid = a.idx;
        return a;
    }
    BMapT & addMap(BDocType &doc, int keyIdx, int valIdx) {
        doc.maptype.reserve(100);
        auto & m = doc.maptype.emplace_back();
        m.idx = ++_idx;
        m.keytype = keyIdx;
        m.valuetype = valIdx;
        m.internalid = m.idx;
        return m;
    }
    BWsetT & addWset(BDocType &doc, int nestedIdx) {
        doc.wsettype.reserve(100);
        auto & w = doc.wsettype.emplace_back();
        w.idx = ++_idx;
        w.elementtype = nestedIdx;
        w.internalid = w.idx;
        return w;
    }
    BAnnotationT & addAnnotation(BDocType &doc, const string &name) {
        doc.annotationtype.reserve(100);
        auto & ann = doc.annotationtype.emplace_back();
        ann.idx = ++_idx;
        ann.name = name;
        ann.internalid = hashId(name);
        return ann;
    }
    BAnnRefT & addAnnotationRef(BDocType &doc, int annIdx) {
        doc.annotationref.reserve(100);
        auto & aref = doc.annotationref.emplace_back();
        aref.idx = ++_idx;
        aref.annotationtype = annIdx;
        aref.internalid = aref.idx;
        return aref;
    }
    BDocRefT & addDocumentRef(BDocType &doc, int targetIdx) {
        doc.documentref.reserve(100);
        auto & dref = doc.documentref.emplace_back();
        dref.idx = ++_idx;
        dref.targettype = targetIdx;
        dref.internalid = dref.idx;
        return dref;
    }
    BTensorT & addTensorType(BDocType &doc, const string& spec) {
        doc.tensortype.reserve(100);
        auto & tt = doc.tensortype.emplace_back();
        tt.idx = ++_idx;
        tt.detailedtype = spec;
        return tt;
    }
    const DocumenttypesConfig & config() { return _config; }
    BuilderHelper() {
        _idxOfBuiltins.resize(DataType::MAX);
        LOG(debug, "builtins.size = %zu", _idxOfBuiltins.size());
        auto & root = document("document");
        root.internalid = DataType::T_DOCUMENT;
        addPrimitive(root, "int", DataType::T_INT);
        addPrimitive(root, "float", DataType::T_FLOAT);
        addPrimitive(root, "string", DataType::T_STRING);
        addPrimitive(root, "raw", DataType::T_RAW);
        addPrimitive(root, "long", DataType::T_LONG);
        addPrimitive(root, "double", DataType::T_DOUBLE);
        addPrimitive(root, "bool", DataType::T_BOOL);
        addPrimitive(root, "uri", DataType::T_URI);
        addPrimitive(root, "byte", DataType::T_BYTE);
        addPrimitive(root, "tag", DataType::T_TAG);
        addPrimitive(root, "short", DataType::T_SHORT);
        addPrimitive(root, "predicate", DataType::T_PREDICATE);
    }
    int builtin(DataType::Type t) {
        if (t == DataType::T_DOCUMENT) {
            return _config.doctype[0].idx;
        }
        assert(t < _idxOfBuiltins.size());
        LOG(debug, "lookup builtin %d -> %d", (int)t, _idxOfBuiltins[t]);
        return _idxOfBuiltins[t];
    }
};

BuilderHelper::~BuilderHelper() = default;

TEST("requireThatDocumentTypeCanBeLookedUp") {
    BuilderHelper builder;
    auto &doc = builder.document(type_name);
    doc.internalid = doc_type_id;
    doc.structtype[0].internalid = header_id;
    DocumentTypeRepo repo(builder.config());

    const DocumentType *type = repo.getDocumentType(type_name);
    ASSERT_TRUE(type);
    EXPECT_EQUAL(type_name, type->getName());
    EXPECT_EQUAL(doc_type_id, type->getId());
    EXPECT_EQUAL(header_name, type->getFieldsType().getName());
    EXPECT_EQUAL(header_id, type->getFieldsType().getId());
}

TEST("requireThatDocumentTypeCanBeLookedUpWhenIdIsNotAHash") {
    BuilderHelper builder;
    auto & doc = builder.document(type_name);
    doc.internalid = doc_type_id + 2;
    auto & contents = doc.structtype[0];
    contents.name = header_name;
    contents.internalid = header_id + 3;
    DocumentTypeRepo repo(builder.config());

    const DocumentType *type = repo.getDocumentType(type_name);
    ASSERT_TRUE(type);
    EXPECT_EQUAL(type_name, type->getName());
    EXPECT_EQUAL(doc_type_id + 2, type->getId());
    EXPECT_EQUAL(header_name, type->getFieldsType().getName());
    EXPECT_EQUAL(header_id + 3, type->getFieldsType().getId());
}

TEST("requireThatDocumentsCanHaveFields") {
    BuilderHelper builder;
    auto & doc = builder.document(type_name);
    builder.addField(doc, field_name).type = builder.builtin(DataType::T_INT);
    DocumentTypeRepo repo(builder.config());

    const StructDataType &s = repo.getDocumentType(type_name)->getFieldsType();
    ASSERT_EQUAL(1u, s.getFieldCount());
    const Field &field = s.getField(field_name);
    EXPECT_EQUAL(DataType::T_INT, field.getDataType().getId());
}

template <typename T>
const T &getFieldDataType(const DocumentTypeRepo &repo) {
    const DataType &d = repo.getDocumentType(type_name)
                        ->getFieldsType().getField(field_name).getDataType();
    const T *t = dynamic_cast<const T *>(&d);
    ASSERT_TRUE(t);
    return *t;
}

TEST("requireThatArraysCanBeConfigured") {
    BuilderHelper builder;
    auto & doc = builder.document(type_name);
    auto & arr = builder.addArray(doc, builder.builtin(DataType::T_STRING));
    builder.addField(doc, field_name).type = arr.idx;
    DocumentTypeRepo repo(builder.config());

    const ArrayDataType &a = getFieldDataType<ArrayDataType>(repo);
    EXPECT_EQUAL(DataType::T_STRING, a.getNestedType().getId());
}

TEST("requireThatWsetsCanBeConfigured") {
    BuilderHelper builder;
    auto & doc = builder.document(type_name);
    auto & wset = builder.addWset(doc, builder.builtin(DataType::T_INT));
    wset.removeifzero = true;
    wset.createifnonexistent = true;
    builder.addField(doc, field_name).type = wset.idx;
    DocumentTypeRepo repo(builder.config());

    const WeightedSetDataType &w = getFieldDataType<WeightedSetDataType>(repo);
    EXPECT_EQUAL(DataType::T_INT, w.getNestedType().getId());
    EXPECT_TRUE(w.createIfNonExistent());
    EXPECT_TRUE(w.removeIfZero());
}

TEST("requireThatMapsCanBeConfigured") {
    BuilderHelper builder;
    auto & doc = builder.document(type_name);
    auto & map = builder.addMap(doc,
                                builder.builtin(DataType::T_INT),
                                builder.builtin(DataType::T_STRING));
    builder.addField(doc, field_name).type = map.idx;
    DocumentTypeRepo repo(builder.config());

    const MapDataType &m = getFieldDataType<MapDataType>(repo);
    EXPECT_EQUAL(DataType::T_INT, m.getKeyType().getId());
    EXPECT_EQUAL(DataType::T_STRING, m.getValueType().getId());
}

TEST("requireThatAnnotationReferencesCanBeConfigured") {
    int32_t annotation_type_id = 424;
    BuilderHelper builder;
    auto &doc = builder.document(type_name);
    auto & ann = builder.addAnnotation(doc, "foo");
    ann.internalid = annotation_type_id;
    auto & annRef = builder.addAnnotationRef(doc, ann.idx);
    builder.addField(doc, field_name).type = annRef.idx;
    DocumentTypeRepo repo(builder.config());

    const AnnotationReferenceDataType &ar = getFieldDataType<AnnotationReferenceDataType>(repo);
    EXPECT_EQUAL(annotation_type_id, ar.getAnnotationType().getId());
    EXPECT_EQUAL("foo", ar.getAnnotationType().getName());
}

TEST("requireThatDocumentsCanInheritFields") {
    BuilderHelper builder;
    auto & pdoc = builder.document(type_name);
    auto & cdoc = builder.document(derived_name);
    builder.addField(pdoc, field_name).type = builder.builtin(DataType::T_INT);
    builder.addField(cdoc, "derived_field").type = builder.builtin(DataType::T_STRING);
    cdoc.inherits.emplace_back().idx = pdoc.idx;
    DocumentTypeRepo repo(builder.config());

    const StructDataType &s = repo.getDocumentType(derived_name)->getFieldsType();
    ASSERT_EQUAL(2u, s.getFieldCount());
    const Field &field = s.getField(field_name);
    const DataType &type = field.getDataType();
    EXPECT_EQUAL(DataType::T_INT, type.getId());
    EXPECT_EQUAL(DataType::T_STRING, s.getField("derived_field").getDataType().getId());
}

TEST("requireThatDocumentsCanUseInheritedTypes") {
    const int32_t id = 64;
    BuilderHelper builder;
    auto & pdoc = builder.document(type_name);
    auto & cdoc = builder.document(derived_name);
    auto & arr = builder.addArray(pdoc, builder.builtin(DataType::T_INT));
    arr.internalid = id;
    builder.addField(pdoc, "foo").type = arr.idx;
    builder.addField(cdoc, field_name).type = arr.idx;
    cdoc.inherits.emplace_back().idx = pdoc.idx;

    DocumentTypeRepo repo(builder.config());

    const DataType &type =
        repo.getDocumentType(derived_name)->getFieldsType()
        .getField(field_name).getDataType();
    EXPECT_EQUAL(id, type.getId());
    EXPECT_TRUE(dynamic_cast<const ArrayDataType *>(&type));
}

TEST("requireThatIllegalConfigsCausesExceptions") {
    BuilderHelper builder;
    auto &doc = builder.document(type_name);
    doc.inherits.emplace_back().idx = 20000;
    EXPECT_EXCEPTION(DocumentTypeRepo repo(builder.config()),
                IllegalArgumentException, "Unable to find document");
}

TEST("requireThatDataTypesCanBeLookedUpById") {
    BuilderHelper builder;
    auto &doc1 = builder.document(type_name);
    auto &doc2 = builder.document(derived_name);
    doc1.internalid = doc_type_id;
    doc1.structtype[0].internalid = header_id;
    doc2.internalid = doc_type_id + 1;
    DocumentTypeRepo repo(builder.config());

    const auto * dt1 = repo.getDocumentType(type_name);
    const auto * dt2 = repo.getDocumentType(derived_name);

    ASSERT_TRUE(dt1);
    ASSERT_TRUE(dt2);
    EXPECT_EQUAL(dt1, repo.getDocumentType(doc_type_id));
    EXPECT_EQUAL(dt2, repo.getDocumentType(doc_type_id + 1));

    const DataType *type = repo.getDataType(*dt1, header_id);
    ASSERT_TRUE(type);
    EXPECT_EQUAL(header_name, type->getName());
    EXPECT_EQUAL(header_id, type->getId());

    EXPECT_TRUE(!repo.getDataType(*dt1, -1));
    EXPECT_TRUE(!repo.getDataType(*dt2, header_id));
}

TEST("requireThatDataTypesCanBeLookedUpByName") {
    BuilderHelper builder;
    auto &doc1 = builder.document(type_name);
    doc1.structtype[0].internalid = header_id;
    builder.document(type_name_2);
    DocumentTypeRepo repo(builder.config());

    const DocumentType * dt1 = repo.getDocumentType(type_name);
    const DocumentType * dt2 = repo.getDocumentType(type_name_2);
    ASSERT_TRUE(dt1);
    ASSERT_TRUE(dt2);

    const DataType *type = repo.getDataType(*dt1, header_name);
    ASSERT_TRUE(type);
    EXPECT_EQUAL(header_name, type->getName());
    EXPECT_EQUAL(header_id, type->getId());

    EXPECT_TRUE(repo.getDataType(*dt1, header_name));
    EXPECT_TRUE(!repo.getDataType(*dt1, field_name));
    EXPECT_TRUE(!repo.getDataType(*dt2, header_name));
}

TEST("requireThatInheritingDocCanRedefineIdenticalField") {
    BuilderHelper builder;

    auto & pdoc = builder.document(type_name);
    auto & cdoc = builder.document(derived_name);
    builder.addField(pdoc, field_name).type = builder.builtin(DataType::T_STRING);

    builder.addField(cdoc, field_name).type = builder.builtin(DataType::T_STRING);
    cdoc.inherits.emplace_back().idx = pdoc.idx;

    DocumentTypeRepo repo(builder.config());

    const StructDataType &s = repo.getDocumentType(derived_name)->getFieldsType();
    ASSERT_EQUAL(1u, s.getFieldCount());
}

TEST("requireThatAnnotationTypesCanBeConfigured") {
    const int32_t a_id = 654;
    const string a_name = "annotation_name";
    BuilderHelper builder;
    auto & doc = builder.document(type_name);
    auto & ann = builder.addAnnotation(doc, a_name);
    ann.internalid = a_id;
    ann.datatype = builder.builtin(DataType::T_STRING);

    DocumentTypeRepo repo(builder.config());

    const DocumentType *type = repo.getDocumentType(type_name);
    ASSERT_TRUE(type);
    const AnnotationType *a_type = repo.getAnnotationType(*type, a_id);
    ASSERT_TRUE(a_type);
    EXPECT_EQUAL(a_name, a_type->getName());
    ASSERT_TRUE(a_type->getDataType());
    EXPECT_EQUAL(DataType::T_STRING, a_type->getDataType()->getId());

    a_type = repo.getAnnotationType(*type, 1);
    ASSERT_TRUE(a_type);
    EXPECT_EQUAL(1, a_type->getId());
    EXPECT_EQUAL("term", a_type->getName());
    a_type = repo.getAnnotationType(*type, 2);
    ASSERT_TRUE(a_type);
    EXPECT_EQUAL(2, a_type->getId());
    EXPECT_EQUAL("token_type", a_type->getName());
}

TEST("requireThatDocumentsCanUseOtherDocumentTypes") {
    BuilderHelper builder;
    auto &doc2 = builder.document(type_name_2);
    doc2.internalid = doc_type_id + 1;
    auto &doc1 = builder.document(type_name);
    builder.addField(doc1, field_name).type = doc2.idx;
    DocumentTypeRepo repo(builder.config());

    const DataType &type = repo.getDocumentType(type_name)->getFieldsType()
                           .getField(field_name).getDataType();
    EXPECT_EQUAL(doc_type_id + 1, type.getId());
    EXPECT_TRUE(dynamic_cast<const DocumentType *>(&type));
}

TEST("requireThatDocumentTypesCanBeIterated") {
    BuilderHelper builder;
    builder.document(type_name).internalid = doc_type_id;
    builder.document(type_name_2).internalid = doc_type_id + 1;
    DocumentTypeRepo repo(builder.config());

    set<int> ids;
    repo.forEachDocumentType(
            [&ids](const DocumentType &type) { ids.insert(type.getId()); });

    EXPECT_EQUAL(3u, ids.size());
    ASSERT_TRUE(ids.count(DataType::T_DOCUMENT));
    ASSERT_TRUE(ids.count(doc_type_id));
    ASSERT_TRUE(ids.count(doc_type_id + 1));
}

TEST("requireThatDocumentLookupChecksName") {
    BuilderHelper builder;
    auto &doc = builder.document(type_name_2);
    doc.internalid = doc_type_id;
    DocumentTypeRepo repo(builder.config());

    // "type_name" will generate the document type id
    // "doc_type_id". However, this config assigns that id to a
    // different type.
    const DocumentType *type = repo.getDocumentType(type_name);
    ASSERT_TRUE(!type);
}

TEST("requireThatBuildFromConfigWorks") {
    DocumentTypeRepo repo(readDocumenttypesConfig(TEST_PATH("types.cfg")));
    ASSERT_TRUE(repo.getDocumentType("document"));
    ASSERT_TRUE(repo.getDocumentType("types"));
}

TEST("requireThatStructsCanInheritFields") {
    BuilderHelper builder;
    auto & doc = builder.document(type_name);
    auto & st1 = builder.addStruct(doc, "sa");
    auto & st2 = builder.addStruct(doc, "sb");
    auto & st3 = builder.addStruct(doc, "sc");
    builder.addField(st1, "fa").type = builder.builtin(DataType::T_INT);
    builder.addField(st2, "fb").type = builder.builtin(DataType::T_LONG);
    builder.addField(st3, "fc").type = builder.builtin(DataType::T_STRING);
    st1.inherits.emplace_back().type = st2.idx;
    st2.inherits.emplace_back().type = st3.idx;
    builder.addField(doc, field_name).type = st1.idx;
    DocumentTypeRepo repo(builder.config());
    const StructDataType &s = getFieldDataType<StructDataType>(repo);
    EXPECT_EQUAL(3u, s.getFieldCount());
    ASSERT_TRUE(s.hasField("fa"));
    ASSERT_TRUE(s.hasField("fb"));
    ASSERT_TRUE(s.hasField("fc"));
}

TEST("requireThatStructsCanBeRecursive") {
    BuilderHelper builder;
    auto & doc = builder.document(type_name);
    auto & st = builder.addStruct(doc, "folder");
    builder.addField(st, "subfolder").type = st.idx;
    builder.addField(doc, field_name).type = st.idx;
    DocumentTypeRepo repo(builder.config());

    const StructDataType &s = getFieldDataType<StructDataType>(repo);
    EXPECT_EQUAL(1u, s.getFieldCount());
    ASSERT_TRUE(s.hasField("subfolder"));
    EXPECT_EQUAL(&s, &s.getField("subfolder").getDataType());
}

}  // namespace

TEST("requireThatMissingFileCausesException") {
    EXPECT_EXCEPTION(readDocumenttypesConfig("illegal/missing_file"),
                     IllegalArgumentException, "Unable to open file");
}

TEST("requireThatFieldsCanHaveAnyDocumentType") {
    BuilderHelper builder;
    auto &doc1 = builder.document(type_name);
    auto &doc2 = builder.document(type_name_2);

    // Circular dependency
    builder.addField(doc1, field_name).type = doc2.idx;
    builder.addField(doc2, field_name).type = doc1.idx;

    DocumentTypeRepo repo(builder.config());
    const DocumentType *type1 = repo.getDocumentType(type_name);
    const DocumentType *type2 = repo.getDocumentType(type_name_2);
    ASSERT_TRUE(type1);
    EXPECT_TRUE(type1->getFieldsType().hasField(field_name));
    EXPECT_EQUAL(type2, &type1->getFieldsType().getField(field_name).getDataType());
    ASSERT_TRUE(type2);
    EXPECT_TRUE(type2->getFieldsType().hasField(field_name));
    EXPECT_EQUAL(type1, &type2->getFieldsType().getField(field_name).getDataType());
}

TEST("Require that Array can have nested DocumentType") {
    BuilderHelper builder;
    auto &doc = builder.document(type_name);
    auto &arr = builder.addArray(doc, doc.idx);
    builder.addField(doc, field_name).type = arr.idx;
    DocumentTypeRepo repo(builder.config());
    const DocumentType *type = repo.getDocumentType(type_name);
    ASSERT_TRUE(type);
}

TEST("Reference fields are resolved to correct reference type") {
    const int doc_with_refs_id = 5678;
    const int ref1_id = 777;
    const int ref2_id = 888;
    BuilderHelper builder;
    auto & doc1 = builder.document(type_name);
    auto & doc2 = builder.document(type_name_2);
    auto & doc3 = builder.document("doc_with_refs");
    doc3.internalid = doc_with_refs_id;
    auto & refT1 = builder.addDocumentRef(doc3, doc1.idx);
    refT1.internalid = ref1_id;
    auto & refT2 = builder.addDocumentRef(doc3, doc2.idx);
    refT2.internalid = ref2_id;
    builder.addField(doc3, "ref1").type = refT1.idx;
    builder.addField(doc3, "ref2").type = refT2.idx;
    builder.addField(doc3, "ref3").type = refT1.idx;

    DocumentTypeRepo repo(builder.config());
    const DocumentType *type = repo.getDocumentType(doc_with_refs_id);
    ASSERT_TRUE(type != nullptr);
    const auto* ref1_type(repo.getDataType(*type, ref1_id));
    const auto* ref2_type(repo.getDataType(*type, ref2_id));

    EXPECT_EQUAL(*ref1_type, type->getFieldsType().getField("ref1").getDataType());
    EXPECT_EQUAL(*ref2_type, type->getFieldsType().getField("ref2").getDataType());
    EXPECT_EQUAL(*ref1_type, type->getFieldsType().getField("ref3").getDataType());
}

TEST("Config with no imported fields has empty imported fields set in DocumentType") {
    BuilderHelper builder;
    builder.document(type_name);
    DocumentTypeRepo repo(builder.config());
    const auto *type = repo.getDocumentType(type_name);
    ASSERT_TRUE(type != nullptr);
    EXPECT_TRUE(type->imported_field_names().empty());
    EXPECT_FALSE(type->has_imported_field_name("foo"));
}

TEST("Configured imported field names are available in the DocumentType") {
    // Note: we cheat a bit by specifying imported field names in types that have no
    // reference fields. Add to test if we add config read-time validation of this. :)
    BuilderHelper builder;
    // Type with one imported field
    builder.document(type_name).importedfield.emplace_back().name = "my_cool_field";
    // Type with two imported fields
    auto & doc2 = builder.document(type_name_2);
    doc2.importedfield.emplace_back().name = "my_awesome_field";
    doc2.importedfield.emplace_back().name = "my_swag_field";

    DocumentTypeRepo repo(builder.config());
    const auto* type = repo.getDocumentType(type_name);
    ASSERT_TRUE(type != nullptr);
    EXPECT_EQUAL(1u, type->imported_field_names().size());
    EXPECT_TRUE(type->has_imported_field_name("my_cool_field"));
    EXPECT_FALSE(type->has_imported_field_name("my_awesome_field"));

    type = repo.getDocumentType(type_name_2);
    ASSERT_TRUE(type != nullptr);
    EXPECT_EQUAL(2u, type->imported_field_names().size());
    EXPECT_TRUE(type->has_imported_field_name("my_awesome_field"));
    EXPECT_TRUE(type->has_imported_field_name("my_swag_field"));
    EXPECT_FALSE(type->has_imported_field_name("my_cool_field"));
}

namespace {

const TensorDataType &
asTensorDataType(const DataType &dataType) {
    return dynamic_cast<const TensorDataType &>(dataType);
}

}

TEST("Tensor fields have tensor types") {
    BuilderHelper builder;
    auto & doc = builder.document(type_name);
    auto & t1t = builder.addTensorType(doc, "tensor(x[3])");
    auto & t2t = builder.addTensorType(doc, "tensor(y{})");
    builder.addField(doc, "tensor1").type = t1t.idx;
    builder.addField(doc, "tensor2").type = t2t.idx;
    builder.addField(doc, "tensor3").type = t1t.idx;

    DocumentTypeRepo repo(builder.config());
    auto *docType = repo.getDocumentType(type_name);
    ASSERT_TRUE(docType != nullptr);
    auto &tensorField1 = docType->getField("tensor1");
    auto &tensorField2 = docType->getField("tensor2");
    EXPECT_EQUAL("tensor(x[3])", asTensorDataType(tensorField1.getDataType()).getTensorType().to_spec());
    EXPECT_EQUAL("tensor(y{})", asTensorDataType(tensorField2.getDataType()).getTensorType().to_spec());
    auto &tensorField3 = docType->getField("tensor3");
    EXPECT_TRUE(&tensorField1.getDataType() == &tensorField3.getDataType());
    auto tensorFieldValue1 = tensorField1.getDataType().createFieldValue();
    EXPECT_TRUE(&tensorField1.getDataType() == tensorFieldValue1->getDataType());
}

TEST("requireThatImportedFieldsWorks") {
    DocumentTypeRepo repo(readDocumenttypesConfig(TEST_PATH("import-dt.cfg")));
    ASSERT_TRUE(repo.getDocumentType("document"));
    ASSERT_TRUE(repo.getDocumentType("grandparent"));
    ASSERT_TRUE(repo.getDocumentType("parent_a"));
    ASSERT_TRUE(repo.getDocumentType("parent_b"));
    ASSERT_TRUE(repo.getDocumentType("child"));
}


TEST_MAIN() { TEST_RUN_ALL(); }
