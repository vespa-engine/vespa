// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/document/util/bytebuffer.h>
#include <gtest/gtest.h>
#include <gmock/gmock.h>

using vespalib::nbostream;
using document::config_builder::Struct;
using document::config_builder::Wset;
using document::config_builder::Array;
using document::config_builder::Map;
using namespace ::testing;

namespace document {

class StructFieldValueTest : public ::testing::Test {

protected:
    DocumentTypeRepo doc_repo;
    StructFieldValueTest();
};

namespace {
template <typename T>
void deserialize(nbostream & stream, T &value, const FixedTypeRepo &repo)
{
    uint16_t version = Document::getNewestSerializationVersion();
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

TEST_F(StructFieldValueTest, testEmptyStruct)
{
    FixedTypeRepo repo(doc_repo, *doc_repo.getDocumentType(42));
    const DataType &type = *repo.getDataType("test.header");
    StructFieldValue value(type);

    // Serialize & equality
    nbostream buffer(value.serialize());

    StructFieldValue value2(type);

    deserialize(buffer, value2, repo);
    EXPECT_TRUE(value == value2);
}

TEST_F(StructFieldValueTest, testStruct)
{
    const DocumentType *doc_type = doc_repo.getDocumentType(42);
    ASSERT_TRUE(doc_type != nullptr);
    FixedTypeRepo repo(doc_repo, *doc_type);
    const DataType &type = *repo.getDataType("test.header");
    StructFieldValue value(type);
    const Field &intF = value.getField("int");
    const Field &longF = value.getField("long");
    const Field &strF = value.getField("content");

        // Initially empty
    EXPECT_EQ(size_t(0), value.getSetFieldCount());
    EXPECT_TRUE(!value.hasValue(intF));

    value.setValue(intF, IntFieldValue(1));

        // Not empty
    EXPECT_EQ(size_t(1), value.getSetFieldCount());
    EXPECT_TRUE(value.hasValue(intF));

        // Adding some more
    value.setValue(longF, LongFieldValue(2));

        // Not empty
    EXPECT_EQ(size_t(2), value.getSetFieldCount());
    EXPECT_EQ(1, value.getValue(intF)->getAsInt());
    EXPECT_EQ(2, value.getValue(longF)->getAsInt());

        // Serialize & equality
    nbostream buffer(value.serialize());

    StructFieldValue value2(type);
    EXPECT_TRUE(value != value2);

    deserialize(buffer, value2, repo);

    EXPECT_TRUE(value2.hasValue(intF));
    EXPECT_EQ(value, value2);

    // Various ways of removing
    {
        // By value
        buffer.rp(0);
        deserialize(buffer, value2, repo);
        value2.remove(intF);
        EXPECT_TRUE(!value2.hasValue(intF));
        EXPECT_EQ(size_t(1), value2.getSetFieldCount());

        // Clearing all
        buffer.rp(0);
        deserialize(buffer, value2, repo);
        value2.clear();
        EXPECT_TRUE(!value2.hasValue(intF));
        EXPECT_EQ(size_t(0), value2.getSetFieldCount());
    }

    // Updating
    value2 = value;
    EXPECT_EQ(value, value2);
    value2.setValue(strF, StringFieldValue("foo"));
    EXPECT_TRUE(value2.hasValue(strF));
    EXPECT_EQ(vespalib::string("foo"), value2.getValue(strF)->getAsString());
    EXPECT_TRUE(value != value2);
    value2.assign(value);
    EXPECT_EQ(value, value2);
    StructFieldValue::UP valuePtr(value2.clone());

    EXPECT_TRUE(valuePtr.get());
    EXPECT_EQ(value, *valuePtr);

        // Iterating
    const StructFieldValue& constVal(value);
    for(StructFieldValue::const_iterator it = constVal.begin();
        it != constVal.end(); ++it)
    {
        constVal.getValue(it.field());
    }

        // Comparison
    value2 = value;
    EXPECT_EQ(0, value.compare(value2));
    value2.remove(intF);
    EXPECT_TRUE(value.compare(value2) < 0);
    EXPECT_TRUE(value2.compare(value) > 0);
    value2 = value;
    value2.setValue(intF, IntFieldValue(5));
    EXPECT_TRUE(value.compare(value2) < 0);
    EXPECT_TRUE(value2.compare(value) > 0);

        // Output
    EXPECT_EQ(
            std::string("Struct test.header(\n"
                        "  int - 1,\n"
                        "  long - 2\n"
                        ")"),
            value.toString(false));
    EXPECT_EQ(
            std::string("  Struct test.header(\n"
                        "..  int - 1,\n"
                        "..  long - 2\n"
                        "..)"),
            "  " + value.toString(true, ".."));
    EXPECT_EQ(
            std::string("<value>\n"
                        "  <int>1</int>\n"
                        "  <long>2</long>\n"
                        "</value>"),
            value.toXml("  "));

        // Failure situations.

        // Refuse to set wrong types
    try{
        value2.setValue(intF, StringFieldValue("bar"));
        FAIL() << "Failed to check type equality in setValue";
    } catch (std::exception& e) {
        EXPECT_THAT(e.what(), HasSubstr("Cannot assign value of type"));
    }
}

} // document

