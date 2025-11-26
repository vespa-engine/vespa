// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
#include <vespa/document/repo/newconfigbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/identifiable.h>
#include <vespa/vespalib/test/test_path.h>
#include <vespa/vespalib/util/exceptions.h>
#include <set>
#include <string>


using config::AsciiConfigWriter;
using std::set;
using std::vector;
using vespalib::Identifiable;
using vespalib::IllegalArgumentException;
using std::string;

using namespace document::config_builder;
using document::new_config_builder::NewConfigBuilder;
using namespace document;

namespace {

void dumpConfig(const auto& cfg) {
    vespalib::asciistream ss;
    AsciiConfigWriter writer(ss);
    EXPECT_TRUE(writer.write(cfg));
    fprintf(stderr, "config >>>\n%s\n<<<\n", ss.str().c_str());
}

const string type_name = "test";
const int32_t doc_type_id = 787121340;
const string header_name = type_name + ".header";
const int32_t header_id = 306916075; // value of String("test.header.0").hashCode() in java
const string body_name = type_name + ".body";
const int32_t body_id = 31;
const string type_name_2 = "test_2";
const string header_name_2 = type_name_2 + ".header";
const string body_name_2 = type_name_2 + ".body";
const string field_name = "field_name";
const string derived_name = "derived";

TEST(DocumentTypeRepoTest, requireThatDocumentTypeCanBeLookedUp)
{
    NewConfigBuilder builder;
    builder.document(type_name, doc_type_id);
    DocumentTypeRepo repo(builder.config());

    const DocumentType *type = repo.getDocumentType(type_name);
    ASSERT_TRUE(type);
    EXPECT_EQ(type_name, type->getName());
    EXPECT_EQ(doc_type_id, type->getId());
    EXPECT_EQ(header_name, type->getFieldsType().getName());
    EXPECT_EQ(header_id, type->getFieldsType().getId());
}

TEST(DocumentTypeRepoTest, requireThatDocumentTypeCanBeLookedUpWhenIdIsNotAHash)
{
    NewConfigBuilder builder;
    builder.document(type_name, doc_type_id + 2);
    DocumentTypeRepo repo(builder.config());

    const DocumentType *type = repo.getDocumentType(type_name);
    ASSERT_TRUE(type);
}

TEST(DocumentTypeRepoTest, requireThatStructsCanHaveFields)
{
    NewConfigBuilder builder;
    auto& doc = builder.document(type_name, doc_type_id);
    doc.addField(field_name, builder.intTypeRef());
    DocumentTypeRepo repo(builder.config());

    const StructDataType &s = repo.getDocumentType(type_name)->getFieldsType();
    ASSERT_EQ(1u, s.getFieldCount());
    const Field &field = s.getField(field_name);
    EXPECT_EQ(DataType::T_INT, field.getDataType().getId());
}

template <typename T>
const T &getFieldDataType(const DocumentTypeRepo &repo) {
    const DataType &d = repo.getDocumentType(type_name)
                        ->getFieldsType().getField(field_name).getDataType();
    return dynamic_cast<const T&>(d);
}

TEST(DocumentTypeRepoTest, requireThatArraysCanBeConfigured)
{
    NewConfigBuilder builder;
    auto& doc = builder.document(type_name, doc_type_id);
    auto arr = doc.createArray(builder.stringTypeRef());
    doc.addField(field_name, doc.registerArray(std::move(arr)));
    DocumentTypeRepo repo(builder.config());

    const ArrayDataType &a = getFieldDataType<ArrayDataType>(repo);
    EXPECT_EQ(DataType::T_STRING, a.getNestedType().getId());
}

TEST(DocumentTypeRepoTest, requireThatWsetsCanBeConfigured)
{
    NewConfigBuilder builder;
    auto& doc = builder.document(type_name, doc_type_id);
    auto wset = doc.createWset(builder.intTypeRef());
    wset.removeIfZero().createIfNonExistent();
    doc.addField(field_name, doc.registerWset(std::move(wset)));
    DocumentTypeRepo repo(builder.config());

    const WeightedSetDataType &w = getFieldDataType<WeightedSetDataType>(repo);
    EXPECT_EQ(DataType::T_INT, w.getNestedType().getId());
    EXPECT_TRUE(w.createIfNonExistent());
    EXPECT_TRUE(w.removeIfZero());
}

TEST(DocumentTypeRepoTest, requireThatMapsCanBeConfigured)
{
    NewConfigBuilder builder;
    auto& doc = builder.document(type_name, doc_type_id);
    auto map = doc.createMap(builder.intTypeRef(), builder.stringTypeRef());
    doc.addField(field_name, doc.registerMap(std::move(map)));
    DocumentTypeRepo repo(builder.config());

    const MapDataType &m = getFieldDataType<MapDataType>(repo);
    EXPECT_EQ(DataType::T_INT, m.getKeyType().getId());
    EXPECT_EQ(DataType::T_STRING, m.getValueType().getId());
}

TEST(DocumentTypeRepoTest, requireThatAnnotationReferencesCanBeConfigured)
{
    int32_t annotation_type_id = 424;

    NewConfigBuilder builder;
    auto& doc = builder.document(type_name, doc_type_id);
    auto annotationIdx = doc.createAnnotationType(annotation_type_id, "foo");
    auto annotationRefIdx = doc.createAnnotationReference(annotationIdx);
    doc.addField(field_name, annotationRefIdx);
    DocumentTypeRepo repo(builder.config());

    const AnnotationReferenceDataType &ar = getFieldDataType<AnnotationReferenceDataType>(repo);
    EXPECT_EQ(annotation_type_id, ar.getAnnotationType().getId());
}

TEST(DocumentTypeRepoTest, requireThatFieldsCanNotBeHeaderAndBody)
{
    DocumenttypesConfigBuilderHelper builder;
    builder.document(doc_type_id, type_name,
                     Struct(header_name).addField(field_name,
                             DataType::T_STRING),
                     Struct(body_name).addField(field_name,
                             DataType::T_INT));
    VESPA_EXPECT_EXCEPTION(DocumentTypeRepo repo(builder.config()),
                           IllegalArgumentException,
                           "Failed to add field 'field_name' to struct 'test.header': "
                           "Name in use by field with different id");
}

TEST(DocumentTypeRepoTest, requireThatDocumentStructsAreCalledHeaderAndBody)
{
    DocumenttypesConfigBuilderHelper builder;
    builder.document(doc_type_id, type_name, Struct("foo"), Struct("bar"));
    VESPA_EXPECT_EXCEPTION(DocumentTypeRepo repo(builder.config()),
                           IllegalArgumentException,
                           "Previously defined as \"test.header\".");
}

TEST(DocumentTypeRepoTest, requireThatDocumentsCanInheritFields)
{
    NewConfigBuilder builder;
    auto& base_doc = builder.document(type_name, doc_type_id);
    base_doc.addField(field_name, builder.intTypeRef());

    auto& derived_doc = builder.document(derived_name, doc_type_id + 1);
    derived_doc.addField("derived_field", builder.stringTypeRef());
    derived_doc.inherit(base_doc.idx());

    DocumentTypeRepo repo(builder.config());

    const StructDataType &s =
        repo.getDocumentType(doc_type_id + 1)->getFieldsType();
    ASSERT_EQ(2u, s.getFieldCount());
    const Field &field = s.getField(field_name);
    const DataType &type = field.getDataType();
    EXPECT_EQ(DataType::T_INT, type.getId());
}

TEST(DocumentTypeRepoTest, requireThatDocumentsCanUseInheritedTypes)
{
    NewConfigBuilder builder;

    // Create base document with an array type
    auto& base_doc = builder.document(type_name, doc_type_id);
    auto arr = base_doc.createArray(builder.intTypeRef());
    auto arr_ref = base_doc.registerArray(std::move(arr));
    base_doc.addField("foo", arr_ref);

    // Create derived document that inherits and uses the same array type
    auto& derived_doc = builder.document(derived_name, doc_type_id + 1);
    derived_doc.inherit(base_doc.idx());
    derived_doc.addField(field_name, arr_ref);  // Reuse the same array type ref

    DocumentTypeRepo repo(builder.config());

    const DataType &type =
        repo.getDocumentType(doc_type_id + 1)->getFieldsType()
        .getField(field_name).getDataType();
    EXPECT_EQ(builder.getInternalId(arr_ref), type.getId());
    EXPECT_TRUE(dynamic_cast<const ArrayDataType *>(&type));
}

TEST(DocumentTypeRepoTest, requireThatIllegalConfigsCausesExceptions)
{
    DocumenttypesConfigBuilderHelper builder;
    builder.document(doc_type_id, type_name,
                     Struct(header_name), Struct(body_name))
        .inherit(doc_type_id + 1);
    VESPA_EXPECT_EXCEPTION(DocumentTypeRepo repo(builder.config()),
                           IllegalArgumentException, "Unable to find document");

    builder = DocumenttypesConfigBuilderHelper();
    builder.document(doc_type_id, type_name,
                     Struct(header_name), Struct(body_name));
    builder.config().documenttype[0].datatype[0].type =
        DocumenttypesConfig::Documenttype::Datatype::Type(-1);
    VESPA_EXPECT_EXCEPTION(DocumentTypeRepo repo(builder.config()),
                           IllegalArgumentException, "Unknown datatype type -1");

    builder = DocumenttypesConfigBuilderHelper();
    const int id = 10000;
    builder.document(doc_type_id, type_name,
                     Struct(header_name),
                     Struct(body_name).addField(field_name,
                             Array(DataType::T_INT).setId(id)));
    EXPECT_EQ(id, builder.config().documenttype[0].datatype[1].id);
    builder.config().documenttype[0].datatype[1].array.element.id = id;
    VESPA_EXPECT_EXCEPTION(DocumentTypeRepo repo(builder.config()),
                           IllegalArgumentException, "Unknown datatype 10000");

    builder = DocumenttypesConfigBuilderHelper();
    builder.document(doc_type_id, type_name,
                     Struct(header_name).setId(header_id),
                     Struct(body_name).addField("foo",
                             Struct("bar").setId(header_id)));
    VESPA_EXPECT_EXCEPTION(DocumentTypeRepo repo(builder.config()),
                           IllegalArgumentException, "Redefinition of data type");

    builder = DocumenttypesConfigBuilderHelper();
    builder.document(doc_type_id, type_name,
                     Struct(header_name),
                     Struct(body_name).addField(field_name,
                             AnnotationRef(id)));
    VESPA_EXPECT_EXCEPTION(DocumentTypeRepo repo(builder.config()),
                           IllegalArgumentException, "Unknown AnnotationType");

    builder = DocumenttypesConfigBuilderHelper();
    builder.document(doc_type_id, type_name,
                     Struct(header_name), Struct(body_name))
        .annotationType(id, type_name, DataType::T_STRING)
        .annotationType(id, type_name, DataType::T_INT);
    VESPA_EXPECT_EXCEPTION(DocumentTypeRepo repo(builder.config()),
                           IllegalArgumentException, "Redefinition of annotation type");

    builder = DocumenttypesConfigBuilderHelper();
    builder.document(doc_type_id, type_name,
                     Struct(header_name), Struct(body_name))
        .annotationType(id, type_name, DataType::T_STRING)
        .annotationType(id, "foobar", DataType::T_STRING);
    VESPA_EXPECT_EXCEPTION(DocumentTypeRepo repo(builder.config()),
                           IllegalArgumentException, "Redefinition of annotation type");
}

TEST(DocumentTypeRepoTest, requireThatDataTypesCanBeLookedUpById)
{
    NewConfigBuilder builder;
    builder.document(type_name, doc_type_id);
    builder.document(type_name_2, doc_type_id + 1);
    DocumentTypeRepo repo(builder.config());

    // In the new API, the contentstruct (fields struct) gets an auto-generated ID
    // which should match the expected header_id
    const DataType *type =
        repo.getDataType(*repo.getDocumentType(doc_type_id), header_id);
    ASSERT_TRUE(type);
    EXPECT_EQ(header_name, type->getName());
    EXPECT_EQ(header_id, type->getId());

    ASSERT_TRUE(!repo.getDataType(*repo.getDocumentType(doc_type_id), -1));
    ASSERT_TRUE(!repo.getDataType(*repo.getDocumentType(doc_type_id + 1),
                                  header_id));
}

TEST(DocumentTypeRepoTest, requireThatDataTypesCanBeLookedUpByName)
{
    DocumenttypesConfigBuilderHelper builder;
    builder.document(doc_type_id, type_name,
                     Struct(header_name).setId(header_id),
                     Struct(body_name));
    builder.document(doc_type_id + 1, type_name_2,
                     Struct(header_name_2), Struct(body_name_2));
    DocumentTypeRepo repo(builder.config());

    const DataType *type =
        repo.getDataType(*repo.getDocumentType(doc_type_id), header_name);
    ASSERT_TRUE(type);
    EXPECT_EQ(header_name, type->getName());
    EXPECT_EQ(header_id, type->getId());

    EXPECT_TRUE(repo.getDataType(*repo.getDocumentType(doc_type_id),
                                 type_name));
    EXPECT_TRUE(!repo.getDataType(*repo.getDocumentType(doc_type_id),
                                  field_name));
    EXPECT_TRUE(!repo.getDataType(*repo.getDocumentType(doc_type_id + 1),
                                  body_name));
}

TEST(DocumentTypeRepoTest, requireThatInheritingDocCanRedefineIdenticalField)
{
    NewConfigBuilder builder;

    // Base document with a string field
    auto& base_doc = builder.document(type_name, doc_type_id);
    base_doc.addField(field_name, builder.stringTypeRef());

    // Derived document redefines the same field (same name, same type)
    auto& derived_doc = builder.document(derived_name, doc_type_id + 1);
    derived_doc.inherit(base_doc.idx());
    derived_doc.addField(field_name, builder.stringTypeRef());

    DocumentTypeRepo repo(builder.config());

    const StructDataType &s = repo.getDocumentType(doc_type_id + 1)->getFieldsType();
    ASSERT_EQ(1u, s.getFieldCount());
}

TEST(DocumentTypeRepoTest, requireThatAnnotationTypesCanBeConfigured)
{
    const int32_t a_id = 654;
    const string a_name = "annotation_name";
    NewConfigBuilder builder;
    auto& doc = builder.document(type_name, doc_type_id);
    doc.createAnnotationType(a_id, a_name, builder.stringTypeRef());
    DocumentTypeRepo repo(builder.config());

    const DocumentType *type = repo.getDocumentType(doc_type_id);
    const AnnotationType *a_type = repo.getAnnotationType(*type, a_id);
    ASSERT_TRUE(a_type);
    EXPECT_EQ(a_name, a_type->getName());
    ASSERT_TRUE(a_type->getDataType());
    EXPECT_EQ(DataType::T_STRING, a_type->getDataType()->getId());
}

TEST(DocumentTypeRepoTest, requireThatDocumentsCanUseOtherDocumentTypes)
{
    NewConfigBuilder builder;

    // Create second document type first
    auto& doc2 = builder.document(type_name_2, doc_type_id + 1);

    // Create first document type that has a field of the second document type
    auto& doc1 = builder.document(type_name, doc_type_id);
    doc1.addField(type_name_2, document::new_config_builder::TypeRef(doc2.idx()));

    DocumentTypeRepo repo(builder.config());

    const DataType &type = repo.getDocumentType(doc_type_id)->getFieldsType()
                           .getField(type_name_2).getDataType();
    EXPECT_EQ(doc_type_id + 1, type.getId());
    EXPECT_TRUE(dynamic_cast<const DocumentType *>(&type));
}

TEST(DocumentTypeRepoTest, requireThatDocumentTypesCanBeIterated)
{
    NewConfigBuilder builder;
    builder.document(type_name, doc_type_id);
    builder.document(type_name_2, doc_type_id + 1);
    DocumentTypeRepo repo(builder.config());

    set<int> ids;
    repo.forEachDocumentType(
            [&ids](const DocumentType &type) { ids.insert(type.getId()); });

    EXPECT_EQ(3u, ids.size());
    ASSERT_TRUE(ids.count(DataType::T_DOCUMENT));
    ASSERT_TRUE(ids.count(doc_type_id));
    ASSERT_TRUE(ids.count(doc_type_id + 1));
}

TEST(DocumentTypeRepoTest, requireThatDocumentLookupChecksName)
{
    DocumenttypesConfigBuilderHelper builder;

    // Java hashcode of string 'test_doc.0':
    int32_t collisionId = 2056425229;
    builder.document(collisionId, type_name_2,
                     Struct(header_name_2), Struct(body_name_2));

    dumpConfig(builder.config());

    DocumentTypeRepo repo(builder.config());

    // "test_doc" will generate the document type id
    // collisionId. However, this config assigns that id to a
    // different type.
    const DocumentType *type = repo.getDocumentType("test_doc");
    ASSERT_TRUE(!type);
}

TEST(DocumentTypeRepoTest, requireThatBuildFromConfigWorks)
{
    DocumentTypeRepo repo(readDocumenttypesConfig(TEST_PATH("documenttypes.cfg")));
    ASSERT_TRUE(repo.getDocumentType("document"));
    ASSERT_TRUE(repo.getDocumentType("types"));
    ASSERT_TRUE(repo.getDocumentType("types_search"));
}

TEST(DocumentTypeRepoTest, requireThatStructsCanBeRecursive)
{
    NewConfigBuilder builder;
    auto& doc = builder.document(type_name, doc_type_id);
    auto bodyRef = doc.createStruct(body_name).ref();
    doc.addField(field_name, bodyRef);
    builder.registerStructField(bodyRef, field_name, bodyRef);
    DocumentTypeRepo repo(builder.config());
    const DataType &dt = repo.getDocumentType(type_name)->getFieldsType().getField(field_name).getDataType();
    ASSERT_TRUE(dynamic_cast<const StructDataType *>(&dt));
    const StructDataType &s = dynamic_cast<const StructDataType &>(dt);
    ASSERT_EQ(1u, s.getFieldCount());
}

}  // namespace

TEST(DocumentTypeRepoTest, requireThatMissingFileCausesException)
{
    VESPA_EXPECT_EXCEPTION(readDocumenttypesConfig("illegal/missing_file"),
                           IllegalArgumentException, "Unable to open file");
}

TEST(DocumentTypeRepoTest, requireThatFieldsCanHaveAnyDocumentType) {
    NewConfigBuilder builder;
    auto& doc1 = builder.document(type_name, doc_type_id);
    auto& doc2 = builder.document(type_name_2, doc_type_id + 1);
    // Circular dependency
    doc1.addField(field_name, doc2.ref());
    doc2.addField(field_name, doc1.ref());

    DocumentTypeRepo repo(builder.config());
    const DocumentType *type1 = repo.getDocumentType(doc_type_id);
    const DocumentType *type2 = repo.getDocumentType(doc_type_id + 1);
    ASSERT_TRUE(type1);
    ASSERT_TRUE(type2);
    EXPECT_TRUE(type1->getFieldsType().hasField(field_name));
    EXPECT_TRUE(type2->getFieldsType().hasField(field_name));

    EXPECT_EQ(type2, &type1->getFieldsType().getField(field_name).getDataType());
    EXPECT_EQ(type1, &type2->getFieldsType().getField(field_name).getDataType());

    // not expected anymore, this is probably ok?
    // EXPECT_EQ(type1, repo.getDataType(*type1, doc_type_id));
    // EXPECT_EQ(type2, repo.getDataType(*type1, doc_type_id + 1));
    // EXPECT_EQ(type1, repo.getDataType(*type2, doc_type_id));
    // EXPECT_EQ(type2, repo.getDataType(*type2, doc_type_id + 1));
}

TEST(DocumentTypeRepoTest, requireThatBodyCanOccurBeforeHeaderInConfig)
{
    DocumenttypesConfigBuilderHelper builder;
    // Add header and body in reverse order, then swap the ids.
    builder.document(doc_type_id, type_name,
                     Struct(body_name).setId(body_id).addField("bodystuff",
                             DataType::T_STRING),
                     Struct(header_name).setId(header_id).addField(
                             "headerstuff", DataType::T_INT));
    std::swap(builder.config().documenttype[0].headerstruct,
              builder.config().documenttype[0].bodystruct);

    DocumentTypeRepo repo(builder.config());
    const StructDataType &s = repo.getDocumentType(type_name)->getFieldsType();
    // Should have both fields in fields struct
    EXPECT_TRUE(s.hasField("headerstuff"));
    EXPECT_TRUE(s.hasField("bodystuff"));
}

TEST(DocumentTypeRepoTest, Require_that_Array_can_have_nested_DocumentType)
{
    NewConfigBuilder builder;
    auto& doc = builder.document(type_name, doc_type_id);
    // Create an array that contains the document type itself
    auto arr = doc.createArray(document::new_config_builder::TypeRef(doc.idx()));
    doc.addField(field_name, doc.registerArray(std::move(arr)));
    DocumentTypeRepo repo(builder.config());
    const DocumentType *type = repo.getDocumentType(doc_type_id);
    ASSERT_TRUE(type);
}

TEST(DocumentTypeRepoTest, Reference_fields_are_resolved_to_correct_reference_type)
{
    const int doc_with_refs_id = 5678;
    const int type_2_id = doc_type_id + 1;
    NewConfigBuilder builder;

    // Create the target document types
    auto& target1 = builder.document(type_name, doc_type_id);
    auto& target2 = builder.document(type_name_2, type_2_id);

    // Create document with reference fields
    auto& doc_with_refs = builder.document("doc_with_refs", doc_with_refs_id);
    auto ref1_type = doc_with_refs.referenceType(target1.idx());
    auto ref2_type = doc_with_refs.referenceType(target2.idx());

    doc_with_refs.addField("ref1", ref1_type);
    doc_with_refs.addField("ref2", ref2_type);
    doc_with_refs.addField("ref3", ref1_type);  // Reuse ref1_type

    DocumentTypeRepo repo(builder.config());
    const DocumentType *type = repo.getDocumentType(doc_with_refs_id);
    ASSERT_TRUE(type != nullptr);

    // Get the reference types by their auto-generated IDs
    auto ref1_id = builder.getInternalId(ref1_type);
    auto ref2_id = builder.getInternalId(ref2_type);

    const auto* ref1_dt(repo.getDataType(*type, ref1_id));
    const auto* ref2_dt(repo.getDataType(*type, ref2_id));

    EXPECT_EQ(*ref1_dt, type->getFieldsType().getField("ref1").getDataType());
    EXPECT_EQ(*ref2_dt, type->getFieldsType().getField("ref2").getDataType());
    EXPECT_EQ(*ref1_dt, type->getFieldsType().getField("ref3").getDataType());
}

TEST(DocumentTypeRepoTest, Config_with_no_imported_fields_has_empty_imported_fields_set_in_DocumentType)
{
    NewConfigBuilder builder;
    builder.document(type_name, doc_type_id);
    DocumentTypeRepo repo(builder.config());
    const auto *type = repo.getDocumentType(doc_type_id);
    ASSERT_TRUE(type != nullptr);
    EXPECT_TRUE(type->imported_field_names().empty());
    EXPECT_FALSE(type->has_imported_field_name("foo"));
}

TEST(DocumentTypeRepoTest, Configured_imported_field_names_are_available_in_the_DocumentType)
{
    const int type_2_id = doc_type_id + 1;
    // Note: we cheat a bit by specifying imported field names in types that have no
    // reference fields. Add to test if we add config read-time validation of this. :)
    NewConfigBuilder builder;
    // Type with one imported field
    builder.document(type_name, doc_type_id)
           .imported_field("my_cool_field");
    // Type with two imported fields
    builder.document(type_name_2, type_2_id)
           .imported_field("my_awesome_field")
           .imported_field("my_swag_field");

    DocumentTypeRepo repo(builder.config());
    const auto* type = repo.getDocumentType(doc_type_id);
    ASSERT_TRUE(type != nullptr);
    EXPECT_EQ(1u, type->imported_field_names().size());
    EXPECT_TRUE(type->has_imported_field_name("my_cool_field"));
    EXPECT_FALSE(type->has_imported_field_name("my_awesome_field"));

    type = repo.getDocumentType(type_2_id);
    ASSERT_TRUE(type != nullptr);
    EXPECT_EQ(2u, type->imported_field_names().size());
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

TEST(DocumentTypeRepoTest, Tensor_fields_have_tensor_types)
{
    NewConfigBuilder builder;
    auto& doc = builder.document(type_name, doc_type_id);
    doc.addTensorField("tensor1", "tensor(x[3])")
       .addTensorField("tensor2", "tensor(y{})")
       .addTensorField("tensor3", "tensor(x[3])");
    DocumentTypeRepo repo(builder.config());
    auto *docType = repo.getDocumentType(doc_type_id);
    ASSERT_TRUE(docType != nullptr);
    auto &tensorField1 = docType->getField("tensor1");
    auto &tensorField2 = docType->getField("tensor2");
    EXPECT_EQ("tensor(x[3])", asTensorDataType(tensorField1.getDataType()).getTensorType().to_spec());
    EXPECT_EQ("tensor(y{})", asTensorDataType(tensorField2.getDataType()).getTensorType().to_spec());
    auto &tensorField3 = docType->getField("tensor3");
    EXPECT_TRUE(&tensorField1.getDataType() == &tensorField3.getDataType());
    auto tensorFieldValue1 = tensorField1.getDataType().createFieldValue();
    EXPECT_TRUE(&tensorField1.getDataType() == tensorFieldValue1->getDataType());
}

GTEST_MAIN_RUN_ALL_TESTS()
