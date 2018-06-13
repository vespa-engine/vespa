// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/base/testdocman.h>

#include <vespa/document/update/addvalueupdate.h>
#include <vespa/document/update/arithmeticvalueupdate.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/clearvalueupdate.h>
#include <vespa/document/update/documentupdateflags.h>
#include <vespa/document/update/fieldupdate.h>
#include <vespa/document/update/mapvalueupdate.h>
#include <vespa/document/update/removevalueupdate.h>
#include <vespa/document/update/valueupdate.h>
#include <vespa/document/serialization/vespadocumentserializer.h>
#include <vespa/document/util/bytebuffer.h>

#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/types.h>
#include <vespa/eval/tensor/default_tensor.h>
#include <vespa/eval/tensor/tensor_factory.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/exception.h>
#include <fcntl.h>
#include <unistd.h>

using namespace document::config_builder;
using vespalib::tensor::Tensor;
using vespalib::tensor::TensorCells;
using vespalib::tensor::TensorDimensions;
using vespalib::nbostream;

namespace document {

struct DocumentUpdateTest : public CppUnit::TestFixture {

  void testSimpleUsage();
  void testUpdateApplySingleValue();
  void testClearField();
  void testUpdateArray();
  void testUpdateWeightedSet();
  void testIncrementNonExistingAutoCreateWSetField();
  void testIncrementExistingWSetField();
  void testIncrementWithZeroResultWeightIsRemoved();
  void testReadSerializedFile();
  void testGenerateSerializedFile();
  void testSetBadFieldTypes();
  void testUpdateApplyNoParams();
  void testUpdateApplyNoArrayValues();
  void testUpdateArrayEmptyParamValue();
  void testUpdateWeightedSetEmptyParamValue();
  void testUpdateArrayWrongSubtype();
  void testUpdateWeightedSetWrongSubtype();
  void testMapValueUpdate();
  void testTensorAssignUpdate();
  void testTensorClearUpdate();
  void testThatDocumentUpdateFlagsIsWorking();
  void testThatCreateIfNonExistentFlagIsSerialized50AndDeserialized50();
  void testThatCreateIfNonExistentFlagIsSerializedAndDeserialized();
  void array_element_update_can_be_roundtrip_serialized();
  void array_element_update_applies_to_specified_element();

  CPPUNIT_TEST_SUITE(DocumentUpdateTest);
  CPPUNIT_TEST(testSimpleUsage);
  CPPUNIT_TEST(testUpdateApplySingleValue);
  CPPUNIT_TEST(testClearField);
  CPPUNIT_TEST(testUpdateArray);
  CPPUNIT_TEST(testUpdateWeightedSet);
  CPPUNIT_TEST(testIncrementNonExistingAutoCreateWSetField);
  CPPUNIT_TEST(testIncrementExistingWSetField);
  CPPUNIT_TEST(testIncrementWithZeroResultWeightIsRemoved);
  CPPUNIT_TEST(testReadSerializedFile);
  CPPUNIT_TEST(testGenerateSerializedFile);
  CPPUNIT_TEST(testSetBadFieldTypes);
  CPPUNIT_TEST(testUpdateApplyNoParams);
  CPPUNIT_TEST(testUpdateApplyNoArrayValues);
  CPPUNIT_TEST(testUpdateArrayEmptyParamValue);
  CPPUNIT_TEST(testUpdateWeightedSetEmptyParamValue);
  CPPUNIT_TEST(testUpdateArrayWrongSubtype);
  CPPUNIT_TEST(testUpdateWeightedSetWrongSubtype);
  CPPUNIT_TEST(testMapValueUpdate);
  CPPUNIT_TEST(testTensorAssignUpdate);
  CPPUNIT_TEST(testTensorClearUpdate);
  CPPUNIT_TEST(testThatDocumentUpdateFlagsIsWorking);
  CPPUNIT_TEST(testThatCreateIfNonExistentFlagIsSerialized50AndDeserialized50);
  CPPUNIT_TEST(testThatCreateIfNonExistentFlagIsSerializedAndDeserialized);
  CPPUNIT_TEST(array_element_update_can_be_roundtrip_serialized);
  CPPUNIT_TEST(array_element_update_applies_to_specified_element);
  CPPUNIT_TEST_SUITE_END();

};

CPPUNIT_TEST_SUITE_REGISTRATION(DocumentUpdateTest);

namespace {

ByteBuffer::UP serializeHEAD(const DocumentUpdate & update)
{
    nbostream stream;
    VespaDocumentSerializer serializer(stream);
    serializer.writeHEAD(update);
    ByteBuffer::UP retVal(new ByteBuffer(stream.size()));
    retVal->putBytes(stream.peek(), stream.size());
    return retVal;
}

ByteBuffer::UP serialize42(const DocumentUpdate & update)
{
    nbostream stream;
    VespaDocumentSerializer serializer(stream);
    serializer.write42(update);
    ByteBuffer::UP retVal(new ByteBuffer(stream.size()));
    retVal->putBytes(stream.peek(), stream.size());
    return retVal;
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
void testValueUpdate(const UpdateType& update, const DataType &type) {
    try{
        DocumentTypeRepo repo;
        nbostream stream = serialize(update);
        typename UpdateType::UP copy(dynamic_cast<UpdateType*>(
                        ValueUpdate::createInstance(repo, type, stream, Document::getNewestSerializationVersion())
                        .release()));
        CPPUNIT_ASSERT_EQUAL(update, *copy);
    } catch (std::exception& e) {
            std::cerr << "Failed while processing update " << update << "\n";
    throw;
    }
}

Tensor::UP
createTensor(const TensorCells &cells, const TensorDimensions &dimensions) {
    vespalib::tensor::DefaultTensor::builder builder;
    return vespalib::tensor::TensorFactory::create(cells, dimensions, builder);
}

FieldValue::UP createTensorFieldValue() {
    auto fv(std::make_unique<TensorFieldValue>());
    *fv = createTensor({ {{{"x", "8"}, {"y", "9"}}, 11} }, {"x", "y"});
    return std::move(fv);
}

}  // namespace

void
DocumentUpdateTest::testSimpleUsage() {
    DocumenttypesConfigBuilderHelper builder;
    builder.document(42, "test",
                     Struct("test.header").addField("bytef", DataType::T_BYTE).addField("intf", DataType::T_INT),
                     Struct("test.body").addField("intarr", Array(DataType::T_INT)));
    DocumentTypeRepo repo(builder.config());
    const DocumentType* docType(repo.getDocumentType("test"));
    const DataType *arrayType = repo.getDataType(*docType, "Array<Int>");

        // Test that primitive value updates can be serialized
    testValueUpdate(ClearValueUpdate(), *DataType::INT);
    testValueUpdate(AssignValueUpdate(IntFieldValue(1)), *DataType::INT);
    testValueUpdate(ArithmeticValueUpdate(ArithmeticValueUpdate::Div, 4.3), *DataType::FLOAT);
    testValueUpdate(AddValueUpdate(IntFieldValue(1), 4), *arrayType);
    testValueUpdate(RemoveValueUpdate(IntFieldValue(1)), *arrayType);

    FieldUpdate fieldUpdate(docType->getField("intf"));
    fieldUpdate.addUpdate(AssignValueUpdate(IntFieldValue(1)));
    nbostream stream = serialize(fieldUpdate);
    FieldUpdate fieldUpdateCopy(repo, *docType, stream, Document::getNewestSerializationVersion());
    CPPUNIT_ASSERT_EQUAL(fieldUpdate, fieldUpdateCopy);

        // Test that a document update can be serialized
    DocumentUpdate docUpdate(repo, *docType, DocumentId("doc::testdoc"));
    docUpdate.addUpdate(fieldUpdateCopy);
    ByteBuffer::UP docBuf = serializeHEAD(docUpdate);
    docBuf->flip();
    auto docUpdateCopy(DocumentUpdate::createHEAD(repo, nbostream(docBuf->getBufferAtPos(), docBuf->getRemaining())));

        // Create a test document
    Document doc(*docType, DocumentId("doc::testdoc"));
    doc.set("bytef", 0);
    doc.set("intf", 5);
    ArrayFieldValue array(*arrayType);
    array.add(IntFieldValue(3));
    array.add(IntFieldValue(7));
    doc.setValue("intarr", array);

        // Verify that we can apply simple updates to it
    {
        Document updated(doc);
        DocumentUpdate upd(repo, *docType, DocumentId("doc::testdoc"));
        upd.addUpdate(FieldUpdate(docType->getField("intf")).addUpdate(ClearValueUpdate()));
        upd.applyTo(updated);
        CPPUNIT_ASSERT(doc != updated);
        CPPUNIT_ASSERT(! updated.getValue("intf"));
    }
    {
        Document updated(doc);
        DocumentUpdate upd(repo, *docType, DocumentId("doc::testdoc"));
        upd.addUpdate(FieldUpdate(docType->getField("intf")).addUpdate(AssignValueUpdate(IntFieldValue(15))));
        upd.applyTo(updated);
        CPPUNIT_ASSERT(doc != updated);
        CPPUNIT_ASSERT_EQUAL(15, updated.getValue("intf")->getAsInt());
    }
    {
        Document updated(doc);
        DocumentUpdate upd(repo, *docType, DocumentId("doc::testdoc"));
        upd.addUpdate(FieldUpdate(docType->getField("intf")).addUpdate(ArithmeticValueUpdate(ArithmeticValueUpdate::Add, 15)));
        upd.applyTo(updated);
        CPPUNIT_ASSERT(doc != updated);
        CPPUNIT_ASSERT_EQUAL(20, updated.getValue("intf")->getAsInt());
    }
    {
        Document updated(doc);
        DocumentUpdate upd(repo, *docType, DocumentId("doc::testdoc"));
        upd.addUpdate(FieldUpdate(docType->getField("intarr")).addUpdate(AddValueUpdate(IntFieldValue(4))));
        upd.applyTo(updated);
        CPPUNIT_ASSERT(doc != updated);
        std::unique_ptr<ArrayFieldValue> val(dynamic_cast<ArrayFieldValue*>(updated.getValue("intarr").release()));
        CPPUNIT_ASSERT_EQUAL(size_t(3), val->size());
        CPPUNIT_ASSERT_EQUAL(4, (*val)[2].getAsInt());
    }
    {
        Document updated(doc);
        DocumentUpdate upd(repo, *docType, DocumentId("doc::testdoc"));
        upd.addUpdate(FieldUpdate(docType->getField("intarr")).addUpdate(RemoveValueUpdate(IntFieldValue(3))));
        upd.applyTo(updated);
        CPPUNIT_ASSERT(doc != updated);
        std::unique_ptr<ArrayFieldValue> val(dynamic_cast<ArrayFieldValue*>(updated.getValue("intarr").release()));
        CPPUNIT_ASSERT_EQUAL(size_t(1), val->size());
        CPPUNIT_ASSERT_EQUAL(7, (*val)[0].getAsInt());
    }
    {
        Document updated(doc);
        DocumentUpdate upd(repo, *docType, DocumentId("doc::testdoc"));
        upd.addUpdate(FieldUpdate(docType->getField("bytef"))
                              .addUpdate(ArithmeticValueUpdate(ArithmeticValueUpdate::Add, 15)));
        upd.applyTo(updated);
        CPPUNIT_ASSERT(doc != updated);
        CPPUNIT_ASSERT_EQUAL(15, (int) updated.getValue("bytef")->getAsByte());
    }
}

void
DocumentUpdateTest::testClearField()
{
    // Create a document.
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    doc->setValue(doc->getField("headerval"), IntFieldValue(4));
    CPPUNIT_ASSERT_EQUAL(4, doc->getValue("headerval")->getAsInt());

    // Apply an update.
    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(doc->getField("headerval")).addUpdate(AssignValueUpdate()))
        .applyTo(*doc);
    CPPUNIT_ASSERT(!doc->getValue("headerval"));
}

void
DocumentUpdateTest::testUpdateApplySingleValue()
{
    // Create a document.
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    doc->setValue(doc->getField("headerval"), IntFieldValue(4));
    CPPUNIT_ASSERT_EQUAL(4, doc->getValue("headerval")->getAsInt());

    // Apply an update.
    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(doc->getField("headerval")).addUpdate(AssignValueUpdate(IntFieldValue(9))))
        .applyTo(*doc);
    CPPUNIT_ASSERT_EQUAL(9, doc->getValue("headerval")->getAsInt());
}

void
DocumentUpdateTest::testUpdateArray()
{
    // Create a document.
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    CPPUNIT_ASSERT_EQUAL((document::FieldValue*)NULL, doc->getValue(doc->getField("tags")).get());

    // Assign array field.
    ArrayFieldValue myarray(doc->getType().getField("tags").getDataType());
    myarray.add(StringFieldValue("foo"));
	myarray.add(StringFieldValue("bar"));

    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(doc->getField("tags")).addUpdate(AssignValueUpdate(myarray)))
        .applyTo(*doc);
    auto fval1(doc->getAs<ArrayFieldValue>(doc->getField("tags")));
    CPPUNIT_ASSERT_EQUAL((size_t) 2, fval1->size());
    CPPUNIT_ASSERT_EQUAL(std::string("foo"), std::string((*fval1)[0].getAsString()));
    CPPUNIT_ASSERT_EQUAL(std::string("bar"), std::string((*fval1)[1].getAsString()));

    // Append array field
    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(doc->getField("tags"))
                   .addUpdate(AddValueUpdate(StringFieldValue("another")))
                   .addUpdate(AddValueUpdate(StringFieldValue("tag"))))
        .applyTo(*doc);
    std::unique_ptr<ArrayFieldValue>
        fval2(doc->getAs<ArrayFieldValue>(doc->getField("tags")));
    CPPUNIT_ASSERT_EQUAL((size_t) 4, fval2->size());
    CPPUNIT_ASSERT_EQUAL(std::string("foo"), std::string((*fval2)[0].getAsString()));
    CPPUNIT_ASSERT_EQUAL(std::string("bar"), std::string((*fval2)[1].getAsString()));
    CPPUNIT_ASSERT_EQUAL(std::string("another"), std::string((*fval2)[2].getAsString()));
    CPPUNIT_ASSERT_EQUAL(std::string("tag"), std::string((*fval2)[3].getAsString()));

    // Append single value.
    try {
        DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
            .addUpdate(FieldUpdate(doc->getField("tags"))
                       .addUpdate(AssignValueUpdate(StringFieldValue("THROW MEH!"))))
            .applyTo(*doc);
        CPPUNIT_FAIL("Expected exception when assinging a string value to an "
                     "array field.");
        }
    catch (std::exception& e) {
        ; // fprintf(stderr, "Got exception => OK: %s\n", e.what());
    }

    // Remove array field.
    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(doc->getField("tags"))
                   .addUpdate(RemoveValueUpdate(StringFieldValue("foo")))
                   .addUpdate(RemoveValueUpdate(StringFieldValue("tag"))))
        .applyTo(*doc);
    auto fval3(doc->getAs<ArrayFieldValue>(doc->getField("tags")));
    CPPUNIT_ASSERT_EQUAL((size_t) 2, fval3->size());
    CPPUNIT_ASSERT_EQUAL(std::string("bar"), std::string((*fval3)[0].getAsString()));
    CPPUNIT_ASSERT_EQUAL(std::string("another"), std::string((*fval3)[1].getAsString()));

    // Remove array from array.
    ArrayFieldValue myarray2(doc->getType().getField("tags").getDataType());
    myarray2.add(StringFieldValue("foo"));
    myarray2.add(StringFieldValue("bar"));
    try {
        DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
            .addUpdate(FieldUpdate(doc->getField("tags"))
                       .addUpdate(RemoveValueUpdate(myarray2)))
            .applyTo(*doc);
        CPPUNIT_FAIL("Expected exception when removing an array from a "
                     "string array.");
    }
    catch (std::exception& e) {
        ; // fprintf(stderr, "Got exception => OK: %s\n", e.what());
    }
}

void
DocumentUpdateTest::testUpdateWeightedSet()
{
    // Create a test document
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    const Field& field(doc->getType().getField("stringweightedset"));
    CPPUNIT_ASSERT_EQUAL((FieldValue*) 0, doc->getValue(field).get());
	
    // Assign weightedset field
    WeightedSetFieldValue wset(field.getDataType());
    wset.add(StringFieldValue("foo"), 3);
    wset.add(StringFieldValue("bar"), 14);
    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field).addUpdate(AssignValueUpdate(wset)))
        .applyTo(*doc);
    auto fval1(doc->getAs<WeightedSetFieldValue>(field));
    CPPUNIT_ASSERT_EQUAL((size_t) 2, fval1->size());
    CPPUNIT_ASSERT(fval1->contains(StringFieldValue("foo")));
    CPPUNIT_ASSERT(fval1->find(StringFieldValue("foo")) != fval1->end());
    CPPUNIT_ASSERT_EQUAL(3, fval1->get(StringFieldValue("foo"), 0));
    CPPUNIT_ASSERT(fval1->contains(StringFieldValue("bar")));
    CPPUNIT_ASSERT(fval1->find(StringFieldValue("bar")) != fval1->end());
    CPPUNIT_ASSERT_EQUAL(14, fval1->get(StringFieldValue("bar"), 0));

    // Do a second assign
    WeightedSetFieldValue wset2(field.getDataType());
    wset2.add(StringFieldValue("foo"), 16);
    wset2.add(StringFieldValue("bar"), 24);
    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field)
                   .addUpdate(AssignValueUpdate(wset2)))
        .applyTo(*doc);
    auto fval2(doc->getAs<WeightedSetFieldValue>(field));
    CPPUNIT_ASSERT_EQUAL((size_t) 2, fval2->size());
    CPPUNIT_ASSERT(fval2->contains(StringFieldValue("foo")));
    CPPUNIT_ASSERT(fval2->find(StringFieldValue("foo")) != fval1->end());
    CPPUNIT_ASSERT_EQUAL(16, fval2->get(StringFieldValue("foo"), 0));
    CPPUNIT_ASSERT(fval2->contains(StringFieldValue("bar")));
    CPPUNIT_ASSERT(fval2->find(StringFieldValue("bar")) != fval1->end());
    CPPUNIT_ASSERT_EQUAL(24, fval2->get(StringFieldValue("bar"), 0));

    // Append weighted field
    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field)
                   .addUpdate(AddValueUpdate(StringFieldValue("foo")).setWeight(3))
                   .addUpdate(AddValueUpdate(StringFieldValue("too")).setWeight(14)))
        .applyTo(*doc);
    std::unique_ptr<WeightedSetFieldValue>
        fval3(doc->getAs<WeightedSetFieldValue>(field));
    CPPUNIT_ASSERT_EQUAL((size_t) 3, fval3->size());
    CPPUNIT_ASSERT(fval3->contains(StringFieldValue("foo")));
    CPPUNIT_ASSERT_EQUAL(3, fval3->get(StringFieldValue("foo"), 0));
    CPPUNIT_ASSERT(fval3->contains(StringFieldValue("bar")));
    CPPUNIT_ASSERT_EQUAL(24, fval3->get(StringFieldValue("bar"), 0));
    CPPUNIT_ASSERT(fval3->contains(StringFieldValue("too")));
    CPPUNIT_ASSERT_EQUAL(14, fval3->get(StringFieldValue("too"), 0));

    // Remove weighted field
    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field)
                   .addUpdate(RemoveValueUpdate(StringFieldValue("foo")))
                   .addUpdate(RemoveValueUpdate(StringFieldValue("too"))))
        .applyTo(*doc);
    auto fval4(doc->getAs<WeightedSetFieldValue>(field));
    CPPUNIT_ASSERT_EQUAL((size_t) 1, fval4->size());
    CPPUNIT_ASSERT(!fval4->contains(StringFieldValue("foo")));
    CPPUNIT_ASSERT(fval4->contains(StringFieldValue("bar")));
    CPPUNIT_ASSERT_EQUAL(24, fval4->get(StringFieldValue("bar"), 0));
    CPPUNIT_ASSERT(!fval4->contains(StringFieldValue("too")));
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
      doc(*docType, DocumentId("doc::testdoc")),
      field(docType->getField("strwset")),
      update(repo, *docType, DocumentId("doc::testdoc"))
{
    update.addUpdate(FieldUpdate(field)
                             .addUpdate(MapValueUpdate(StringFieldValue("foo"),
                                                       ArithmeticValueUpdate(ArithmeticValueUpdate::Add, 1))));
}
} // anon ns

void
DocumentUpdateTest::testIncrementNonExistingAutoCreateWSetField()
{
    WeightedSetAutoCreateFixture fixture;

    fixture.applyUpdateToDocument();

    std::unique_ptr<WeightedSetFieldValue> ws(
            fixture.doc.getAs<WeightedSetFieldValue>(fixture.field));
    CPPUNIT_ASSERT_EQUAL(size_t(1), ws->size());
    CPPUNIT_ASSERT(ws->contains(StringFieldValue("foo")));
    CPPUNIT_ASSERT_EQUAL(1, ws->get(StringFieldValue("foo"), 0));
}

void
DocumentUpdateTest::testIncrementExistingWSetField()
{
    WeightedSetAutoCreateFixture fixture;
    {
        WeightedSetFieldValue wset(fixture.field.getDataType());
        wset.add(StringFieldValue("bar"), 14);
        fixture.doc.setValue(fixture.field, wset);
    }
    fixture.applyUpdateToDocument();

    auto ws(fixture.doc.getAs<WeightedSetFieldValue>(fixture.field));
    CPPUNIT_ASSERT_EQUAL(size_t(2), ws->size());
    CPPUNIT_ASSERT(ws->contains(StringFieldValue("foo")));
    CPPUNIT_ASSERT_EQUAL(1, ws->get(StringFieldValue("foo"), 0));
}

void
DocumentUpdateTest::testIncrementWithZeroResultWeightIsRemoved()
{
    WeightedSetAutoCreateFixture fixture;
    fixture.update.addUpdate(FieldUpdate(fixture.field)
            .addUpdate(MapValueUpdate(StringFieldValue("baz"),
                                      ArithmeticValueUpdate(ArithmeticValueUpdate::Add, 0))));

    fixture.applyUpdateToDocument();

    auto ws(fixture.doc.getAs<WeightedSetFieldValue>(fixture.field));
    CPPUNIT_ASSERT_EQUAL(size_t(1), ws->size());
    CPPUNIT_ASSERT(ws->contains(StringFieldValue("foo")));
    CPPUNIT_ASSERT(!ws->contains(StringFieldValue("baz")));
}

void DocumentUpdateTest::testReadSerializedFile()
{
    // Reads a file serialized from java
    const std::string file_name = TEST_PATH("data/crossplatform-java-cpp-doctypes.cfg");
    DocumentTypeRepo repo(readDocumenttypesConfig(file_name));

    int fd = open(TEST_PATH("data/serializeupdatejava.dat").c_str(), O_RDONLY);

    int len = lseek(fd,0,SEEK_END);
    ByteBuffer buf(len);
    lseek(fd,0,SEEK_SET);
    if (read(fd, buf.getBuffer(), len) != len) {
    	throw vespalib::Exception("read failed");
    }
    close(fd);

    nbostream is(buf.getBufferAtPos(), buf.getRemaining());
    DocumentUpdate::UP updp(DocumentUpdate::create42(repo, is));
    DocumentUpdate& upd(*updp);

    const DocumentType *type = repo.getDocumentType("serializetest");
    CPPUNIT_ASSERT_EQUAL(DocumentId(DocIdString("update", "test")), upd.getId());
    CPPUNIT_ASSERT_EQUAL(*type, upd.getType());

    // Verify assign value update.
    FieldUpdate serField = upd.getUpdates()[0];
    CPPUNIT_ASSERT_EQUAL(serField.getField().getId(), type->getField("intfield").getId());

    const ValueUpdate* serValue = &serField[0];
    CPPUNIT_ASSERT_EQUAL(serValue->getType(), ValueUpdate::Assign);

    const AssignValueUpdate* assign(static_cast<const AssignValueUpdate*>(serValue));
    CPPUNIT_ASSERT_EQUAL(IntFieldValue(4), static_cast<const IntFieldValue&>(assign->getValue()));

    // Verify clear field update.
    serField = upd.getUpdates()[1];
    CPPUNIT_ASSERT_EQUAL(serField.getField().getId(), type->getField("floatfield").getId());

    serValue = &serField[0];
    CPPUNIT_ASSERT_EQUAL(serValue->getType(), ValueUpdate::Clear);
    CPPUNIT_ASSERT(serValue->inherits(ClearValueUpdate::classId));

    // Verify add value update.
    serField = upd.getUpdates()[2];
    CPPUNIT_ASSERT_EQUAL(serField.getField().getId(), type->getField("arrayoffloatfield").getId());

    serValue = &serField[0];
    CPPUNIT_ASSERT_EQUAL(serValue->getType(), ValueUpdate::Add);

    const AddValueUpdate* add = static_cast<const AddValueUpdate*>(serValue);
    const FieldValue* value = &add->getValue();
    CPPUNIT_ASSERT(value->inherits(FloatFieldValue::classId));
    CPPUNIT_ASSERT_EQUAL(value->getAsFloat(), 5.00f);

    serValue = &serField[1];
    CPPUNIT_ASSERT_EQUAL(serValue->getType(), ValueUpdate::Add);

    add = static_cast<const AddValueUpdate*>(serValue);
    value = &add->getValue();
    CPPUNIT_ASSERT(value->inherits(FloatFieldValue::classId));
    CPPUNIT_ASSERT_EQUAL(value->getAsFloat(), 4.23f);

    serValue = &serField[2];
    CPPUNIT_ASSERT_EQUAL(serValue->getType(), ValueUpdate::Add);

    add = static_cast<const AddValueUpdate*>(serValue);
    value = &add->getValue();
    CPPUNIT_ASSERT(value->inherits(FloatFieldValue::classId));
    CPPUNIT_ASSERT_EQUAL(value->getAsFloat(), -1.00f);

}

void DocumentUpdateTest::testGenerateSerializedFile()
{
    // Tests nothing, only generates a file for java test
    const std::string file_name = TEST_PATH("data/crossplatform-java-cpp-doctypes.cfg");
    DocumentTypeRepo repo(readDocumenttypesConfig(file_name));

    const DocumentType *type(repo.getDocumentType("serializetest"));
    DocumentUpdate upd(repo, *type, DocumentId(DocIdString("update", "test")));
    upd.addUpdate(FieldUpdate(type->getField("intfield"))
		  .addUpdate(AssignValueUpdate(IntFieldValue(4))));
    upd.addUpdate(FieldUpdate(type->getField("floatfield"))
		  .addUpdate(AssignValueUpdate(FloatFieldValue(1.00f))));
    upd.addUpdate(FieldUpdate(type->getField("arrayoffloatfield"))
		  .addUpdate(AddValueUpdate(FloatFieldValue(5.00f)))
		  .addUpdate(AddValueUpdate(FloatFieldValue(4.23f)))
		  .addUpdate(AddValueUpdate(FloatFieldValue(-1.00f))));
    upd.addUpdate(FieldUpdate(type->getField("intfield"))
          .addUpdate(ArithmeticValueUpdate(ArithmeticValueUpdate::Add, 3)));
    upd.addUpdate(FieldUpdate(type->getField("wsfield"))
          .addUpdate(MapValueUpdate(StringFieldValue("foo"),
                        ArithmeticValueUpdate(ArithmeticValueUpdate::Add, 2)))
          .addUpdate(MapValueUpdate(StringFieldValue("foo"),
                        ArithmeticValueUpdate(ArithmeticValueUpdate::Mul, 2))));
    ByteBuffer::UP buf(serialize42(upd));

    int fd = open(TEST_PATH("data/serializeupdatecpp.dat").c_str(), O_WRONLY | O_TRUNC | O_CREAT, 0644);
    if (write(fd, buf->getBuffer(), buf->getPos()) != (ssize_t)buf->getPos()) {
	    throw vespalib::Exception("read failed");
    }
    close(fd);
}


void DocumentUpdateTest::testSetBadFieldTypes()
{
    // Create a test document
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    CPPUNIT_ASSERT_EQUAL((document::FieldValue*)NULL, doc->getValue(doc->getField("headerval")).get());

    // Assign a float value to an int field.
    DocumentUpdate update(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    try {
        update.addUpdate(FieldUpdate(doc->getField("headerval"))
                 .addUpdate(AssignValueUpdate(FloatFieldValue(4.00f))));
        CPPUNIT_FAIL("Expected exception when adding a float to an int field.");
    } catch (std::exception& e) {
        ; // fprintf(stderr, "Got exception => OK: %s\n", e.what());
    }

    update.applyTo(*doc);

    // Verify that the field is NOT set in the document.
    CPPUNIT_ASSERT_EQUAL((document::FieldValue*)NULL,
			 doc->getValue(doc->getField("headerval")).get());
}

void
DocumentUpdateTest::testUpdateApplyNoParams()
{
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    CPPUNIT_ASSERT_EQUAL((document::FieldValue*)NULL, doc->getValue(doc->getField("tags")).get());

    // Assign array field with no parameters - illegal.
    DocumentUpdate update(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    try {
        update.addUpdate(FieldUpdate(doc->getField("tags")).addUpdate(AssignValueUpdate()));
        CPPUNIT_FAIL("Expected exception when assign a NULL value.");
    } catch (std::exception& e) {
        ; // fprintf(stderr, "Got exception => OK: %s\n", e.what());
    }

    update.applyTo(*doc);

    // Verify that the field was cleared in the document.
    CPPUNIT_ASSERT(!doc->hasValue(doc->getField("tags")));
}

void
DocumentUpdateTest::testUpdateApplyNoArrayValues()
{
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    const Field &field(doc->getType().getField("tags"));
    CPPUNIT_ASSERT_EQUAL((document::FieldValue*) 0, doc->getValue(field).get());

    // Assign array field with no array values = empty array
    DocumentUpdate update(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    update.addUpdate(FieldUpdate(field)
                     .addUpdate(AssignValueUpdate(ArrayFieldValue(field.getDataType()))));

    update.applyTo(*doc);

    // Verify that the field was set in the document
    std::unique_ptr<ArrayFieldValue> fval(doc->getAs<ArrayFieldValue>(field));
    CPPUNIT_ASSERT(fval.get());
    CPPUNIT_ASSERT_EQUAL((size_t) 0, fval->size());
}

void
DocumentUpdateTest::testUpdateArrayEmptyParamValue()
{
    // Create a test document.
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    const Field &field(doc->getType().getField("tags"));
    CPPUNIT_ASSERT_EQUAL((document::FieldValue*) 0, doc->getValue(field).get());

    // Assign array field with no array values = empty array.
    DocumentUpdate update(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    update.addUpdate(FieldUpdate(field).addUpdate(AssignValueUpdate(ArrayFieldValue(field.getDataType()))));
    update.applyTo(*doc);

    // Verify that the field was set in the document.
    std::unique_ptr<ArrayFieldValue> fval1(doc->getAs<ArrayFieldValue>(field));
    CPPUNIT_ASSERT(fval1.get());
    CPPUNIT_ASSERT_EQUAL((size_t) 0, fval1->size());

    // Remove array field.
    DocumentUpdate update2(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    update2.addUpdate(FieldUpdate(field).addUpdate(ClearValueUpdate()));
    update2.applyTo(*doc);

    // Verify that the field was cleared in the document.
    std::unique_ptr<ArrayFieldValue> fval2(doc->getAs<ArrayFieldValue>(field));
    CPPUNIT_ASSERT(!fval2);
}

void
DocumentUpdateTest::testUpdateWeightedSetEmptyParamValue()
{
    // Create a test document
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    const Field &field(doc->getType().getField("stringweightedset"));
    CPPUNIT_ASSERT_EQUAL((document::FieldValue*) 0, doc->getValue(field).get());

    // Assign weighted set with no items = empty set.
    DocumentUpdate update(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    update.addUpdate(FieldUpdate(field).addUpdate(AssignValueUpdate(WeightedSetFieldValue(field.getDataType()))));
    update.applyTo(*doc);

    // Verify that the field was set in the document.
    auto fval1(doc->getAs<WeightedSetFieldValue>(field));
    CPPUNIT_ASSERT(fval1.get());
    CPPUNIT_ASSERT_EQUAL((size_t) 0, fval1->size());

    // Remove weighted set field.
    DocumentUpdate update2(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    update2.addUpdate(FieldUpdate(field).addUpdate(ClearValueUpdate()));
    update2.applyTo(*doc);

    // Verify that the field was cleared in the document.
    auto fval2(doc->getAs<WeightedSetFieldValue>(field));
    CPPUNIT_ASSERT(!fval2);
}

void
DocumentUpdateTest::testUpdateArrayWrongSubtype()
{
    // Create a test document
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    const Field &field(doc->getType().getField("tags"));
    CPPUNIT_ASSERT_EQUAL((document::FieldValue*) 0, doc->getValue(field).get());

    // Assign int values to string array.
    DocumentUpdate update(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    try {
        update.addUpdate(FieldUpdate(field)
                 .addUpdate(AddValueUpdate(IntFieldValue(123)))
                 .addUpdate(AddValueUpdate(IntFieldValue(456))));
        CPPUNIT_FAIL("Expected exception when adding wrong type.");
    } catch (std::exception& e) {
        ; // fprintf(stderr, "Got exception => OK: %s\n", e.what());
    }

    // Apply update
    update.applyTo(*doc);

    // Verify that the field was NOT set in the document
    FieldValue::UP fval(doc->getValue(field));
    CPPUNIT_ASSERT_EQUAL((document::FieldValue*) 0, fval.get());
}

void
DocumentUpdateTest::testUpdateWeightedSetWrongSubtype()
{
    // Create a test document
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    const Field &field(doc->getType().getField("stringweightedset"));
    CPPUNIT_ASSERT_EQUAL((document::FieldValue*) 0, doc->getValue(field).get());

    // Assign int values to string array.
    DocumentUpdate update(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    try {
        update.addUpdate(FieldUpdate(field)
                 .addUpdate(AddValueUpdate(IntFieldValue(123)).setWeight(1000))
                 .addUpdate(AddValueUpdate(IntFieldValue(456)).setWeight(2000)));
        CPPUNIT_FAIL("Expected exception when adding wrong type.");
    } catch (std::exception& e) {
        ; // fprintf(stderr, "Got exception => OK: %s\n", e.what());
    }

    // Apply update
    update.applyTo(*doc);

    // Verify that the field was NOT set in the document
    FieldValue::UP fval(doc->getValue(field));
    CPPUNIT_ASSERT_EQUAL((document::FieldValue*) 0, fval.get());
}

void
DocumentUpdateTest::testMapValueUpdate()
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
                   .addUpdate(MapValueUpdate(StringFieldValue("banana"),
                                             ArithmeticValueUpdate(ArithmeticValueUpdate::Add, 1.0))))
        .applyTo(*doc);
    std::unique_ptr<WeightedSetFieldValue> fv1 =
        doc->getAs<WeightedSetFieldValue>(field1);
    CPPUNIT_ASSERT(fv1->size() == 0);

    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field2)
                   .addUpdate(MapValueUpdate(StringFieldValue("banana"),
                                             ArithmeticValueUpdate(ArithmeticValueUpdate::Add, 1.0))))
        .applyTo(*doc);
    auto fv2 = doc->getAs<WeightedSetFieldValue>(field2);
    CPPUNIT_ASSERT(fv2->size() == 1);

    CPPUNIT_ASSERT(fv1->find(StringFieldValue("apple")) == fv1->end());
    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field1).addUpdate(ClearValueUpdate()))
        .applyTo(*doc);

    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field1).addUpdate(AddValueUpdate(StringFieldValue("apple")).setWeight(1)))
        .applyTo(*doc);

    auto fval3(doc->getAs<WeightedSetFieldValue>(field1));
    CPPUNIT_ASSERT(fval3->find(StringFieldValue("apple")) != fval3->end());
    CPPUNIT_ASSERT_EQUAL(1, fval3->get(StringFieldValue("apple")));

    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field2).addUpdate(AddValueUpdate(StringFieldValue("apple")).setWeight(1)))
        .applyTo(*doc);

    auto fval3b(doc->getAs<WeightedSetFieldValue>(field2));
    CPPUNIT_ASSERT(fval3b->find(StringFieldValue("apple")) != fval3b->end());
    CPPUNIT_ASSERT_EQUAL(1, fval3b->get(StringFieldValue("apple")));

    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field1)
                   .addUpdate(MapValueUpdate(StringFieldValue("apple"),
                                             ArithmeticValueUpdate(ArithmeticValueUpdate::Sub, 1.0))))
        .applyTo(*doc);

    auto fv3 = doc->getAs<WeightedSetFieldValue>(field1);
    CPPUNIT_ASSERT(fv3->find(StringFieldValue("apple")) != fv3->end());
    CPPUNIT_ASSERT_EQUAL(0, fv3->get(StringFieldValue("apple")));

    DocumentUpdate(docMan.getTypeRepo(), *doc->getDataType(), doc->getId())
        .addUpdate(FieldUpdate(field2)
                   .addUpdate(MapValueUpdate(StringFieldValue("apple"),
                                             ArithmeticValueUpdate(ArithmeticValueUpdate::Sub, 1.0))))
        .applyTo(*doc);

    auto fv4 = doc->getAs<WeightedSetFieldValue>(field2);
    CPPUNIT_ASSERT(fv4->find(StringFieldValue("apple")) == fv4->end());
}


void
DocumentUpdateTest::testTensorAssignUpdate()
{
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    CPPUNIT_ASSERT(!doc->getValue("tensor"));
    Document updated(*doc);
    FieldValue::UP new_value(createTensorFieldValue());
    testValueUpdate(AssignValueUpdate(*new_value), *DataType::TENSOR);
    DocumentUpdate upd(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    upd.addUpdate(FieldUpdate(upd.getType().getField("tensor")).addUpdate(AssignValueUpdate(*new_value)));
    upd.applyTo(updated);
    FieldValue::UP fval(updated.getValue("tensor"));
    CPPUNIT_ASSERT(fval);
    CPPUNIT_ASSERT(*fval == *new_value);
    CPPUNIT_ASSERT(*doc != updated);
}

void
DocumentUpdateTest::testTensorClearUpdate()
{
    TestDocMan docMan;
    Document::UP doc(docMan.createDocument());
    Document updated(*doc);
    updated.setValue(updated.getField("tensor"), *createTensorFieldValue());
    CPPUNIT_ASSERT(*doc != updated);
    DocumentUpdate upd(docMan.getTypeRepo(), *doc->getDataType(), doc->getId());
    upd.addUpdate(FieldUpdate(upd.getType().getField("tensor")).addUpdate(ClearValueUpdate()));
    upd.applyTo(updated);
    CPPUNIT_ASSERT(!updated.getValue("tensor"));
    CPPUNIT_ASSERT(*doc == updated);
}

void
assertDocumentUpdateFlag(bool createIfNonExistent, int value)
{
    DocumentUpdateFlags f1;
    f1.setCreateIfNonExistent(createIfNonExistent);
    CPPUNIT_ASSERT_EQUAL(createIfNonExistent, f1.getCreateIfNonExistent());
    int combined = f1.injectInto(value);
    std::cout << "createIfNonExistent=" << createIfNonExistent << ", value=" << value << ", combined=" << combined << std::endl;

    DocumentUpdateFlags f2 = DocumentUpdateFlags::extractFlags(combined);
    int extractedValue = DocumentUpdateFlags::extractValue(combined);
    CPPUNIT_ASSERT_EQUAL(createIfNonExistent, f2.getCreateIfNonExistent());
    CPPUNIT_ASSERT_EQUAL(value, extractedValue);
}

void
DocumentUpdateTest::testThatDocumentUpdateFlagsIsWorking()
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
      update(new DocumentUpdate(docMan.getTypeRepo(), *document->getDataType(), document->getId()))
{
    update->addUpdate(FieldUpdate(document->getField("headerval"))
                              .addUpdate(AssignValueUpdate(IntFieldValue(1))));
    update->setCreateIfNonExistent(true);
}

void
DocumentUpdateTest::testThatCreateIfNonExistentFlagIsSerialized50AndDeserialized50()
{
    CreateIfNonExistentFixture f;

    ByteBuffer::UP buf(serializeHEAD(*f.update));
    buf->flip();

    DocumentUpdate::UP deserialized = DocumentUpdate::createHEAD(f.docMan.getTypeRepo(), *buf);
    CPPUNIT_ASSERT_EQUAL(*f.update, *deserialized);
    CPPUNIT_ASSERT(deserialized->getCreateIfNonExistent());
}

void
DocumentUpdateTest::testThatCreateIfNonExistentFlagIsSerializedAndDeserialized()
{
    CreateIfNonExistentFixture f;

    ByteBuffer::UP buf(serialize42(*f.update));
    buf->flip();

    nbostream is(buf->getBufferAtPos(), buf->getRemaining());
    auto deserialized = DocumentUpdate::create42(f.docMan.getTypeRepo(), is);
    CPPUNIT_ASSERT_EQUAL(*f.update, *deserialized);
    CPPUNIT_ASSERT(deserialized->getCreateIfNonExistent());
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
                              .addUpdate(MapValueUpdate(IntFieldValue(1),
                                                        AssignValueUpdate(StringFieldValue("bar")))));
}
ArrayUpdateFixture::~ArrayUpdateFixture() = default;

void DocumentUpdateTest::array_element_update_can_be_roundtrip_serialized() {
    ArrayUpdateFixture f;

    auto buffer = serializeHEAD(*f.update);
    buffer->flip();

    auto deserialized = DocumentUpdate::createHEAD(f.doc_man.getTypeRepo(), *buffer);
    CPPUNIT_ASSERT_EQUAL(*f.update, *deserialized);
}

void DocumentUpdateTest::array_element_update_applies_to_specified_element() {
    ArrayUpdateFixture f;

    ArrayFieldValue array_value(f.array_field.getDataType());
    array_value.add("foo");
    array_value.add("baz");
    array_value.add("blarg");
    f.doc->setValue(f.array_field, array_value);

    f.update->applyTo(*f.doc);

    auto result_array = f.doc->getAs<ArrayFieldValue>(f.array_field);
    CPPUNIT_ASSERT_EQUAL(size_t(3), result_array->size());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("foo"), (*result_array)[0].getAsString());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("bar"), (*result_array)[1].getAsString());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("blarg"), (*result_array)[2].getAsString());
}

}  // namespace document
