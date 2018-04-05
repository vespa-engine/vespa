// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/document/util/bytebuffer.h>

using vespalib::nbostream;
using document::config_builder::Struct;
using document::config_builder::Wset;
using document::config_builder::Array;
using document::config_builder::Map;

namespace document {

struct StructFieldValueTest : public CppUnit::TestFixture {
    DocumentTypeRepo doc_repo;
    StructFieldValueTest();

    void testStruct();
    void testEmptyStruct();

    CPPUNIT_TEST_SUITE(StructFieldValueTest);
    CPPUNIT_TEST(testStruct);
    CPPUNIT_TEST(testEmptyStruct);

    CPPUNIT_TEST_SUITE_END();

};

CPPUNIT_TEST_SUITE_REGISTRATION(StructFieldValueTest);

namespace {
template <typename T>
void deserialize(const ByteBuffer &buffer, T &value, const FixedTypeRepo &repo)
{
    uint16_t version = Document::getNewestSerializationVersion();
    nbostream stream(buffer.getBufferAtPos(), buffer.getRemaining());
    VespaDocumentDeserializer deserializer(repo, stream, version);
    deserializer.read(value);
}

config_builder::DocumenttypesConfigBuilderHelper
createBuilder() {
    config_builder::DocumenttypesConfigBuilderHelper builder;
    builder.document(42, "test",
                     Struct("test.header")
                             .addField("int", DataType::T_INT)
                             .addField("long", DataType::T_LONG)
                             .addField("content", DataType::T_STRING),
                     Struct("test.body"));
    return builder;
}

}  // namespace

StructFieldValueTest::StructFieldValueTest()
    : doc_repo(createBuilder().config())
{}

void StructFieldValueTest::testEmptyStruct()
{
    FixedTypeRepo repo(doc_repo, *doc_repo.getDocumentType(42));
    const DataType &type = *repo.getDataType("test.header");
    StructFieldValue value(type);

    // Serialize & equality
    std::unique_ptr<ByteBuffer> buffer(value.serialize());
    buffer->flip();

    CPPUNIT_ASSERT_EQUAL(buffer->getLength(), buffer->getLimit());
    StructFieldValue value2(type);

    deserialize(*buffer, value2, repo);
    CPPUNIT_ASSERT(value == value2);
}

void StructFieldValueTest::testStruct()
{
    const DocumentType *doc_type = doc_repo.getDocumentType(42);
    CPPUNIT_ASSERT(doc_type);
    FixedTypeRepo repo(doc_repo, *doc_type);
    const DataType &type = *repo.getDataType("test.header");
    StructFieldValue value(type);
    const Field &intF = value.getField("int");
    const Field &longF = value.getField("long");
    const Field &strF = value.getField("content");

        // Initially empty
    CPPUNIT_ASSERT_EQUAL(size_t(0), value.getSetFieldCount());
    CPPUNIT_ASSERT(!value.hasValue(intF));

    value.setValue(intF, IntFieldValue(1));

        // Not empty
    CPPUNIT_ASSERT_EQUAL(size_t(1), value.getSetFieldCount());
    CPPUNIT_ASSERT(value.hasValue(intF));

        // Adding some more
    value.setValue(longF, LongFieldValue(2));

        // Not empty
    CPPUNIT_ASSERT_EQUAL(size_t(2), value.getSetFieldCount());
    CPPUNIT_ASSERT_EQUAL(1, value.getValue(intF)->getAsInt());
    CPPUNIT_ASSERT_EQUAL(2, value.getValue(longF)->getAsInt());

        // Serialize & equality
    std::unique_ptr<ByteBuffer> buffer(value.serialize());
    buffer->flip();

    CPPUNIT_ASSERT_EQUAL(buffer->getLength(), buffer->getLimit());
    StructFieldValue value2(type);
    CPPUNIT_ASSERT(value != value2);

    deserialize(*buffer, value2, repo);

    CPPUNIT_ASSERT(value2.hasValue(intF));
    CPPUNIT_ASSERT_EQUAL(value, value2);

    // Various ways of removing
    {
        // By value
        buffer->setPos(0);
        deserialize(*buffer, value2, repo);
        value2.remove(intF);
        CPPUNIT_ASSERT(!value2.hasValue(intF));
        CPPUNIT_ASSERT_EQUAL(size_t(1), value2.getSetFieldCount());

        // Clearing all
        buffer->setPos(0);
        deserialize(*buffer, value2, repo);
        value2.clear();
        CPPUNIT_ASSERT(!value2.hasValue(intF));
        CPPUNIT_ASSERT_EQUAL(size_t(0), value2.getSetFieldCount());
    }

    // Updating
    value2 = value;
    CPPUNIT_ASSERT_EQUAL(value, value2);
    value2.setValue(strF, StringFieldValue("foo"));
    CPPUNIT_ASSERT(value2.hasValue(strF));
    CPPUNIT_ASSERT_EQUAL(vespalib::string("foo"),
                         value2.getValue(strF)->getAsString());
    CPPUNIT_ASSERT(value != value2);
    value2.assign(value);
    CPPUNIT_ASSERT_EQUAL(value, value2);
    StructFieldValue::UP valuePtr(value2.clone());

    CPPUNIT_ASSERT(valuePtr.get());
    CPPUNIT_ASSERT_EQUAL(value, *valuePtr);

        // Iterating
    const StructFieldValue& constVal(value);
    for(StructFieldValue::const_iterator it = constVal.begin();
        it != constVal.end(); ++it)
    {
        constVal.getValue(it.field());
    }

        // Comparison
    value2 = value;
    CPPUNIT_ASSERT_EQUAL(0, value.compare(value2));
    value2.remove(intF);
    CPPUNIT_ASSERT(value.compare(value2) < 0);
    CPPUNIT_ASSERT(value2.compare(value) > 0);
    value2 = value;
    value2.setValue(intF, IntFieldValue(5));
    CPPUNIT_ASSERT(value.compare(value2) < 0);
    CPPUNIT_ASSERT(value2.compare(value) > 0);

        // Output
    CPPUNIT_ASSERT_EQUAL(
            std::string("Struct test.header(\n"
                        "  int - 1,\n"
                        "  long - 2\n"
                        ")"),
            value.toString(false));
    CPPUNIT_ASSERT_EQUAL(
            std::string("  Struct test.header(\n"
                        "..  int - 1,\n"
                        "..  long - 2\n"
                        "..)"),
            "  " + value.toString(true, ".."));
    CPPUNIT_ASSERT_EQUAL(
            std::string("<value>\n"
                        "  <int>1</int>\n"
                        "  <long>2</long>\n"
                        "</value>"),
            value.toXml("  "));

        // Failure situations.

        // Refuse to set wrong types
    try{
        value2.setValue(intF, StringFieldValue("bar"));
        CPPUNIT_FAIL("Failed to check type equality in setValue");
    } catch (std::exception& e) {
        CPPUNIT_ASSERT_CONTAIN("Cannot assign value of type", e.what());
    }
}

} // document

