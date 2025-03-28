// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/test/fieldvalue_helpers.h>
#include <vespa/document/annotation/spanlist.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/document/base/testdocman.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/repo/fixedtyperepo.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/document/serialization/vespadocumentserializer.h>
#include <vespa/document/update/addvalueupdate.h>
#include <vespa/document/update/arithmeticvalueupdate.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/clearvalueupdate.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/update/documentupdateflags.h>
#include <vespa/document/update/fieldupdate.h>
#include <vespa/document/update/mapvalueupdate.h>
#include <vespa/document/update/removevalueupdate.h>
#include <vespa/document/update/tensor_add_update.h>
#include <vespa/document/update/tensor_modify_update.h>
#include <vespa/document/update/tensor_remove_update.h>
#include <vespa/document/update/valueupdate.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/test/value_compare.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/test/test_data.h>
#include <vespa/vespalib/testkit/test_path.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/util/exceptions.h>
#include <filesystem>
#include <fstream>
#include <unistd.h>

using namespace document::config_builder;

using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::ValueType;
using vespalib::nbostream;
using vespalib::test::TestDataBase;

namespace document {

namespace {

nbostream
serializeHEAD(const DocumentUpdate & update)
{
    nbostream stream;
    VespaDocumentSerializer serializer(stream);
    serializer.writeHEAD(update);
    return stream;
}

nbostream serialize(const ValueUpdate & update)
{
    nbostream stream;
    VespaDocumentSerializer serializer(stream);
    serializer.write(update);
    return stream;
}

nbostream serialize(const FieldUpdate & update)
{
    nbostream stream;
    VespaDocumentSerializer serializer(stream);
    serializer.write(update);
    return stream;
}

template<typename UpdateType>
void testRoundtripSerialize(const UpdateType& update, const DataType &type) {
    try{
        DocumentTypeRepo repo;
        nbostream stream = serialize(update);
        std::unique_ptr<UpdateType> copy(dynamic_cast<UpdateType*>(ValueUpdate::createInstance(repo, type, stream).release()));
        EXPECT_EQ(update, *copy);
    } catch (std::exception& e) {
            std::cerr << "Failed while processing update " << update << "\n";
    throw;
    }
}

}

class DocumentUpdateTest : public ::testing::Test, public vespalib::test::TestData<DocumentUpdateTest> {
protected:
    DocumentUpdateTest();
    ~DocumentUpdateTest() override;
    static void SetUpTestSuite();
    static void TearDownTestSuite();
};

DocumentUpdateTest::DocumentUpdateTest()
        : ::testing::Test(),
          vespalib::test::TestData<DocumentUpdateTest>()
{
}

DocumentUpdateTest::~DocumentUpdateTest() = default;

void
DocumentUpdateTest::SetUpTestSuite()
{
   setup_test_data(TEST_PATH("data"), "documentupdate-build-data");
   std::filesystem::create_directory(build_testdata());
}

void
DocumentUpdateTest::TearDownTestSuite()
{
    tear_down_test_data();
}

TEST_F(DocumentUpdateTest, testSimpleUsage)
{
    DocumenttypesConfigBuilderHelper builder;
    builder.document(42, "test",
                     Struct("test.header").addField("bytef", DataType::T_BYTE).addField("intf", DataType::T_INT),
                     Struct("test.body").addField("intarr", Array(DataType::T_INT)));
    DocumentTypeRepo repo(builder.config());
    const DocumentType* docType(repo.getDocumentType("test"));
    const DataType *arrayType = repo.getDataType(*docType, "Array<Int>");

        // Test that primitive value updates can be serialized
    testRoundtripSerialize(ClearValueUpdate(), *DataType::INT);
    testRoundtripSerialize(AssignValueUpdate(std::make_unique<IntFieldValue>(1)), *DataType::INT);
    testRoundtripSerialize(ArithmeticValueUpdate(ArithmeticValueUpdate::Div, 4.3), *DataType::FLOAT);
    testRoundtripSerialize(AddValueUpdate(std::make_unique<IntFieldValue>(1), 4), *arrayType);
    testRoundtripSerialize(RemoveValueUpdate(std::make_unique<IntFieldValue>(1)), *arrayType);

    FieldUpdate fieldUpdate(docType->getField("intf"));
    fieldUpdate.addUpdate(std::make_unique<AssignValueUpdate>(std::make_unique<IntFieldValue>(1)));
    nbostream stream = serialize(fieldUpdate);
    FieldUpdate fieldUpdateCopy(repo, *docType, stream);
    EXPECT_EQ(fieldUpdate, fieldUpdateCopy);

        // Test that a document update can be serialized
    DocumentUpdate docUpdate(repo, *docType, DocumentId("id:ns:test::1"));
    docUpdate.addUpdate(std::move(fieldUpdateCopy));
    nbostream docBuf = serializeHEAD(docUpdate);
    auto docUpdateCopy(DocumentUpdate::createHEAD(repo, docBuf));

        // Create a test document
    Document doc(repo, *docType, DocumentId("id:ns:test::1"));
    doc.setValue("bytef", ByteFieldValue::make(0));
    doc.setValue("intf", IntFieldValue::make(5));
    ArrayFieldValue array(*arrayType);
    array.add(IntFieldValue(3));
    array.add(IntFieldValue(7));
    doc.setValue("intarr", array);

        // Verify that we can apply simple updates to it
    {
        Document updated(doc);
        DocumentUpdate upd(repo, *docType, DocumentId("id:ns:test::1"));
        upd.addUpdate(FieldUpdate(docType->getField("intf")).addUpdate(std::make_unique<ClearValueUpdate>()));
        upd.applyTo(updated);
        EXPECT_NE(doc, updated);
        EXPECT_FALSE(updated.getValue("intf"));
    }
    {
        Document updated(doc);
        DocumentUpdate upd(repo, *docType, DocumentId("id:ns:test::1"));
        upd.addUpdate(FieldUpdate(docType->getField("intf")).addUpdate(std::make_unique<AssignValueUpdate>(std::make_unique<IntFieldValue>(15))));
        upd.applyTo(updated);
        EXPECT_NE(doc, updated);
        EXPECT_EQ(15, updated.getValue("intf")->getAsInt());
    }
    {
        Document updated(doc);
        DocumentUpdate upd(repo, *docType, DocumentId("id:ns:test::1"));
        upd.addUpdate(FieldUpdate(docType->getField("intf")).addUpdate(std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Add, 15)));
        upd.applyTo(updated);
        EXPECT_NE(doc, updated);
        EXPECT_EQ(20, updated.getValue("intf")->getAsInt());
    }
    {
        Document updated(doc);
        DocumentUpdate upd(repo, *docType, DocumentId("id:ns:test::1"));
        upd.addUpdate(FieldUpdate(docType->getField("intarr")).addUpdate(std::make_unique<AddValueUpdate>(std::make_unique<IntFieldValue>(4))));
        upd.applyTo(updated);
        EXPECT_NE(doc, updated);
        std::unique_ptr<ArrayFieldValue> val(dynamic_cast<ArrayFieldValue*>(updated.getValue("intarr").release()));
        ASSERT_EQ(size_t(3), val->size());
        EXPECT_EQ(4, (*val)[2].getAsInt());
    }
    {
        Document updated(doc);
        DocumentUpdate upd(repo, *docType, DocumentId("id:ns:test::1"));
        upd.addUpdate(FieldUpdate(docType->getField("intarr")).addUpdate(std::make_unique<RemoveValueUpdate>(std::make_unique<IntFieldValue>(3))));
        upd.applyTo(updated);
        EXPECT_NE(doc, updated);
        std::unique_ptr<ArrayFieldValue> val(dynamic_cast<ArrayFieldValue*>(updated.getValue("intarr").release()));
        ASSERT_EQ(size_t(1), val->size());
        EXPECT_EQ(7, (*val)[0].getAsInt());
    }
    {
        Document updated(doc);
        DocumentUpdate upd(repo, *docType, DocumentId("id:ns:test::1"));
        upd.addUpdate(FieldUpdate(docType->getField("bytef"))
                              .addUpdate(std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Add, 15)));
        upd.applyTo(updated);
        EXPECT_NE(doc, updated);
        EXPECT_EQ(15, (int) updated.getValue("bytef")->getAsByte());
    }
}

TEST_F(DocumentUpdateTest, testClearField)
{
    // Create a document.
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    doc->setValue(doc->getField("headerval"), IntFieldValue(4));
    EXPECT_EQ(4, doc->getValue("headerval")->getAsInt());

    // Apply an update.
    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(doc->getField("headerval")).addUpdate(std::make_unique<AssignValueUpdate>()))
        .applyTo(*doc);
    EXPECT_FALSE(doc->getValue("headerval"));
}

TEST_F(DocumentUpdateTest, testUpdateApplySingleValue)
{
    // Create a document.
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    doc->setValue(doc->getField("headerval"), IntFieldValue(4));
    EXPECT_EQ(4, doc->getValue("headerval")->getAsInt());

    // Apply an update.
    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(doc->getField("headerval")).addUpdate(std::make_unique<AssignValueUpdate>(std::make_unique<IntFieldValue>(9))))
        .applyTo(*doc);
    EXPECT_EQ(9, doc->getValue("headerval")->getAsInt());
}

TEST_F(DocumentUpdateTest, testUpdateArray)
{
    // Create a document.
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    EXPECT_EQ((document::FieldValue*)nullptr, doc->getValue(doc->getField("tags")).get());

    // Assign array field.
    auto myarray = std::make_unique<ArrayFieldValue>(doc->getType().getField("tags").getDataType());
    myarray->add(StringFieldValue("foo"));
	myarray->add(StringFieldValue("bar"));

    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(doc->getField("tags")).addUpdate(std::make_unique<AssignValueUpdate>(std::move(myarray))))
        .applyTo(*doc);
    auto fval1(doc->getAs<ArrayFieldValue>(doc->getField("tags")));
    ASSERT_EQ((size_t) 2, fval1->size());
    EXPECT_EQ(std::string("foo"), std::string((*fval1)[0].getAsString()));
    EXPECT_EQ(std::string("bar"), std::string((*fval1)[1].getAsString()));

    // Append array field
    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(doc->getField("tags"))
                   .addUpdate(std::make_unique<AddValueUpdate>(StringFieldValue::make("another")))
                   .addUpdate(std::make_unique<AddValueUpdate>(StringFieldValue::make("tag"))))
        .applyTo(*doc);
    std::unique_ptr<ArrayFieldValue>
        fval2(doc->getAs<ArrayFieldValue>(doc->getField("tags")));
    ASSERT_EQ((size_t) 4, fval2->size());
    EXPECT_EQ(std::string("foo"), std::string((*fval2)[0].getAsString()));
    EXPECT_EQ(std::string("bar"), std::string((*fval2)[1].getAsString()));
    EXPECT_EQ(std::string("another"), std::string((*fval2)[2].getAsString()));
    EXPECT_EQ(std::string("tag"), std::string((*fval2)[3].getAsString()));

    // Append single value.
    ASSERT_THROW(
        DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
            .addUpdate(FieldUpdate(doc->getField("tags"))
                       .addUpdate(std::make_unique<AssignValueUpdate>(StringFieldValue::make("THROW MEH!"))))
            .applyTo(*doc),
        std::exception) << "Expected exception when assigning a string value to an array field.";

    // Remove array field.
    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(doc->getField("tags"))
                   .addUpdate(std::make_unique<RemoveValueUpdate>(StringFieldValue::make("foo")))
                   .addUpdate(std::make_unique<RemoveValueUpdate>(StringFieldValue::make("tag"))))
        .applyTo(*doc);
    auto fval3(doc->getAs<ArrayFieldValue>(doc->getField("tags")));
    ASSERT_EQ((size_t) 2, fval3->size());
    EXPECT_EQ(std::string("bar"), std::string((*fval3)[0].getAsString()));
    EXPECT_EQ(std::string("another"), std::string((*fval3)[1].getAsString()));

    // Remove array from array.
    auto myarray2 = std::make_unique<ArrayFieldValue>(doc->getType().getField("tags").getDataType());
    myarray2->add(StringFieldValue("foo"));
    myarray2->add(StringFieldValue("bar"));
    ASSERT_THROW(
        DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
            .addUpdate(FieldUpdate(doc->getField("tags"))
                       .addUpdate(std::make_unique<RemoveValueUpdate>(std::move(myarray2))))
            .applyTo(*doc),
        std::exception) << "Expected exception when removing an array from a string array.";
}

std::unique_ptr<ValueUpdate>
createAddUpdate(std::string_view key, int weight) {
    auto upd = std::make_unique<AddValueUpdate>(StringFieldValue::make(key));
    upd->setWeight(weight);
    return upd;
}

std::unique_ptr<ValueUpdate>
createAddUpdate(int key, int weight) {
    auto upd = std::make_unique<AddValueUpdate>(std::make_unique<IntFieldValue>(key));
    upd->setWeight(weight);
    return upd;
}

TEST_F(DocumentUpdateTest, testUpdateWeightedSet)
{
    // Create a test document
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    const Field& field(doc->getType().getField("stringweightedset"));
    EXPECT_EQ(nullptr, doc->getValue(field).get());
	
    // Assign weightedset field
    auto wset =std::make_unique<WeightedSetFieldValue>(field.getDataType());
    wset->add(StringFieldValue("foo"), 3);
    wset->add(StringFieldValue("bar"), 14);
    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field).addUpdate(std::make_unique<AssignValueUpdate>(std::move(wset))))
        .applyTo(*doc);
    auto fval1(doc->getAs<WeightedSetFieldValue>(field));
    ASSERT_EQ((size_t) 2, fval1->size());
    EXPECT_TRUE(fval1->contains(StringFieldValue("foo")));
    EXPECT_NE(fval1->find(StringFieldValue("foo")), fval1->end());
    EXPECT_EQ(3, fval1->get(StringFieldValue("foo"), 0));
    EXPECT_TRUE(fval1->contains(StringFieldValue("bar")));
    EXPECT_NE(fval1->find(StringFieldValue("bar")), fval1->end());
    EXPECT_EQ(14, fval1->get(StringFieldValue("bar"), 0));

    // Do a second assign
    auto wset2 = std::make_unique<WeightedSetFieldValue>(field.getDataType());
    wset2->add(StringFieldValue("foo"), 16);
    wset2->add(StringFieldValue("bar"), 24);
    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field)
                   .addUpdate(std::make_unique<AssignValueUpdate>(std::move(wset2))))
        .applyTo(*doc);
    auto fval2(doc->getAs<WeightedSetFieldValue>(field));
    ASSERT_EQ((size_t) 2, fval2->size());
    EXPECT_TRUE(fval2->contains(StringFieldValue("foo")));
    EXPECT_NE(fval2->find(StringFieldValue("foo")), fval1->end());
    EXPECT_EQ(16, fval2->get(StringFieldValue("foo"), 0));
    EXPECT_TRUE(fval2->contains(StringFieldValue("bar")));
    EXPECT_NE(fval2->find(StringFieldValue("bar")), fval1->end());
    EXPECT_EQ(24, fval2->get(StringFieldValue("bar"), 0));

    // Append weighted field
    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field)
                   .addUpdate(createAddUpdate("foo", 3))
                   .addUpdate(createAddUpdate("too", 14)))
        .applyTo(*doc);
    std::unique_ptr<WeightedSetFieldValue>
        fval3(doc->getAs<WeightedSetFieldValue>(field));
    ASSERT_EQ((size_t) 3, fval3->size());
    EXPECT_TRUE(fval3->contains(StringFieldValue("foo")));
    EXPECT_EQ(3, fval3->get(StringFieldValue("foo"), 0));
    EXPECT_TRUE(fval3->contains(StringFieldValue("bar")));
    EXPECT_EQ(24, fval3->get(StringFieldValue("bar"), 0));
    EXPECT_TRUE(fval3->contains(StringFieldValue("too")));
    EXPECT_EQ(14, fval3->get(StringFieldValue("too"), 0));

    // Remove weighted field
    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field)
                   .addUpdate(std::make_unique<RemoveValueUpdate>(StringFieldValue::make("foo")))
                   .addUpdate(std::make_unique<RemoveValueUpdate>(StringFieldValue::make("too"))))
        .applyTo(*doc);
    auto fval4(doc->getAs<WeightedSetFieldValue>(field));
    ASSERT_EQ((size_t) 1, fval4->size());
    EXPECT_FALSE(fval4->contains(StringFieldValue("foo")));
    EXPECT_TRUE(fval4->contains(StringFieldValue("bar")));
    EXPECT_EQ(24, fval4->get(StringFieldValue("bar"), 0));
    EXPECT_FALSE(fval4->contains(StringFieldValue("too")));
}

namespace {

struct WeightedSetAutoCreateFixture
{
    DocumentTypeRepo repo;
    const DocumentType* docType;
    Document doc;
    const Field& field;
    DocumentUpdate update;

    ~WeightedSetAutoCreateFixture();
    WeightedSetAutoCreateFixture();

    void applyUpdateToDocument() {
        update.applyTo(doc);
    }

    static DocumenttypesConfig makeConfig() {
        DocumenttypesConfigBuilderHelper builder;
        // T_TAG is an alias for a weighted set with create-if-non-existing
        // and remove-if-zero attributes set. Attempting to explicitly create
        // a field matching those characteristics will in fact fail with a
        // redefinition error.
        builder.document(42, "test", Struct("test.header").addField("strwset", DataType::T_TAG), Struct("test.body"));
        return builder.config();
    }
};

WeightedSetAutoCreateFixture::~WeightedSetAutoCreateFixture() = default;
WeightedSetAutoCreateFixture::WeightedSetAutoCreateFixture()
    : repo(makeConfig()),
      docType(repo.getDocumentType("test")),
      doc(repo, *docType, DocumentId("id:ns:test::1")),
      field(docType->getField("strwset")),
      update(repo, *docType, DocumentId("id:ns:test::1"))
{
    update.addUpdate(FieldUpdate(field)
                             .addUpdate(std::make_unique<MapValueUpdate>(StringFieldValue::make("foo"),
                                                       std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Add, 1))));
}
} // anon ns

TEST_F(DocumentUpdateTest, testIncrementNonExistingAutoCreateWSetField)
{
    WeightedSetAutoCreateFixture fixture;

    fixture.applyUpdateToDocument();

    std::unique_ptr<WeightedSetFieldValue> ws(
            fixture.doc.getAs<WeightedSetFieldValue>(fixture.field));
    ASSERT_EQ(size_t(1), ws->size());
    EXPECT_TRUE(ws->contains(StringFieldValue("foo")));
    EXPECT_EQ(1, ws->get(StringFieldValue("foo"), 0));
}

TEST_F(DocumentUpdateTest, testIncrementExistingWSetField)
{
    WeightedSetAutoCreateFixture fixture;
    {
        WeightedSetFieldValue wset(fixture.field.getDataType());
        wset.add(StringFieldValue("bar"), 14);
        fixture.doc.setValue(fixture.field, wset);
    }
    fixture.applyUpdateToDocument();

    auto ws(fixture.doc.getAs<WeightedSetFieldValue>(fixture.field));
    ASSERT_EQ(size_t(2), ws->size());
    EXPECT_TRUE(ws->contains(StringFieldValue("foo")));
    EXPECT_EQ(1, ws->get(StringFieldValue("foo"), 0));
}

TEST_F(DocumentUpdateTest, testIncrementWithZeroResultWeightIsRemoved)
{
    WeightedSetAutoCreateFixture fixture;
    fixture.update.addUpdate(FieldUpdate(fixture.field)
            .addUpdate(std::make_unique<MapValueUpdate>(StringFieldValue::make("baz"),
                                      std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Add, 0))));

    fixture.applyUpdateToDocument();

    auto ws(fixture.doc.getAs<WeightedSetFieldValue>(fixture.field));
    ASSERT_EQ(size_t(1), ws->size());
    EXPECT_TRUE(ws->contains(StringFieldValue("foo")));
    EXPECT_FALSE(ws->contains(StringFieldValue("baz")));
}

TEST_F(DocumentUpdateTest, testReadSerializedFile)
{
    // Reads a file serialized from java
    const std::string file_name = source_testdata() + "/crossplatform-java-cpp-doctypes.cfg";
    DocumentTypeRepo repo(readDocumenttypesConfig(file_name));

    auto is = read_buffer_from_file(source_testdata() + "/serializeupdatejava.dat");
    DocumentUpdate::UP updp(DocumentUpdate::createHEAD(repo, is));
    DocumentUpdate& upd(*updp);

    const DocumentType *type = repo.getDocumentType("serializetest");
    EXPECT_EQ(DocumentId("id:ns:serializetest::update"), upd.getId());
    EXPECT_EQ(*type, upd.getType());

    // Verify assign value update.
    const FieldUpdate & serField1 = upd.getUpdates()[1];
    EXPECT_EQ(serField1.getField().getId(), type->getField("intfield").getId());

    const ValueUpdate* serValue = &serField1[0];
    ASSERT_EQ(serValue->getType(), ValueUpdate::Assign);

    const AssignValueUpdate* assign(static_cast<const AssignValueUpdate*>(serValue));
    EXPECT_EQ(IntFieldValue(4), static_cast<const IntFieldValue&>(assign->getValue()));

    // Verify clear field update.
    const FieldUpdate & serField2 = upd.getUpdates()[2];
    EXPECT_EQ(serField2.getField().getId(), type->getField("floatfield").getId());

    serValue = &serField2[0];
    EXPECT_EQ(serValue->getType(), ValueUpdate::Clear);
    EXPECT_EQ(ValueUpdate::Clear, serValue->getType());

    // Verify add value update.
    const FieldUpdate & serField3 = upd.getUpdates()[0];
    EXPECT_EQ(serField3.getField().getId(), type->getField("arrayoffloatfield").getId());

    serValue = &serField3[0];
    ASSERT_EQ(serValue->getType(), ValueUpdate::Add);

    const AddValueUpdate* add = static_cast<const AddValueUpdate*>(serValue);
    const FieldValue* value = &add->getValue();
    EXPECT_TRUE(value->isA(FieldValue::Type::FLOAT));
    EXPECT_FLOAT_EQ(value->getAsFloat(), 5.00f);

    serValue = &serField3[1];
    ASSERT_EQ(serValue->getType(), ValueUpdate::Add);

    add = static_cast<const AddValueUpdate*>(serValue);
    value = &add->getValue();
    EXPECT_TRUE(value->isA(FieldValue::Type::FLOAT));
    EXPECT_FLOAT_EQ(value->getAsFloat(), 4.23f);

    serValue = &serField3[2];
    ASSERT_EQ(serValue->getType(), ValueUpdate::Add);

    add = static_cast<const AddValueUpdate*>(serValue);
    value = &add->getValue();
    EXPECT_TRUE(value->isA(FieldValue::Type::FLOAT));
    EXPECT_FLOAT_EQ(value->getAsFloat(), -1.00f);

}

TEST_F(DocumentUpdateTest, testGenerateSerializedFile)
{
    // Tests nothing, only generates a file for java test
    const std::string cfg_file_name = source_testdata() + "/crossplatform-java-cpp-doctypes.cfg";
    DocumentTypeRepo repo(readDocumenttypesConfig(cfg_file_name));

    const DocumentType *type(repo.getDocumentType("serializetest"));
    DocumentUpdate upd(repo, *type, DocumentId("id:ns:serializetest::update"));
    upd.addUpdate(FieldUpdate(type->getField("intfield"))
		  .addUpdate(std::make_unique<AssignValueUpdate>(std::make_unique<IntFieldValue>(4))));
    upd.addUpdate(FieldUpdate(type->getField("floatfield"))
		  .addUpdate(std::make_unique<AssignValueUpdate>(std::make_unique<FloatFieldValue>(1.00f))));
    upd.addUpdate(FieldUpdate(type->getField("arrayoffloatfield"))
		  .addUpdate(std::make_unique<AddValueUpdate>(std::make_unique<FloatFieldValue>(5.00f)))
		  .addUpdate(std::make_unique<AddValueUpdate>(std::make_unique<FloatFieldValue>(4.23f)))
		  .addUpdate(std::make_unique<AddValueUpdate>(std::make_unique<FloatFieldValue>(-1.00f))));
    upd.addUpdate(FieldUpdate(type->getField("intfield"))
          .addUpdate(std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Add, 3)));
    upd.addUpdate(FieldUpdate(type->getField("wsfield"))
          .addUpdate(std::make_unique<MapValueUpdate>(StringFieldValue::make("foo"),
                        std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Add, 2)))
          .addUpdate(std::make_unique<MapValueUpdate>(StringFieldValue::make("foo"),
                        std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Mul, 2))));
    nbostream buf(serializeHEAD(upd));
    std::string file_name("serializeupdatecpp.dat");
    write_buffer_to_file(buf, build_testdata() + "/" + file_name);
    ASSERT_NO_FATAL_FAILURE(remove_unchanged_build_testdata_file_or_fail(buf, file_name));
}


TEST_F(DocumentUpdateTest, testSetBadFieldTypes)
{
    // Create a test document
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    EXPECT_EQ((document::FieldValue*)nullptr, doc->getValue(doc->getField("headerval")).get());

    // Assign a float value to an int field.
    DocumentUpdate update(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    ASSERT_THROW(
        update.addUpdate(FieldUpdate(doc->getField("headerval"))
                 .addUpdate(std::make_unique<AssignValueUpdate>(std::make_unique<FloatFieldValue>(4.00f)))),
        std::exception) << "Expected exception when adding a float to an int field.";

    update.applyTo(*doc);

    // Verify that the field is NOT set in the document.
    EXPECT_EQ((document::FieldValue*)nullptr,
			 doc->getValue(doc->getField("headerval")).get());
}

TEST_F(DocumentUpdateTest, testUpdateApplyNoParams)
{
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    EXPECT_EQ((document::FieldValue*)nullptr, doc->getValue(doc->getField("tags")).get());

    DocumentUpdate update(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    update.addUpdate(FieldUpdate(doc->getField("tags")).addUpdate(std::make_unique<AssignValueUpdate>()));

    update.applyTo(*doc);

    // Verify that the field was cleared in the document.
    EXPECT_FALSE(doc->hasValue(doc->getField("tags")));
}

TEST_F(DocumentUpdateTest, testUpdateApplyNoArrayValues)
{
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    const Field &field(doc->getType().getField("tags"));
    EXPECT_EQ(nullptr, doc->getValue(field).get());

    // Assign array field with no array values = empty array
    DocumentUpdate update(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    update.addUpdate(FieldUpdate(field)
                     .addUpdate(std::make_unique<AssignValueUpdate>(std::make_unique<ArrayFieldValue>(field.getDataType()))));

    update.applyTo(*doc);

    // Verify that the field was set in the document
    std::unique_ptr<ArrayFieldValue> fval(doc->getAs<ArrayFieldValue>(field));
    ASSERT_TRUE(fval.get());
    EXPECT_EQ((size_t) 0, fval->size());
}

TEST_F(DocumentUpdateTest, testUpdateArrayEmptyParamValue)
{
    // Create a test document.
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    const Field &field(doc->getType().getField("tags"));
    EXPECT_EQ(nullptr, doc->getValue(field).get());

    // Assign array field with no array values = empty array.
    DocumentUpdate update(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    update.addUpdate(FieldUpdate(field).addUpdate(std::make_unique<AssignValueUpdate>(std::make_unique<ArrayFieldValue>(field.getDataType()))));
    update.applyTo(*doc);

    // Verify that the field was set in the document.
    std::unique_ptr<ArrayFieldValue> fval1(doc->getAs<ArrayFieldValue>(field));
    ASSERT_TRUE(fval1.get());
    EXPECT_EQ((size_t) 0, fval1->size());

    // Remove array field.
    DocumentUpdate update2(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    update2.addUpdate(FieldUpdate(field).addUpdate(std::make_unique<ClearValueUpdate>()));
    update2.applyTo(*doc);

    // Verify that the field was cleared in the document.
    std::unique_ptr<ArrayFieldValue> fval2(doc->getAs<ArrayFieldValue>(field));
    EXPECT_FALSE(fval2);
}

TEST_F(DocumentUpdateTest, testUpdateWeightedSetEmptyParamValue)
{
    // Create a test document
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    const Field &field(doc->getType().getField("stringweightedset"));
    EXPECT_EQ(nullptr, doc->getValue(field).get());

    // Assign weighted set with no items = empty set.
    DocumentUpdate update(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    update.addUpdate(FieldUpdate(field).addUpdate(std::make_unique<AssignValueUpdate>(std::make_unique<WeightedSetFieldValue>(field.getDataType()))));
    update.applyTo(*doc);

    // Verify that the field was set in the document.
    auto fval1(doc->getAs<WeightedSetFieldValue>(field));
    ASSERT_TRUE(fval1.get());
    EXPECT_EQ((size_t) 0, fval1->size());

    // Remove weighted set field.
    DocumentUpdate update2(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    update2.addUpdate(FieldUpdate(field).addUpdate(std::make_unique<ClearValueUpdate>()));
    update2.applyTo(*doc);

    // Verify that the field was cleared in the document.
    auto fval2(doc->getAs<WeightedSetFieldValue>(field));
    EXPECT_FALSE(fval2);
}

TEST_F(DocumentUpdateTest, testUpdateArrayWrongSubtype)
{
    // Create a test document
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    const Field &field(doc->getType().getField("tags"));
    EXPECT_EQ(nullptr, doc->getValue(field).get());

    // Assign int values to string array.
    DocumentUpdate update(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    ASSERT_THROW(
        update.addUpdate(FieldUpdate(field)
                 .addUpdate(std::make_unique<AddValueUpdate>(std::make_unique<IntFieldValue>(123)))
                 .addUpdate(std::make_unique<AddValueUpdate>(std::make_unique<IntFieldValue>(456)))),
        std::exception) << "Expected exception when adding wrong type.";

    // Apply update
    update.applyTo(*doc);

    // Verify that the field was NOT set in the document
    FieldValue::UP fval(doc->getValue(field));
    EXPECT_EQ(nullptr, fval.get());
}

TEST_F(DocumentUpdateTest, testUpdateWeightedSetWrongSubtype)
{
    // Create a test document
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    const Field &field(doc->getType().getField("stringweightedset"));
    EXPECT_EQ(nullptr, doc->getValue(field).get());

    // Assign int values to string array.
    DocumentUpdate update(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    ASSERT_THROW(
        update.addUpdate(FieldUpdate(field)
                 .addUpdate(createAddUpdate(123, 1000))
                 .addUpdate(createAddUpdate(456, 2000))),
        std::exception) << "Expected exception when adding wrong type.";

    // Apply update
    update.applyTo(*doc);

    // Verify that the field was NOT set in the document
    FieldValue::UP fval(doc->getValue(field));
    EXPECT_EQ(nullptr, fval.get());
}

TEST_F(DocumentUpdateTest, testMapValueUpdate)
{
    // Create a test document
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    const Field &field1 = doc->getField("stringweightedset");
    const Field &field2 = doc->getField("stringweightedset2");
    WeightedSetFieldValue wsval1(field1.getDataType());
    WeightedSetFieldValue wsval2(field2.getDataType());
    doc->setValue(field1, wsval1);
    doc->setValue(field2, wsval2);

    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field1)
                   .addUpdate(std::make_unique<MapValueUpdate>(StringFieldValue::make("banana"),
                                             std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Add, 1.0))))
        .applyTo(*doc);
    std::unique_ptr<WeightedSetFieldValue> fv1 =
        doc->getAs<WeightedSetFieldValue>(field1);
    EXPECT_EQ(0, fv1->size());

    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field2)
                   .addUpdate(std::make_unique<MapValueUpdate>(StringFieldValue::make("banana"),
                                             std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Add, 1.0))))
        .applyTo(*doc);
    auto fv2 = doc->getAs<WeightedSetFieldValue>(field2);
    EXPECT_EQ(1, fv2->size());

    EXPECT_EQ(fv1->find(StringFieldValue("apple")), fv1->end());
    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field1).addUpdate(std::make_unique<ClearValueUpdate>()))
        .applyTo(*doc);

    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field1).addUpdate(createAddUpdate("apple", 1)))
        .applyTo(*doc);

    auto fval3(doc->getAs<WeightedSetFieldValue>(field1));
    EXPECT_NE(fval3->find(StringFieldValue("apple")), fval3->end());
    EXPECT_EQ(1, fval3->get(StringFieldValue("apple")));

    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field2).addUpdate(createAddUpdate("apple", 1)))
        .applyTo(*doc);

    auto fval3b(doc->getAs<WeightedSetFieldValue>(field2));
    EXPECT_NE(fval3b->find(StringFieldValue("apple")), fval3b->end());
    EXPECT_EQ(1, fval3b->get(StringFieldValue("apple")));

    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field1)
                   .addUpdate(std::make_unique<MapValueUpdate>(StringFieldValue::make("apple"),
                                             std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Sub, 1.0))))
        .applyTo(*doc);

    auto fv3 = doc->getAs<WeightedSetFieldValue>(field1);
    EXPECT_NE(fv3->find(StringFieldValue("apple")), fv3->end());
    EXPECT_EQ(0, fv3->get(StringFieldValue("apple")));

    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field2)
                   .addUpdate(std::make_unique<MapValueUpdate>(StringFieldValue::make("apple"),
                                             std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Sub, 1.0))))
        .applyTo(*doc);

    auto fv4 = doc->getAs<WeightedSetFieldValue>(field2);
    EXPECT_EQ(fv4->find(StringFieldValue("apple")), fv4->end());
}

std::unique_ptr<vespalib::eval::Value>
makeTensor(const TensorSpec &spec)
{
    return SimpleValue::from_spec(spec);
}

std::unique_ptr<TensorFieldValue>
makeTensorFieldValue(const TensorSpec &spec, const TensorDataType &dataType)
{
    auto tensor = makeTensor(spec);
    auto result = std::make_unique<TensorFieldValue>(dataType);
    *result = std::move(tensor);
    return result;
}

const vespalib::eval::Value &asTensor(const FieldValue &fieldValue) {
    auto &tensorFieldValue = dynamic_cast<const TensorFieldValue &>(fieldValue);
    const auto* tensor = tensorFieldValue.getAsTensorPtr();
    assert(tensor);
    return *tensor;
}

struct TensorUpdateFixture {
    TestDocMan docMan;
    Document::UP emptyDoc;
    Document updatedDoc;
    std::string fieldName;
    const TensorDataType &tensorDataType;
    std::string tensorType;

    const TensorDataType &extractTensorDataType() {
        const auto &dataType = emptyDoc->getField(fieldName).getDataType();
        return dynamic_cast<const TensorDataType &>(dataType);
    }

    const Field &getNonTensorField() {
        return emptyDoc->getField("title");
    }

    TensorUpdateFixture(const std::string &fieldName_ = "sparse_tensor")
        : docMan(),
          emptyDoc(docMan.createDocument()),
          updatedDoc(*emptyDoc),
          fieldName(fieldName_),
          tensorDataType(extractTensorDataType()),
          tensorType(tensorDataType.getTensorType().to_spec())
    {
        EXPECT_FALSE(emptyDoc->getValue(fieldName));
    }
    ~TensorUpdateFixture() {}

    TensorSpec spec() {
        return TensorSpec(tensorType);
    }

    FieldValue::UP getTensor() {
        return updatedDoc.getValue(fieldName);
    }

    void setTensor(const TensorFieldValue &tensorValue) {
        updatedDoc.setValue(updatedDoc.getField(fieldName), tensorValue);
        assertDocumentUpdated();
    }

    void setTensor(const TensorSpec &spec) {
        setTensor(*makeTensor(spec));
    }

    std::unique_ptr<TensorFieldValue> makeTensor(const TensorSpec &spec) {
        return makeTensorFieldValue(spec, tensorDataType);
    }

    std::unique_ptr<TensorFieldValue> makeBaselineTensor() {
        return makeTensor(spec().add({{"x", "a"}}, 2)
                                  .add({{"x", "b"}}, 3));
    }

    void applyUpdate(std::unique_ptr<ValueUpdate> update) {
        DocumentUpdate docUpdate(docMan.getTypeRepo(), *emptyDoc->getDataType(), emptyDoc->getId());
        docUpdate.addUpdate(FieldUpdate(docUpdate.getType().getField(fieldName)).addUpdate(std::move(update)));
        docUpdate.applyTo(updatedDoc);
    }

    void assertDocumentUpdated() {
        EXPECT_NE(*emptyDoc, updatedDoc);
    }

    void assertDocumentNotUpdated() {
        EXPECT_EQ(*emptyDoc, updatedDoc);
    }

    void assertTensor(const TensorFieldValue &expTensorValue) {
        auto actTensorValue = getTensor();
        ASSERT_TRUE(actTensorValue);
        EXPECT_EQ(*actTensorValue, expTensorValue);
        auto &actTensor = asTensor(*actTensorValue);
        auto &expTensor = asTensor(expTensorValue);
        EXPECT_EQ(actTensor, expTensor);
    }

    void assertTensorNull() {
        auto field = getTensor();
        auto tensor_field = dynamic_cast<TensorFieldValue*>(field.get());
        ASSERT_TRUE(tensor_field);
        EXPECT_TRUE(tensor_field->getAsTensorPtr() == nullptr);
    }

    void assertTensor(const TensorSpec &expSpec) {
        auto expTensor = makeTensor(expSpec);
        assertTensor(*expTensor);
    }

    void assertApplyUpdate(const TensorSpec &initialTensor,
                           std::unique_ptr<ValueUpdate> update,
                           const TensorSpec &expTensor) {
        setTensor(initialTensor);
        applyUpdate(std::move(update));
        assertDocumentUpdated();
        assertTensor(expTensor);
    }

    void assertApplyUpdateNonExisting(std::unique_ptr<ValueUpdate> update,
                                      const TensorSpec &expTensor) {
        applyUpdate(std::move(update));
        assertDocumentUpdated();
        assertTensor(expTensor);
    }

    void assertApplyUpdateNonExisting(std::unique_ptr<ValueUpdate> update) {
        applyUpdate(std::move(update));
        assertDocumentUpdated();
        assertTensorNull();
    }

    template <typename ValueUpdateType>
    void assertRoundtripSerialize(const ValueUpdateType &valueUpdate) {
        testRoundtripSerialize(valueUpdate, tensorDataType);
    }

    void assertThrowOnNonTensorField(const ValueUpdate &update) {
        ASSERT_THROW(update.checkCompatibility(getNonTensorField()),
                     vespalib::IllegalArgumentException);
        StringFieldValue value("my value");
        ASSERT_THROW(update.applyTo(value),
                     vespalib::IllegalStateException);
    }

};

TEST_F(DocumentUpdateTest, tensor_assign_update_can_be_applied)
{
    TensorUpdateFixture f;
    f.applyUpdate(std::make_unique<AssignValueUpdate>(f.makeBaselineTensor()));
    f.assertDocumentUpdated();
    f.assertTensor(*f.makeBaselineTensor());
}

TEST_F(DocumentUpdateTest, tensor_clear_update_can_be_applied)
{
    TensorUpdateFixture f;
    f.setTensor(*f.makeBaselineTensor());
    f.applyUpdate(std::make_unique<ClearValueUpdate>());
    f.assertDocumentNotUpdated();
    EXPECT_FALSE(f.getTensor());
}

TEST_F(DocumentUpdateTest, tensor_add_update_can_be_applied)
{
    TensorUpdateFixture f;
    f.assertApplyUpdate(f.spec().add({{"x", "a"}}, 2)
                                .add({{"x", "b"}}, 3),

                        std::make_unique<TensorAddUpdate>(f.makeTensor(f.spec().add({{"x", "b"}}, 5)
                                                             .add({{"x", "c"}}, 7))),

                        f.spec().add({{"x", "a"}}, 2)
                                .add({{"x", "b"}}, 5)
                                .add({{"x", "c"}}, 7));
}

TEST_F(DocumentUpdateTest, tensor_add_update_can_be_applied_to_nonexisting_tensor)
{
    TensorUpdateFixture f;
    f.assertApplyUpdateNonExisting(std::make_unique<TensorAddUpdate>(f.makeTensor(f.spec().add({{"x", "b"}}, 5)
                                                                        .add({{"x", "c"}}, 7))),

                        f.spec().add({{"x", "b"}}, 5)
                                .add({{"x", "c"}}, 7));
}

TEST_F(DocumentUpdateTest, tensor_remove_update_can_be_applied)
{
    TensorUpdateFixture f;
    f.assertApplyUpdate(f.spec().add({{"x", "a"}}, 2)
                                .add({{"x", "b"}}, 3),

                        std::make_unique<TensorRemoveUpdate>(f.makeTensor(f.spec().add({{"x", "b"}}, 1))),

                        f.spec().add({{"x", "a"}}, 2));
}

TEST_F(DocumentUpdateTest, tensor_remove_update_can_be_applied_to_nonexisting_tensor)
{
    TensorUpdateFixture f;
    f.assertApplyUpdateNonExisting(std::make_unique<TensorRemoveUpdate>(f.makeTensor(f.spec().add({{"x", "b"}}, 1))));
}

TEST_F(DocumentUpdateTest, tensor_modify_update_can_be_applied)
{
    TensorUpdateFixture f;
    auto baseLine = f.spec().add({{"x", "a"}}, 2)
                            .add({{"x", "b"}}, 3);

    f.assertApplyUpdate(baseLine,
                        std::make_unique<TensorModifyUpdate>(TensorModifyUpdate::Operation::REPLACE,
                                           f.makeTensor(f.spec().add({{"x", "b"}}, 5)
                                                                .add({{"x", "c"}}, 7))),
                        f.spec().add({{"x", "a"}}, 2)
                                .add({{"x", "b"}}, 5));

    f.assertApplyUpdate(baseLine,
                        std::make_unique<TensorModifyUpdate>(TensorModifyUpdate::Operation::ADD,
                                           f.makeTensor(f.spec().add({{"x", "b"}}, 5))),
                        f.spec().add({{"x", "a"}}, 2)
                                .add({{"x", "b"}}, 8));

    f.assertApplyUpdate(baseLine,
                        std::make_unique<TensorModifyUpdate>(TensorModifyUpdate::Operation::MULTIPLY,
                                           f.makeTensor(f.spec().add({{"x", "b"}}, 5))),
                        f.spec().add({{"x", "a"}}, 2)
                                .add({{"x", "b"}}, 15));
}

TEST_F(DocumentUpdateTest, tensor_modify_update_with_create_non_existing_cells_can_be_applied)
{
    TensorUpdateFixture f;
    auto baseLine = f.spec().add({{"x", "a"}}, 2)
                            .add({{"x", "b"}}, 3);

    f.assertApplyUpdate(baseLine,
                        std::make_unique<TensorModifyUpdate>(TensorModifyUpdate::Operation::ADD,
                                                             f.makeTensor(f.spec().add({{"x", "b"}}, 5).add({{"x", "c"}}, 6)), 0.0),
                        f.spec().add({{"x", "a"}}, 2)
                                .add({{"x", "b"}}, 8)
                                .add({{"x", "c"}}, 6));
}

TEST_F(DocumentUpdateTest, tensor_modify_update_is_ignored_when_applied_to_nonexisting_tensor)
{
    TensorUpdateFixture f;
    f.assertApplyUpdateNonExisting(std::make_unique<TensorModifyUpdate>(TensorModifyUpdate::Operation::ADD,
                                                      f.makeTensor(f.spec().add({{"x", "b"}}, 5))));
}

TEST_F(DocumentUpdateTest, tensor_modify_update_with_create_non_existing_cells_is_applied_to_nonexisting_tensor)
{
    TensorUpdateFixture f;
    f.assertApplyUpdateNonExisting(std::make_unique<TensorModifyUpdate>(TensorModifyUpdate::Operation::ADD,
                                                                        f.makeTensor(f.spec().add({{"x", "b"}}, 5)
                                                                                             .add({{"x", "c"}}, 6)), 0.0),
                                   f.spec().add({{"x", "b"}}, 5)
                                           .add({{"x", "c"}}, 6));
}

TEST_F(DocumentUpdateTest, tensor_assign_update_can_be_roundtrip_serialized)
{
    TensorUpdateFixture f;
    f.assertRoundtripSerialize(AssignValueUpdate(f.makeBaselineTensor()));
}

TEST_F(DocumentUpdateTest, tensor_add_update_can_be_roundtrip_serialized)
{
    TensorUpdateFixture f;
    f.assertRoundtripSerialize(TensorAddUpdate(f.makeBaselineTensor()));
}

TEST_F(DocumentUpdateTest, tensor_remove_update_can_be_roundtrip_serialized)
{
    TensorUpdateFixture f;
    f.assertRoundtripSerialize(TensorRemoveUpdate(f.makeBaselineTensor()));
}

TEST_F(DocumentUpdateTest, tensor_remove_update_with_not_fully_specified_address_can_be_roundtrip_serialized)
{
    TensorUpdateFixture f("sparse_xy_tensor");
    TensorDataType type(ValueType::from_spec("tensor(y{})"));
    f.assertRoundtripSerialize(TensorRemoveUpdate(
            makeTensorFieldValue(TensorSpec("tensor(y{})").add({{"y", "a"}}, 1), type)));
}

TEST_F(DocumentUpdateTest, tensor_remove_update_on_float_tensor_can_be_roundtrip_serialized)
{
    TensorUpdateFixture f("sparse_float_tensor");
    f.assertRoundtripSerialize(TensorRemoveUpdate(f.makeBaselineTensor()));
}

TEST_F(DocumentUpdateTest, tensor_modify_update_can_be_roundtrip_serialized)
{
    TensorUpdateFixture f;
    f.assertRoundtripSerialize(TensorModifyUpdate(TensorModifyUpdate::Operation::REPLACE, f.makeBaselineTensor()));
    f.assertRoundtripSerialize(TensorModifyUpdate(TensorModifyUpdate::Operation::ADD, f.makeBaselineTensor()));
    f.assertRoundtripSerialize(TensorModifyUpdate(TensorModifyUpdate::Operation::MULTIPLY, f.makeBaselineTensor()));
    f.assertRoundtripSerialize(TensorModifyUpdate(TensorModifyUpdate::Operation::REPLACE, f.makeBaselineTensor(), 0.0));
    f.assertRoundtripSerialize(TensorModifyUpdate(TensorModifyUpdate::Operation::ADD, f.makeBaselineTensor(), 0.0));
    f.assertRoundtripSerialize(TensorModifyUpdate(TensorModifyUpdate::Operation::MULTIPLY, f.makeBaselineTensor(), 1.0));
}

TEST_F(DocumentUpdateTest, tensor_modify_update_on_float_tensor_can_be_roundtrip_serialized)
{
    TensorUpdateFixture f("sparse_float_tensor");
    f.assertRoundtripSerialize(TensorModifyUpdate(TensorModifyUpdate::Operation::REPLACE, f.makeBaselineTensor()));
    f.assertRoundtripSerialize(TensorModifyUpdate(TensorModifyUpdate::Operation::ADD, f.makeBaselineTensor()));
    f.assertRoundtripSerialize(TensorModifyUpdate(TensorModifyUpdate::Operation::MULTIPLY, f.makeBaselineTensor()));
    f.assertRoundtripSerialize(TensorModifyUpdate(TensorModifyUpdate::Operation::REPLACE, f.makeBaselineTensor(), 0.0));
    f.assertRoundtripSerialize(TensorModifyUpdate(TensorModifyUpdate::Operation::ADD, f.makeBaselineTensor(), 0.0));
    f.assertRoundtripSerialize(TensorModifyUpdate(TensorModifyUpdate::Operation::MULTIPLY, f.makeBaselineTensor(), 1.0));
}

TEST_F(DocumentUpdateTest, tensor_modify_update_on_dense_tensor_can_be_roundtrip_serialized)
{
    TensorUpdateFixture f("dense_tensor");
    std::string sparseType("tensor(x{})");
    TensorDataType sparseTensorType(ValueType::from_spec(sparseType));
    auto sparseTensor = makeTensorFieldValue(TensorSpec(sparseType).add({{"x","0"}}, 2), sparseTensorType);
    f.assertRoundtripSerialize(TensorModifyUpdate(TensorModifyUpdate::Operation::REPLACE, std::move(sparseTensor)));
}

TEST_F(DocumentUpdateTest, tensor_add_update_throws_on_non_tensor_field)
{
    TensorUpdateFixture f;
    f.assertThrowOnNonTensorField(TensorAddUpdate(f.makeBaselineTensor()));
}

TEST_F(DocumentUpdateTest, tensor_remove_update_throws_on_non_tensor_field)
{
    TensorUpdateFixture f;
    f.assertThrowOnNonTensorField(TensorRemoveUpdate(f.makeBaselineTensor()));
}

TEST_F(DocumentUpdateTest, tensor_modify_update_throws_on_non_tensor_field)
{
    TensorUpdateFixture f;
    f.assertThrowOnNonTensorField(TensorModifyUpdate(TensorModifyUpdate::Operation::REPLACE, f.makeBaselineTensor()));
}

TEST_F(DocumentUpdateTest, tensor_remove_update_throws_if_address_tensor_is_not_sparse)
{
    TensorUpdateFixture f("dense_tensor");
    auto addressTensor = f.makeTensor(f.spec().add({{"x", 0}}, 2)); // creates a dense address tensor
    ASSERT_THROW(
            f.assertRoundtripSerialize(TensorRemoveUpdate(std::move(addressTensor))),
            vespalib::IllegalStateException);
}

TEST_F(DocumentUpdateTest, tensor_modify_update_throws_if_cells_tensor_is_not_sparse)
{
    TensorUpdateFixture f("dense_tensor");
    auto cellsTensor = f.makeTensor(f.spec().add({{"x", 0}}, 2)); // creates a dense cells tensor
    ASSERT_THROW(
            f.assertRoundtripSerialize(TensorModifyUpdate(TensorModifyUpdate::Operation::REPLACE, std::move(cellsTensor))),
            document::WrongTensorTypeException);
}

struct TensorUpdateSerializeFixture {
    std::unique_ptr<DocumentTypeRepo> repo;
    const DocumentType &docType;

    const TensorDataType &extractTensorDataType(const std::string &fieldName) {
        const auto &dataType = docType.getField(fieldName).getDataType();
        return dynamic_cast<const TensorDataType &>(dataType);
    }

    TensorUpdateSerializeFixture()
        : repo(makeDocumentTypeRepo()),
          docType(*repo->getDocumentType("test"))
    {
    }

    std::unique_ptr<DocumentTypeRepo> makeDocumentTypeRepo() {
        config_builder::DocumenttypesConfigBuilderHelper builder;
        builder.document(222, "test",
                         Struct("test.header")
                                 .addTensorField("sparse_tensor", "tensor(x{})")
                                 .addTensorField("dense_tensor", "tensor(x[4])"),
                         Struct("testdoc.body"));
        return std::make_unique<DocumentTypeRepo>(builder.config());
    }

    std::unique_ptr<TensorFieldValue> makeTensor() {
        return makeTensorFieldValue(TensorSpec("tensor(x{})").add({{"x", "2"}}, 5)
                                            .add({{"x", "3"}}, 7),
                                    extractTensorDataType("sparse_tensor"));
    }

    const Field &getField(const std::string &name) {
        return docType.getField(name);
    }

    DocumentUpdate::UP makeUpdate() {
        auto result = std::make_unique<DocumentUpdate>
                (*repo, docType, DocumentId("id:test:test::0"));

        result->addUpdate(FieldUpdate(getField("sparse_tensor"))
                                  .addUpdate(std::make_unique<AssignValueUpdate>(makeTensor()))
                                  .addUpdate(std::make_unique<TensorAddUpdate>(makeTensor()))
                                  .addUpdate(std::make_unique<TensorRemoveUpdate>(makeTensor())));
        result->addUpdate(FieldUpdate(getField("dense_tensor"))
                                  .addUpdate(std::make_unique<TensorModifyUpdate>(TensorModifyUpdate::Operation::REPLACE, makeTensor()))
                                  .addUpdate(std::make_unique<TensorModifyUpdate>(TensorModifyUpdate::Operation::ADD, makeTensor()))
                                  .addUpdate(std::make_unique<TensorModifyUpdate>(TensorModifyUpdate::Operation::MULTIPLY, makeTensor()))
                                  .addUpdate(std::make_unique<TensorModifyUpdate>(TensorModifyUpdate::Operation::REPLACE, makeTensor(), 0.0))
                                  .addUpdate(std::make_unique<TensorModifyUpdate>(TensorModifyUpdate::Operation::ADD, makeTensor(), 0.0))
                                  .addUpdate(std::make_unique<TensorModifyUpdate>(TensorModifyUpdate::Operation::MULTIPLY, makeTensor(), 1.0)));
        return result;
    }

    void serializeUpdateToFile(const DocumentUpdate &update, const std::string &fileName) {
        nbostream buf = serializeHEAD(update);
        TestDataBase::write_buffer_to_file(buf, fileName);
    }

    DocumentUpdate::UP deserializeUpdateFromFile(const std::string &fileName) {
        auto stream = TestDataBase::read_buffer_from_file(fileName);
        return DocumentUpdate::createHEAD(*repo, stream);
    }

};

TEST_F(DocumentUpdateTest, tensor_update_file_java_can_be_deserialized)
{
    TensorUpdateSerializeFixture f;
    auto update = f.deserializeUpdateFromFile(source_testdata() + "/serialize-tensor-update-java.dat");
    EXPECT_EQ(*f.makeUpdate(), *update);
}

TEST_F(DocumentUpdateTest, generate_serialized_tensor_update_file_cpp)
{
    TensorUpdateSerializeFixture f;
    auto update = f.makeUpdate();
    std::string file_name("serialize-tensor-update-cpp.dat");
    auto act_path = build_testdata() + "/" + file_name;
    f.serializeUpdateToFile(*update, act_path);
    auto buf = read_buffer_from_file(act_path);
    ASSERT_NO_FATAL_FAILURE(remove_unchanged_build_testdata_file_or_fail(buf, file_name));
}

void
assertDocumentUpdateFlag(bool createIfNonExistent, int value)
{
    DocumentUpdateFlags f1;
    f1.setCreateIfNonExistent(createIfNonExistent);
    EXPECT_EQ(createIfNonExistent, f1.getCreateIfNonExistent());
    int combined = f1.injectInto(value);
    std::cout << "createIfNonExistent=" << createIfNonExistent << ", value=" << value << ", combined=" << combined << std::endl;

    DocumentUpdateFlags f2 = DocumentUpdateFlags::extractFlags(combined);
    int extractedValue = DocumentUpdateFlags::extractValue(combined);
    EXPECT_EQ(createIfNonExistent, f2.getCreateIfNonExistent());
    EXPECT_EQ(value, extractedValue);
}

TEST_F(DocumentUpdateTest, testThatDocumentUpdateFlagsIsWorking)
{
    { // create-if-non-existent = true
        assertDocumentUpdateFlag(true, 0);
        assertDocumentUpdateFlag(true, 1);
        assertDocumentUpdateFlag(true, 2);
        assertDocumentUpdateFlag(true, 9999);
        assertDocumentUpdateFlag(true, 0xFFFFFFE);
        assertDocumentUpdateFlag(true, 0xFFFFFFF);
    }
    { // create-if-non-existent = false
        assertDocumentUpdateFlag(false, 0);
        assertDocumentUpdateFlag(false, 1);
        assertDocumentUpdateFlag(false, 2);
        assertDocumentUpdateFlag(false, 9999);
        assertDocumentUpdateFlag(false, 0xFFFFFFE);
        assertDocumentUpdateFlag(false, 0xFFFFFFF);
    }
}

struct CreateIfNonExistentFixture
{
    TestDocMan docMan;
    Document::UP document;
    DocumentUpdate::UP update;
    ~CreateIfNonExistentFixture();
    CreateIfNonExistentFixture();
};

CreateIfNonExistentFixture::~CreateIfNonExistentFixture() = default;
CreateIfNonExistentFixture::CreateIfNonExistentFixture()
    : docMan(),
      document(docMan.createDocument()),
      update(std::make_unique<DocumentUpdate>(docMan.getTypeRepo(), *document->getDataType(), document->getId()))
{
    update->addUpdate(FieldUpdate(document->getField("headerval"))
                              .addUpdate(std::make_unique<AssignValueUpdate>(std::make_unique<IntFieldValue>(1))));
    update->setCreateIfNonExistent(true);
}

TEST_F(DocumentUpdateTest, testThatCreateIfNonExistentFlagIsSerializedAndDeserialized)
{
    CreateIfNonExistentFixture f;

    nbostream buf(serializeHEAD(*f.update));

    DocumentUpdate::UP deserialized = DocumentUpdate::createHEAD(f.docMan.getTypeRepo(), buf);
    EXPECT_EQ(*f.update, *deserialized);
    EXPECT_TRUE(deserialized->getCreateIfNonExistent());
}

struct ArrayUpdateFixture {
    TestDocMan doc_man;
    std::unique_ptr<Document> doc;
    const Field& array_field;
    std::unique_ptr<DocumentUpdate> update;

    ArrayUpdateFixture();
    ~ArrayUpdateFixture();
};

ArrayUpdateFixture::ArrayUpdateFixture()
    : doc_man(),
      doc(doc_man.createDocument()),
      array_field(doc->getType().getField("tags")) // of type array<string>
{
    update = std::make_unique<DocumentUpdate>(doc_man.getTypeRepo(), *doc->getDataType(), doc->getId());
    update->addUpdate(FieldUpdate(array_field)
                              .addUpdate(std::make_unique<MapValueUpdate>(std::make_unique<IntFieldValue>(1),
                                                        std::make_unique<AssignValueUpdate>(StringFieldValue::make("bar")))));
}
ArrayUpdateFixture::~ArrayUpdateFixture() = default;

TEST_F(DocumentUpdateTest, array_element_update_can_be_roundtrip_serialized)
{
    ArrayUpdateFixture f;

    auto buffer = serializeHEAD(*f.update);

    auto deserialized = DocumentUpdate::createHEAD(f.doc_man.getTypeRepo(), buffer);
    EXPECT_EQ(*f.update, *deserialized);
}

TEST_F(DocumentUpdateTest, array_element_update_applies_to_specified_element)
{
    ArrayUpdateFixture f;

    ArrayFieldValue array_value(f.array_field.getDataType());
    CollectionHelper(array_value).add("foo");
    CollectionHelper(array_value).add("baz");
    CollectionHelper(array_value).add("blarg");
    f.doc->setValue(f.array_field, array_value);

    f.update->applyTo(*f.doc);

    auto result_array = f.doc->getAs<ArrayFieldValue>(f.array_field);
    ASSERT_EQ(size_t(3), result_array->size());
    EXPECT_EQ(std::string("foo"), (*result_array)[0].getAsString());
    EXPECT_EQ(std::string("bar"), (*result_array)[1].getAsString());
    EXPECT_EQ(std::string("blarg"), (*result_array)[2].getAsString());
}

TEST_F(DocumentUpdateTest, array_element_update_for_invalid_index_is_ignored)
{
    ArrayUpdateFixture f;

    ArrayFieldValue array_value(f.array_field.getDataType());
    CollectionHelper(array_value).add("jerry");
    f.doc->setValue(f.array_field, array_value);

    f.update->applyTo(*f.doc); // MapValueUpdate for index 1, which does not exist

    auto result_array = f.doc->getAs<ArrayFieldValue>(f.array_field);
    EXPECT_EQ(array_value, *result_array);
}

struct UpdateToEmptyDocumentFixture {
    std::unique_ptr<DocumentTypeRepo> repo;
    const DocumentType& doc_type;
    FixedTypeRepo fixed_repo;

    UpdateToEmptyDocumentFixture()
        : repo(make_repo()),
          doc_type(*repo->getDocumentType("test")),
          fixed_repo(*repo, doc_type)
    {
    }

    std::unique_ptr<DocumentTypeRepo> make_repo() {
        config_builder::DocumenttypesConfigBuilderHelper builder;
        builder.document(222, "test",
                         Struct("test.header").addField("text", DataType::T_STRING),
                         Struct("test.body"));
        return std::make_unique<DocumentTypeRepo>(builder.config());
    }

    Document::UP make_empty_doc() {
        vespalib::nbostream stream;
        {
            Document doc(*repo, doc_type, DocumentId("id:test:test::0"));
            VespaDocumentSerializer serializer(stream);
            serializer.write(doc);
        }
        // This simulates that the document is read from e.g. the document store
        return std::make_unique<Document>(*repo, stream);
    }

    DocumentUpdate::UP make_update() {
        auto text = std::make_unique<StringFieldValue>("hello world");
        auto span_list_up = std::make_unique<SpanList>();
        auto span_list = span_list_up.get();
        auto tree = std::make_unique<SpanTree>("my_span_tree", std::move(span_list_up));
        tree->annotate(span_list->add(std::make_unique<Span>(0, 5)), *AnnotationType::TERM);
        tree->annotate(span_list->add(std::make_unique<Span>(6, 3)), *AnnotationType::TERM);
        StringFieldValue::SpanTrees trees;
        trees.push_back(std::move(tree));
        text->setSpanTrees(trees, fixed_repo);

        auto result = std::make_unique<DocumentUpdate>(*repo, doc_type, DocumentId("id:test:test::0"));
        result->addUpdate(FieldUpdate(doc_type.getField("text"))
                                  .addUpdate(std::make_unique<AssignValueUpdate>(std::move(text))));
        return result;
    }
};

TEST_F(DocumentUpdateTest, string_field_annotations_can_be_deserialized_after_assign_update_to_empty_document)
{
    UpdateToEmptyDocumentFixture f;
    auto doc = f.make_empty_doc();
    auto update = f.make_update();
    update->applyTo(*doc);
    auto fv = doc->getValue("text");
    auto& text = dynamic_cast<StringFieldValue&>(*fv);
    // This uses both the DocumentTypeRepo and DocumentType in order to deserialize the annotations.
    auto tree = text.getSpanTrees();
    EXPECT_EQ("hello world", text.getValue());
    ASSERT_EQ(1, tree.size());
    ASSERT_EQ(2, tree[0]->numAnnotations());
}

}  // namespace document
