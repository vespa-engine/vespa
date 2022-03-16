// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <gtest/gtest.h>
#include <gmock/gmock.h>

using vespalib::nbostream;
using namespace ::testing;

namespace document {

namespace {
template <typename T>
void deserialize(nbostream & stream, T &value) {
    uint16_t version = Document::getNewestSerializationVersion();
    DocumentTypeRepo repo;
    VespaDocumentDeserializer deserializer(repo, stream, version);
    deserializer.read(value);
}
}  // namespace

TEST(ArrayFieldValueTest, testArray)
{
    ArrayDataType type(*DataType::INT);
    ArrayFieldValue value(type);

        // Initially empty
    EXPECT_EQ(size_t(0), value.size());
    EXPECT_TRUE(value.isEmpty());
    EXPECT_TRUE(!value.contains(IntFieldValue(1)));

    EXPECT_TRUE(value.add(IntFieldValue(1)));

        // Not empty
    EXPECT_EQ(size_t(1), value.size());
    EXPECT_TRUE(!value.isEmpty());
    EXPECT_TRUE(value.contains(IntFieldValue(1)));

        // Adding some more
    EXPECT_TRUE(value.add(IntFieldValue(2)));
    EXPECT_TRUE(value.add(IntFieldValue(3)));

        // Not empty
    ASSERT_EQ(size_t(3), value.size());
    EXPECT_TRUE(!value.isEmpty());
    EXPECT_EQ(IntFieldValue(1), (IntFieldValue&) value[0]);
    EXPECT_EQ(IntFieldValue(2), (IntFieldValue&) value[1]);
    EXPECT_EQ(IntFieldValue(3), (IntFieldValue&) value[2]);

        // Serialize & equality
    nbostream stream(value.serialize());
    ArrayFieldValue value2(type);
    EXPECT_TRUE(value != value2);
    deserialize(stream, value2);
    EXPECT_EQ(value, value2);

        // Various ways of removing
    {
            // By index
        stream.rp(0);
        deserialize(stream, value2);
        value2.remove(1);
        EXPECT_TRUE(!value2.contains(IntFieldValue(2)));
        EXPECT_EQ(size_t(2), value2.size());

            // By value
        stream.rp(0);
        deserialize(stream, value2);
        EXPECT_TRUE(value2.remove(IntFieldValue(1)));
        EXPECT_TRUE(!value2.contains(IntFieldValue(1)));
        EXPECT_EQ(size_t(2), value2.size());

            // By value with multiple present
        stream.rp(0);
        deserialize(stream, value2);
        value2.add(IntFieldValue(1));
        EXPECT_TRUE(value2.remove(IntFieldValue(1)));
        EXPECT_TRUE(!value2.contains(IntFieldValue(1)));
        EXPECT_EQ(size_t(2), value2.size());

            // Clearing all
        stream.rp(0);
        deserialize(stream, value2);
        value2.clear();
        EXPECT_TRUE(!value2.contains(IntFieldValue(1)));
        EXPECT_EQ(size_t(0), value2.size());
        EXPECT_TRUE(value2.isEmpty());
    }

        // Updating
    value2 = value;
    EXPECT_EQ(value, value2);
    value2[1].assign(IntFieldValue(5));
    EXPECT_TRUE(!value2.contains(IntFieldValue(2)));
    EXPECT_EQ(IntFieldValue(5), (IntFieldValue&) value2[1]);
    EXPECT_TRUE(value != value2);
    value2.assign(value);
    EXPECT_EQ(value, value2);
    ArrayFieldValue::UP valuePtr(value2.clone());
    EXPECT_EQ(value, *valuePtr);

    // Iterating
    const ArrayFieldValue& constVal(value);
    for(const FieldValue & fval1 : constVal) {
        EXPECT_EQ(FieldValue::Type::INT, fval1.type());
    }
    value2 = value;
    for(size_t i(0); i < value2.size(); i++) {
        value2[i].assign(IntFieldValue(7));
    }
    EXPECT_TRUE(value != value2);
    EXPECT_TRUE(value2.contains(IntFieldValue(7)));
    EXPECT_TRUE(value2.remove(IntFieldValue(7)));
    EXPECT_TRUE(value2.isEmpty());

        // Comparison
    value2 = value;
    EXPECT_EQ(0, value.compare(value2));
    value2.remove(1);
    EXPECT_TRUE(value.compare(value2) > 0);
    EXPECT_TRUE(value2.compare(value) < 0);
    value2 = value;
    value2[1].assign(IntFieldValue(5));
    EXPECT_TRUE(value.compare(value2) < 0);
    EXPECT_TRUE(value2.compare(value) > 0);

        // Output
    EXPECT_EQ(std::string("Array(size: 3,\n  1,\n  2,\n  3\n)"),
                         value.toString(false));
    EXPECT_EQ(std::string("Array(size: 3,\n.  1,\n.  2,\n.  3\n.)"),
                         value.toString(true, "."));
    EXPECT_EQ(std::string(
                            "<value>\n"
                            "  <item>1</item>\n"
                            "  <item>2</item>\n"
                            "  <item>3</item>\n"
                            "</value>"
                         ),
                         value.toXml("  "));

        // Failure situations.

        // Verify that datatypes are verified
        // Created almost equal types to try to get it to fail
    ArrayDataType type1(*DataType::INT);
    ArrayDataType type2(*DataType::LONG);
    ArrayDataType type3(static_cast<DataType &>(type1));
    ArrayDataType type4(static_cast<DataType &>(type2));
    ArrayFieldValue value3(type3);
    ArrayFieldValue value4(type4);
    try{
        value3 = value4;
        FAIL() << "Failed to check type equality in operator=";
    } catch (std::exception& e) {
        EXPECT_THAT(e.what(), HasSubstr("Cannot assign value of type"));
    }
    try{
        value3.assign(value4);
        FAIL() << "Failed to check type equality in assign()";
    } catch (std::exception& e) {
        EXPECT_THAT(e.what(), HasSubstr("Cannot assign value of type"));
    }
    try{
        ArrayFieldValue subValue(type2);
        subValue.add(LongFieldValue(4));
        value3.add(subValue);
        FAIL() << "Failed to check type equality in add()";
    } catch (std::exception& e) {
        EXPECT_THAT(e.what(), HasSubstr("Cannot add value of type"));
    }
    try{
        ArrayFieldValue subValue(type2);
        subValue.add(LongFieldValue(4));
        value3.contains(subValue);
        FAIL() << "Failed to check type equality in contains()";
    } catch (std::exception& e) {
        EXPECT_THAT(e.what(), HasSubstr("can't possibly be in array of type"));
    }
    try{
        ArrayFieldValue subValue(type2);
        subValue.add(LongFieldValue(4));
        value3.remove(subValue);
        FAIL() << "Failed to check type equality in remove()";
    } catch (std::exception& e) {
        EXPECT_THAT(e.what(), HasSubstr("can't possibly be in array of type"));
    }
        // Verify that compare sees difference
    {
        ArrayFieldValue subValue1(type1);
        ArrayFieldValue subValue2(type2);
        subValue1.add(IntFieldValue(3));
        subValue2.add(LongFieldValue(3));
        value3.clear();
        value4.clear();
        value3.add(subValue1);
        value4.add(subValue2);
        EXPECT_TRUE(value3.compare(value4) != 0);
    }

        // Removing non-existing element
    value2 = value;
    try{
        value2.remove(5);
        FAIL() << "Failed to throw out of bounds exception on remove(int)";
    } catch (std::exception& e) {
        EXPECT_THAT(e.what(), HasSubstr("Cannot remove index 5 from an array of size 3"));
    }
    EXPECT_TRUE(!value2.remove(IntFieldValue(15)));
}

} // document
